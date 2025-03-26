package org.dreamwork.injection.impl;

import com.google.gson.Gson;
import org.dreamwork.cli.Argument;
import org.dreamwork.cli.ArgumentParser;
import org.dreamwork.config.PropertyConfiguration;
import org.dreamwork.injection.*;
import org.dreamwork.util.FileInfo;
import org.dreamwork.util.IOUtil;
import org.dreamwork.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.InstanceAlreadyExistsException;
import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.dreamwork.injection.IObjectContext.CONTEXT_ANNOTATION_KEY;
import static org.dreamwork.injection.impl.ScannerHelper.fillPackageNames;

/**
 * 对象托管容器的工厂
 *
 * <p>这个工厂类负责初始化容器，解析配置，启动扫描</p>
 * <pre>
 * package org.example.app;
 *
 * &#64;AInjectionContext
 * public class MyAppMain {
 *     public static void main (String[] args) throws Exception {
 *         ObjectContextFactory.start (MyAppMain.class, args);
 *     }
 * }
 * </pre>
 * 扫描器将自动扫描 {@code org.example.app} 包下的所有类，所有带有 <strong>托管标注</strong> 的类
 * 都将自动被配置到托管容器内。
 * <p>如果希望扫描其他包，使用 {@link AInjectionContext#scanPackages() scanPackages} 属性来添加额外的包扫描</p>
 * <p>在默认情况下，扫描器 <strong>不会</strong> 递归扫描，若希望递归扫描，设置 {@link AInjectionContext#recursive() recursive}
 * 属性的值为 {@code true} 来激活递归扫描
 * </p>
 *
 * <h2>受托管对象</h2>
 * {@link javax.annotation.Resource} 标注用来表示一个对象/资源是 <strong><i>受托管</i></strong> 的。 托管容器将自动
 * 配置和管理这些对象实例。当 {@link javax.annotation.Resource} 用来标注: <ul>
 * <li>一个类时，意味着这个类将被作为受托管资源配置到托管容器内</li>
 * <li>一个 {@code java getter} 时，意味着这个 getter 的返回值将被自动配置到容器内</li>
 * <li>一个 {@code java setter} 时，意味着从容器内获取对应的资源并作为 setter 的参数注入到对象内</li>
 * <li>一个 {@code 字段} 时，意味着从容器内获取对应的资源输入到这个字段</li>
 * </ul>
 * 每个被 {@link javax.annotation.Resource} 标注的类，<strong>最多有一个</strong>方法被<ul>
 * <li>{@link javax.annotation.PostConstruct} 标注，意味着这个类在被配置到容器后将自动调用。这个方法的
 * 签名必须是<pre>public void &lt;methodName&gt; ()</pre></li>
 * <li>{@link javax.annotation.PreDestroy} 标注，意味着这个类从容器内删除前将自动调用。这个方法的签名必须是
 * <pre>public void &lt;methodName&gt; ()</pre></li>
 * </ul>
 *
 * <h2>简单的容器生命周期</h2>
 * 当容器解决了所有的依赖注入后，会调用所有实现了 {@link IInjectResolvedProcessor} 接口
 * 的实例的 {@link IInjectResolvedProcessor#perform(IObjectContext) perform} 方法。如果希望
 * 多个实现的运行顺序，应当实现 {@link IInjectResolvedProcessor#getOrder() getOrder} 方法，容器将
 * 按照这个方法的返回值排序，并按顺序调用，例如：<pre>
 * package org.example.app;
 * &#64;Resource
 * public class MyProcessor implements IInjectResolvedProcessor {
 *     &#64;Override
 *     public void perform (IObjectContext ctx) throws Exception {
 *         for (String name : ctx.getAllBeanNames ()) {
 *             System.out.println (name);
 *         }
 *     }
 * }
 * </pre>
 *
 * <h2>自动装配</h2>
 * 对象托管容器的工厂还允许用户代码自动装配额外的托管对象。
 * 工厂会扫描类路劲下的 /META-INF/dsi-*.properties 文件来获取自动装配的信息
 *
 * @author seth.yang
 * @since 3.1.1
 */
@SuppressWarnings ("unused")
public class SimpleObjectContextFactory {
    private final static int PREFIX_LENGTH = "META-INF/".length ();
    private Logger logger;
    private final Class<?> type;
    private final Path javaHome;

    /**
     * 对象托管容器的工厂方法
     *
     * @param type 被 {@link AInjectionContext} 标注的类，用于提供托管容器的配置信息
     * @param args 命令行参数
     * @return 托管容器的实例
     * @throws Exception 任何异常
     */
    public static IObjectContext start (Class<?> type, String... args) throws Exception {
        if (!type.isAnnotationPresent (AInjectionContext.class)) {
            throw new TypeNotPresentException (AInjectionContext.class.getName (), null);
        }

        return new SimpleObjectContextFactory (type).createObjectContext (args);
    }

    private SimpleObjectContextFactory (Class<?> type) {
        this.type = type;
        javaHome  = Paths.get (System.getProperty ("java.home"));
    }

    private IObjectContext createObjectContext (String... args) throws Exception {
        ClassLoader loader = type.getClassLoader ();

        // 解析和处理配置/参数
        PropertyConfiguration configuration = initConfiguration (loader, args);
        if (configuration == null) {
            return null;
        }

        logger = LoggerFactory.getLogger (getClass ());
        if (logger.isTraceEnabled ()) {
            prettyPrint (configuration.getRawProperties (), logger);
        }
        AInjectionContext ic = type.getAnnotation (AInjectionContext.class);

        int port = configuration.getInt ("org.dreamwork.dsi.shutdown-port", -1);
        port = Math.max (port, ic.shutdownPort ());

        // 创建 SimpleObjectContext
        // @Since 2.0.0
        SimpleObjectContext root = new SimpleObjectContext (port);
        // 注册全局配置
        root.register ("global-config", configuration);
        // 注册全局的懒加载器
        LazyScanner lazy = new LazyScanner ();
        root.register (lazy);

        // @since 1.1.0 保存所有的注解，后面子模块可能有用
        Annotation[] annotations = type.getAnnotations ();
        root.register (CONTEXT_ANNOTATION_KEY, annotations);

        ClassScanner scanner = new ObjectContextScanner (root);

        Set<String> packages = new HashSet<> ();
        // @since 3.1.1 自动装配
        autoWireStarters (root, loader, packages);
        scanner.scan (packages.toArray (new String[0]));

        packages.clear ();

        fillPackageNames (type, ic, loader, packages);
        scanner.scan (packages.toArray (new String[0]));

        root.resolve ();

        // since 1.0.3
        lazyScan (ic, lazy, root);

        Runtime.getRuntime ().addShutdownHook (new Thread (() -> {
            Thread.currentThread ().setName ("SimpleObjectContext.ShutdownHook");
            root.dispose ();
        }));
        return root;
    }

    private void autoWireStarters (IObjectContext root, ClassLoader loader, Set<String> packages) throws IOException {
        Enumeration<URL> resources = loader.getResources ("META-INF/");
        URL url;
        String protocol;
        while (resources.hasMoreElements ()) {
            url = resources.nextElement ();
            protocol = url.getProtocol ();
            if ("file".equals (protocol)) {
                autoWireFileHook (root, url, loader, packages);
            } else if ("jar".equals (protocol)) {
                autoWireJarHook (root, url, loader, packages);
            }
        }
    }

    private boolean accepted (String name) {
        return  name != null && name.startsWith ("dsi-") &&
                (name.endsWith ("-hook.properties") || name.endsWith ("-hook.conf"));
    }

    private void autoWireFileHook (IObjectContext root, URL url, ClassLoader loader, Set<String> packages) {
        File dir = new File (url.getFile ());
        if (!dir.exists () || !dir.canRead ()) {
            return;
        }

        File[] files = dir.listFiles (file -> accepted (file.getName ()));
        if (files != null && files.length > 0) {
            Arrays.stream (files).forEach (file -> {
                try (InputStream in = Files.newInputStream (file.toPath ())) {
                    autoWire (root, file.getCanonicalPath (), in, loader, packages);
                } catch (IOException | InstanceAlreadyExistsException ex) {
                    logger.warn ("cannot open file: {}", file);
                    if (logger.isTraceEnabled ()) {
                        logger.warn (ex.getMessage (), ex);
                    }
                }
            });
        }
    }

    private void autoWireJarHook (IObjectContext root, URL url, ClassLoader loader, Set<String> packages) throws IOException {
        Set<String> set = new HashSet<> ();
        String path = url.getFile ();
        int index = path.indexOf ('!');
        path = path.substring (0, index);
        url = new URL (path);
        File file = new File (url.getFile ());
        if (file.toPath ().startsWith (javaHome) || !file.exists ()) {
            return;
        }

        if (logger.isTraceEnabled ()) {
            logger.trace ("trying find auto wire config from {}", file.getCanonicalPath ());
        }
        try (JarFile jar = new JarFile (file)) {
            Enumeration<JarEntry> entries = jar.entries ();
            while (entries.hasMoreElements ()) {
                JarEntry entry = entries.nextElement ();
                String name = entry.getName ();

                if (name.startsWith ("META-INF/")) {
                    name = name.substring (PREFIX_LENGTH);
                    if (accepted (name)) {
                        String resource = url + name;
                        try (InputStream in = jar.getInputStream (entry)) {
                            autoWire (root, resource, in, loader, packages);
                        } catch (IOException | InstanceAlreadyExistsException ex) {
                            logger.warn ("cannot open resource: {}", resource);
                            if (logger.isTraceEnabled ()) {
                                logger.warn (ex.getMessage (), ex);
                            }
                        }
                    }
                }
            }
        }
    }

    private void autoWire (IObjectContext root, String resource, InputStream in,
                           ClassLoader loader, Set<String> packages) throws IOException, InstanceAlreadyExistsException {
        Properties props = new Properties ();
        props.load (in);
        for (String key : props.stringPropertyNames ()) {
            key = key.trim ();
            if (key.startsWith ("dsi.") && key.endsWith (".hook")) {
                String className = props.getProperty (key).trim ();
                autoWire (root, className, resource, loader, packages);
            }
        }
    }

    private void autoWire (IObjectContext root, String className, String resource,
                           ClassLoader loader, Set<String> packages) throws IOException, InstanceAlreadyExistsException {
        try {
            Class<?> type = Class.forName (className);
            if (!IObjectContextHook.class.isAssignableFrom (type)) {
                if (logger.isWarnEnabled ()) {
                    logger.warn ("{} does not implement {}, ignore this resource from {}",
                            className, IObjectContextHook.class, resource);
                }
                return;
            }

            IObjectContextHook hook = null;
            try {
                @SuppressWarnings ("unchecked")
                Constructor<IObjectContextHook> c =
                        (Constructor<IObjectContextHook>) type.getConstructor (IObjectContext.class);
                hook = c.newInstance (root);
            } catch (NoSuchMethodException | InvocationTargetException ignore) {
                if (logger.isTraceEnabled ()) {
                    logger.trace ("there's no constructor with IObjectContext, using default constructor");
                }
            }
            if (hook == null) {
                hook = (IObjectContextHook) type.newInstance ();
            }
            if (logger.isTraceEnabled ()) {
                logger.trace ("found a hook: {}", className);
            }
            packages.add (type.getPackage ().getName ());
            String[] ps = hook.getScanPackages ();
            boolean recursive = hook.isRecursive ();
            if (ps != null && ps.length > 0) {
                if (!recursive) {
                    packages.addAll (Arrays.asList (ps));
                } else {
                    fillPackageNames (type.getPackage ().getName (), ps, loader, packages);
                }
            }

            // since 1.0.3
            Map<String, ClassScanner> dict = hook.getExtraScanners ();
            if (dict != null && !dict.isEmpty ()) {
                LazyScanner lazy = root.getBean (LazyScanner.class);
                lazy.merge (dict);
            }
        } catch (InstantiationException | IllegalAccessException ex) {
            logger.warn ("cannot instantiate class: {}", className);
            if (logger.isTraceEnabled ()) {
                logger.warn (ex.getMessage (), ex);
            }
        } catch (ClassNotFoundException ex) {
            logger.warn ("class {} not found, ignore this resource from {}", className, resource);
            if (logger.isTraceEnabled ()) {
                logger.warn (ex.getMessage (), ex);
            }
        }
    }

    private InputStream findArgumentDefinition (ClassLoader loader, String json) throws IOException {
        List<String> list = new ArrayList<> ();
        if (!StringUtil.isEmpty (json)) {
            list.add (json);
        }
        list.addAll (Arrays.asList ("../conf/cli-arguments.json", "cli-arguments.json", "../cli-arguments.json"));
        InputStream in;
        for (String path : list) {
            in = loader.getResourceAsStream (path);
            if (in != null) {
                return in;
            }
        }
        for (String path : list) {
            File file = new File (path);
            if (file.exists () && file.isFile () && file.canRead ()) {
                return Files.newInputStream (file.toPath ());
            }
        }

        return null;
    }

    private PropertyConfiguration initConfiguration (ClassLoader loader, String... args) throws IOException {
        Map<String, Argument> map = new HashMap<> ();
        Gson g = new Gson ();

        // 加载缺省的参数定义
        load (loader, g, map);

        AInjectionContext ic = type.getAnnotation (AInjectionContext.class);
        // 查找参数描述的 json 文件
        String[] definitions = ic.argumentDefinition ();
        for (String definition : definitions) {
            try (InputStream in = findArgumentDefinition (loader, definition)) {
                if (in != null) {
                    load (g, map, in);
                }
            }
        }

        ArgumentParser parser = new ArgumentParser (new ArrayList<> (map.values ()));
        parser.parse (args);

        if (parser.isArgPresent ('h') || parser.isArgPresent ("help")) {
            parser.showHelp ();
            System.exit (0);
            return null;
        }

        if (parser.isArgPresent ('V') || parser.isArgPresent ("version")) {
            System.out.println (SimpleObjectContext.VERSION_INFO);
            System.exit (0);
            return null;
        }

        if (parser.isArgPresent ("shutdown")) {
            ShutdownHook.shutdown ();
            return null;
        }

        PropertyConfiguration conf = mergeConfig (parser);

        try {
            if (!parser.isArgPresent ("without-logs"))
                initLogger (loader, conf, parser);
        } catch (Exception ex) {
            throw new RuntimeException (ex);
        }

        // now, we can use the logger
        Logger logger = LoggerFactory.getLogger (SimpleObjectContextFactory.class);
        return conf;
    }

    private PropertyConfiguration mergeConfig (ArgumentParser parser/*, Logger logger*/) {
        try {
            Properties props = parseConfig (parser/*, logger*/);
            EnhancedConfiguration configuration = new EnhancedConfiguration (props);

            // patch extra config dir
            setDefaultValue (parser, configuration, "ext.conf.dir", 'e');
            setDefaultValue (parser, configuration, "org.dreamwork.dsi.shutdown-port", "shutdown-port");

            Collection<Argument> ca = parser.getAllArguments ();
            for (Argument a : ca) {
                if (!StringUtil.isEmpty (a.propKey)) {
                    if (!StringUtil.isEmpty (a.shortOption))
                        setDefaultValue (parser, configuration, a.propKey, a.shortOption.charAt (0));
                    else if (!StringUtil.isEmpty (a.longOption))
                        setDefaultValue (parser, configuration, a.propKey, a.longOption);
                }
            }

            System.out.println ("configurations load complete, trying to start application");
            return configuration;
        } catch (Exception ex) {
            throw new RuntimeException (ex);
        }
    }

    private Properties parseConfig (ArgumentParser parser/*, Logger logger*/) throws IOException {
        String config_file = null;
        if (parser.isArgPresent ('c')) {
            config_file = parser.getValue ('c');
        }
        if (StringUtil.isEmpty (config_file)) {
            AInjectionContext ic = type.getAnnotation (AInjectionContext.class);
            if (!StringUtil.isEmpty (ic.config ())) {
                String name = ic.config ();
                File file = new File (name);
                if (file.isFile () && file.canRead ()) {
                    config_file = ic.config ();
                }
            }

            if (StringUtil.isEmpty (config_file)) {
                for (String name : Arrays.asList ("../conf/object-context.conf", "object-context.conf")) {
                    File file = new File (name);
                    if (file.isFile () && file.canRead ()) {
                        config_file = name;
                        break;
                    }
                }
            }
        }

        if (StringUtil.isEmpty (config_file)) {
            config_file = parser.getDefaultValue ('c');
        }

        config_file = config_file.trim ();
        File file;
        if (config_file.startsWith ("file:/") || config_file.startsWith ("/")) {
            file = new File (config_file);
        } else {
            file = new File (".", config_file);
        }

        Properties props = new Properties ();
        if (!file.exists ()) {
            System.err.println ("can't find config file: {}" + config_file);
            System.err.println ("using default config.");
        } else {
            try (InputStream in = Files.newInputStream (file.toPath ())) {
                props.load (in);
            }
        }
        return props;
    }

    private void load (Gson g, Map<String, Argument> map, InputStream in) throws IOException {
        String content = new String (IOUtil.read (in), StandardCharsets.UTF_8);
        List<Argument> list = g.fromJson (content, Argument.AS_LIST);
        list.forEach (item -> {
            String key = item.shortOption;
            if (StringUtil.isEmpty (key)) {
                key = item.longOption;
            }
            if (!StringUtil.isEmpty (key)) {
                map.put (key, item);
            }
        });
    }

    private void load (ClassLoader loader, Gson g, Map<String, Argument> map) {
        try (InputStream in = loader.getResourceAsStream ("default-arguments.json")) {
            if (in != null) {
                load (g, map, in);
            }
        } catch (Exception ex) {
            throw new RuntimeException (ex);
        }
    }

    private void prettyPrint (Properties props, Logger logger) {
        logger.trace ("### global configuration ###");
        int length = 0;
        List<String> list = new ArrayList<> ();
        for (String key : props.stringPropertyNames ()) {
            list.add (key);
            if (key.length () > length) {
                length = key.length ();
            }
        }
        list.sort (String::compareTo);
        for (String key : list) {
            StringBuilder builder = new StringBuilder (key);
            if (key.length () < length) {
                int d = length - key.length ();
                for (int i = 0; i < d; i ++) {
                    builder.append (' ');
                }
            }
            builder.append (" : ").append (props.getProperty (key));
            logger.trace (builder.toString ());
        }
        logger.trace ("############################");
    }

    private void setDefaultValue (ArgumentParser parser, PropertyConfiguration configuration, String key, char argument) {
        if (parser.isArgPresent (argument)) {
            configuration.setRawProperty (key, parser.getValue (argument));
        }
        if (!configuration.contains (key)) {
            configuration.setRawProperty (key, parser.getDefaultValue (argument));
        }
    }

    private void setDefaultValue (ArgumentParser parser, PropertyConfiguration configuration, String key, String argument) {
        if (parser.isArgPresent (argument)) {
            configuration.setRawProperty (key, parser.getValue (argument));
        }
        if (!configuration.contains (key)) {
            configuration.setRawProperty (key, parser.getDefaultValue (argument));
        }
    }

    private static void initLogger (ClassLoader loader, PropertyConfiguration conf, ArgumentParser parser) throws IOException {
        String logLevel, logFile;
        if (parser.isArgPresent ('v')) {
            logLevel = "TRACE";
        } else if (parser.isArgPresent ("log-level")) {
            logLevel = parser.getValue ("log-level");
        } else if (conf.contains ("log.level")) {
            logLevel = conf.getString ("log.level");
        } else {
            logLevel = parser.getDefaultValue ("log-level");
        }

        logFile = parser.getValue ("log-file");
        if (StringUtil.isEmpty (logFile)) {
            logFile = conf.getString ("log.file");
        }
        if (StringUtil.isEmpty (logFile)) {
            logFile = parser.getDefaultValue ("log-file");
        }
        File file = new File (logFile);
        File parent = file.getParentFile ();
        if (!parent.exists () && !parent.mkdirs ()) {
            throw new IOException ("Can't create dir: " + parent.getCanonicalPath ());
        }

        if ("TRACE".equalsIgnoreCase (logLevel)) {
            System.out.printf ("## log file: %s ##%n", file.getCanonicalFile ());
        }

        String className = "org.apache.log4j.PropertyConfigurator";
        boolean log4j = false;
        try {
            Class.forName (className);
            log4j = true;
        } catch (ClassNotFoundException ex) {
            // log4j not exists.
        }

        if (log4j)
            initLog4J (loader, logLevel, logFile, parser);
        else
            initJdkLogger (loader, logLevel, logFile);
    }

    private static void initJdkLogger (ClassLoader loader, String logLevel, String logFile) throws IOException {
        Map<String, String> mapping = new HashMap<> ();
        mapping.put ("trace", "FINEST");
        mapping.put ("debug", "FINER");
        mapping.put ("info", "INFO");
        mapping.put ("warn", "WARNING");
        mapping.put ("error", "SEVERE");

        String level = mapping.get (logLevel.toLowerCase ());
        String filePattern = FileInfo.getFileNameWithoutExtension (logFile);
        String path = FileInfo.getFolder (logFile);

        Properties props = new Properties ();
        try (InputStream in = loader.getResourceAsStream ("internal-jdk-logging.properties")) {
            props.load (in);
        }

        boolean trace = "trace".equalsIgnoreCase (logLevel);
        if (trace) {
            System.out.printf ("### setting log level to %s ###%n", logLevel);
        }

        props.setProperty ("java.util.logging.FileHandler.pattern", path + '/' + filePattern + "%u.log");
        if ("trace".equalsIgnoreCase (logLevel)) {

            System.out.printf ("### log file -> %s ###%n",
                    new File (path + '/' + filePattern + "%u.log").getCanonicalPath ());

            props.setProperty ("java.util.logging.FileHandler.level", "FINEST");
            props.setProperty ("java.util.logging.ConsoleHandler.level", "FINEST");
            props.setProperty (".level", "FINEST");
        } else {
            props.setProperty ("java.util.logging.FileHandler.level", level);
            props.setProperty ("java.util.logging.ConsoleHandler.level", level);
            props.setProperty (".level", level);
        }

        String tmpDir = System.getProperty ("java.io.tmpdir");
        String uuid   = StringUtil.uuid ();
        File file = new File (tmpDir, "jdk-logging-" + uuid + ".properties");
        try (OutputStream out = Files.newOutputStream (file.toPath ())) {
            OutputStreamWriter writer = new OutputStreamWriter (out, StandardCharsets.UTF_8);
            props.store (writer, uuid);
            out.flush ();
        }
        System.setProperty ("java.util.logging.config.file", file.getCanonicalPath ());
        Logger logger = LoggerFactory.getLogger (SimpleObjectContextFactory.class);
        logger.info ("JDK Logging load complete");
        file.deleteOnExit ();
    }

    private static void initLog4J (ClassLoader loader, String logLevel, String logFile, ArgumentParser parser) throws IOException {
        try (InputStream in = loader.getResourceAsStream ("internal-log4j.properties")) {
            Properties props = new Properties ();
            props.load (in);

            System.out.println ("### setting log level to " + logLevel + " ###");
            if ("trace".equalsIgnoreCase (logLevel)) {
                props.setProperty ("log4j.rootLogger", "INFO, stdout, FILE");
                props.setProperty ("log4j.appender.FILE.File", logFile);
                props.setProperty ("log4j.appender.FILE.Threshold", logLevel);
                props.setProperty ("log4j.logger.org.dreamwork", "trace");
            } else {
                props.setProperty ("log4j.rootLogger", logLevel + ", stdout, FILE");
                props.setProperty ("log4j.appender.FILE.File", logFile);
                props.setProperty ("log4j.appender.FILE.Threshold", logLevel);
            }

            if (parser.isArgPresent ("trace-prefix")) {
                String prefixes = parser.getValue ("trace-prefix");
                if (!StringUtil.isEmpty (prefixes)) {
                    String[] parts = prefixes.trim ().split (File.pathSeparator);
                    for (String prefix : parts) {
                        if ("trace".equalsIgnoreCase (logLevel)) {
                            System.out.printf ("#### setting %s log level to trace ####%n", prefix);
                        }
                        props.setProperty ("log4j.logger." + prefix, "trace");
                    }
                }
            }

            if ("trace".equalsIgnoreCase (logLevel)) {
                System.out.println ("trying to configure log4j ...");
            }
            try {
                Class<?> type = Class.forName ("org.apache.log4j.PropertyConfigurator");
                Method m = type.getMethod ("configure", Properties.class);
                m.invoke (null, props);

                Logger logger = LoggerFactory.getLogger (SimpleObjectContextFactory.class);
                logger.info ("Log4J load complete");
            } catch (Exception ex) {
                System.err.println ("can't configure log4j");
                ex.printStackTrace (System.out);
            }
        }
    }

    /**
     * 开启扩展扫描
     * @param ic   配置信息
     * @param lazy 懒扫描器集合
     * @param root 托管根容器
     *
     * @since 1.0.3
     */
    private void lazyScan (AInjectionContext ic, LazyScanner lazy, IObjectContext root) throws Exception {
        AExtraScan[] extras = ic.extras ();
        if (extras != null && extras.length > 0) {
            Map<String, ClassScanner> dict = lazy.getCachedScanners ();
            ClassLoader loader = getClass ().getClassLoader ();
            for (AExtraScan es : extras) {
                if (dict.containsKey (es.name ())) {
                    String[] names = es.scanPackages ();
                    if (es.recursive ()) {
                        Set<String> packages = new HashSet<> ();
                        for (String name : names) {
                            fillPackageNames (loader, name, packages);
                        }
                        names = packages.toArray (new String[0]);
                    }
                    if (names.length > 0) {
                        ClassScanner scanner = dict.get (es.name ());
                        scanner.scan (names);
                    }
                }
            }
        }
    }
}

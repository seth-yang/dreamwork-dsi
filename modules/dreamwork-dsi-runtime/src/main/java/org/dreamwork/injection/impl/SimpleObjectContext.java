package org.dreamwork.injection.impl;

import com.google.gson.Gson;
import org.dreamwork.config.IConfiguration;
import org.dreamwork.injection.*;
import org.dreamwork.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 简单容器
 *
 * <p>托管容器的简单实现</p>
 */
public class SimpleObjectContext implements IObjectContext {
    public static final String VERSION_INFO = "Dreamwork Simple Injection V2.0.0";
    private final Logger logger = LoggerFactory.getLogger (SimpleObjectContext.class);
    private final Lock LOCKER = new ReentrantLock ();

    final Map<String, Object> mappedByName = Collections.synchronizedMap (new HashMap<> ());
    final Map<Class<?>, Object> mappedByType = Collections.synchronizedMap (new HashMap<> ());
    final Set<IInjectResolvedProcessor> processors = new HashSet<> ();

    /**
     * 标识容器是否已经解决了依赖注入
     */
    final AtomicBoolean resolved = new AtomicBoolean (false);

    /**
     * 排除的包名。
     *
     * <p>某些 jdk 自带的接口不适合作为类型索引，应该排除它们</p>
     */
    private static final String[] EXCLUDE_PREFIXES = {
            "java.util.", "java.io."
    };

    /**
     * 监听应用停止请求的网络端口
     * @since 2.0.0
     */
    /*private final */int shutdownPort;

    /**
     * 构造函数
     * @param shutdownPort 监听应用停止请求的网络端口
     * @since 2.0.0
     */
    SimpleObjectContext (int shutdownPort) {
        this.shutdownPort = shutdownPort;
    }
    /**
     * 根据给定的名称索引容器内的对象
     * @param name 对象名称
     * @return 指定名称的对象
     * @see #register(Object)
     * @see #register(String, Object)
     * @see #getBean(Class)
     * @see #getBeanMap(Class)
     */
    @Override
    public Object getBean (String name) {
        try {
            LOCKER.lock ();
            return mappedByName.get (name);
        } finally {
            LOCKER.unlock ();
        }
    }

    @Override
    @SuppressWarnings ("unchecked")
    public <T> Map<String, T> getBeanMap (Class<T> type) {
        try {
            LOCKER.lock ();

            Object o = mappedByType.get (type);
            if (o == null) {
                return Collections.emptyMap ();
            }

            if (o.getClass () != InnerList.class) {
                for (Map.Entry<String, Object> e : mappedByName.entrySet ()) {
                    if (o == e.getValue ()) {
                        return Collections.singletonMap (e.getKey (), (T) o);
                    }
                }
            } else {
                InnerList il = (InnerList) o;
                Map<String, T> map = new HashMap<> ();
                for (Object i : il) {
                    for (Map.Entry<String, Object> e : mappedByName.entrySet ()) {
                        if (e.getValue () == i) {
                            map.put (e.getKey (), (T) i);
                            break;
                        }
                    }
                }

                return Collections.unmodifiableMap (map);
            }
            return null;
        } finally {
            LOCKER.unlock ();
        }
    }

    @Override
    @SuppressWarnings ("unchecked")
    public <T> T getBean (Class<T> type) {
        try {
            LOCKER.lock ();

            Object o = mappedByType.get (type);
            if (o == null) {
                return null;
            }

            if (o.getClass () != InnerList.class) {
                return (T) o;
            }

            throw new InstanceNotUniqueException (
                    "there are more than one instances marked as " + type.getCanonicalName ()
            );
        } finally {
            LOCKER.unlock ();
        }
    }

    @Override
    public void remove (Object bean) {
        try {
            LOCKER.lock ();

            String name = null;
            for (Map.Entry<String, Object> e : mappedByName.entrySet ()) {
                if (bean == e.getValue ()) {
                    name = e.getKey ();
                    break;
                }
            }
            if (!StringUtil.isEmpty (name)) {
                mappedByName.remove (name);
                Set<Class<?>> types = new HashSet<> ();
                findAllType (bean.getClass (), types);

                if (!types.isEmpty ()) {
                    Set<Class<?>> temp = new HashSet<> ();
                    for (Class<?> type : types) {
                        Object o = mappedByType.get (type);
                        if (o == bean) {
                            temp.add (type);
                        } else if (o instanceof InnerList) {
                            InnerList il = (InnerList) o;
                            il.remove (bean);
                            if (il.isEmpty ()) {
                                temp.add (type);
                            }
                        }
                    }
                    if (!temp.isEmpty ()) {
                        for (Class<?> type : temp) {
                            mappedByType.remove (type);
                        }
                    }
                }

                if (resolved.get ()) {
                    // 查找bean是否有标注为 PreDestroy 的方法
                    try {
                        destroyBean (bean);
                    } catch (Exception ex) {
                        logger.warn (ex.getMessage (), ex);
                    }
                }
            }
        } finally {
            LOCKER.unlock ();
        }
    }

    @Override
    public void remove (String name) {
        Object bean = getBean (name);
        if (bean != null) {
            remove (bean);
        }
    }

    public void register (String name, Object bean) throws InstanceNotFoundException, IllegalAccessException, InvocationTargetException, InstantiationException, IntrospectionException {
        if (bean == null) {
            throw new InstanceNotFoundException ("cannot register a null object");
        }

        try {
            LOCKER.lock ();
            // 判断名称是否已经注册过了
            if (mappedByName.containsKey (name)) {
                throw new InstanceNotUniqueException ("name [" + name + "] already exist!");
            }

            // 添加到命名映射中
            mappedByName.put (name, bean);

            // 为了能够在客户代码中通过实例的任意级别的类 (java.lang.Object除外) 来索引实例
            // 这里必须展开这个实例的继承树
            Set<Class<?>> types = new HashSet<> ();
            // 获取继承树上的各个类型
            findAllType (bean.getClass (), types);

            if (logger.isTraceEnabled ()) {
                logger.trace ("found all types: {}", types);
            }

            if (!types.isEmpty ()) {
                // 预处理方法
                Method postConstruct = null;
                Set<Field> set = new HashSet<> ();
                for (Class<?> type : types) {
                    if (!mappedByType.containsKey (type)) {
                        // 若这个类型的实例未被映射过，直接映射
                        mappedByType.put (type, bean);
                    } else {
                        // 曾经映射过
                        Object o = mappedByType.get (type);
                        if (o instanceof InnerList) {
                            // 如果类型索引的是一个列表，往列表中添加实例
                            InnerList il = (InnerList) o;
                            if (!il.contains (bean)) {
                                il.add (bean);
                            }
                        } else {
                            // 将原先映射的实例转成列表
                            InnerList il = new InnerList ();
                            il.add (o);
                            il.add (bean);
                            mappedByType.put (type, il);
                        }
                    }

                    // 曾经已经解决了依赖注入，当对象被注入后，需要再次解决注入依赖
                    if (resolved.get ()) {
                        if (!type.isInterface ()) {
                            Method method = resolve (bean, type);
                            if (method != null) {
                                if (postConstruct != null) {
                                    throw new InvocationTargetException (null, "Only ONE method can be annotated as PostConstruct.");
                                }
                                postConstruct = method;
                            }
                        }
                    }

                    Field[] fields = type.getDeclaredFields ();
                    for (Field field : fields) {
                        if (field.isAnnotationPresent (AConfigured.class)) {
                            // 标注为配置注入的字段
                            set.add (field);
                        }
                    }
                }

                // 执行预处理方法
                if (postConstruct != null) {
                    postConstruct.invoke (bean);
                }

                if (!set.isEmpty ()) {
                    // 配置注入
                    IConfiguration conf = getBean (IConfiguration.class);
                    if (conf != null) {
                        configureFields (conf, bean, set);
                    }
                }
            }
        } finally {
            LOCKER.unlock ();
        }
    }

    public void register (Object bean) throws InstanceNotFoundException, IllegalAccessException, InvocationTargetException, InstantiationException, IntrospectionException {
        if (bean == null) {
            throw new InstanceNotFoundException ("cannot register a null object");
        }
        Class<?> type = bean.getClass ();
        register (getSimpleName (type), bean);
    }

    public void create (IObjectCreateFactory factory, Class<?>... interfaces) {
        try {
            LOCKER.lock ();
            Object o = factory.create (interfaces);
            if (o != null) {
                String uuid = StringUtil.uuid ();
                mappedByName.put (uuid, o);

                for (Class<?> type : interfaces) {
                    if (type.isAssignableFrom (o.getClass ())) {
                        if (!mappedByType.containsKey (type)) {
                            mappedByType.put (type, o);
                        } else {
                            Object old = mappedByType.get (type);
                            if (old instanceof InnerList) {
                                ((InnerList) old).add (o);
                            } else {
                                InnerList il = new InnerList ();
                                il.add (old);
                                il.add (o);
                                mappedByType.put (type, il);
                            }
                        }
                    }
                }
            }
        } finally {
            LOCKER.unlock ();
        }
    }

    /**
     * 销毁托管容器
     * 分别针对所有受托管对象进行销毁
     *
     * @see #destroyBean(Object)
     */
    @Override
    public void dispose () {
        if (logger.isTraceEnabled ()) {
            logger.trace ("disposing simple object context");
        }

        for (Object o : mappedByName.values ()) {
            try {
                destroyBean (o);
            } catch (Exception ex) {
                if (logger.isTraceEnabled ()) {
                    logger.warn (ex.getMessage (), ex);
                }
            }
        }
        mappedByName.clear ();
        mappedByType.clear ();
    }

    /**
     * 解决依赖注入
     */
    public void resolve () {
        if (resolved.get ()) {
            // 已经解决了依赖注入，不可再次调用该方法
            throw new IllegalStateException ("context already resolved");
        }

        if (logger.isTraceEnabled ()) {
            logger.trace ("context resolved");
            logger.trace ("executing all inject resolved processors...");
        }
        if (!processors.isEmpty ()) {
            List<IInjectResolvedProcessor> list = new ArrayList<> (processors);
            Collections.sort (list);
            try {
                for (IInjectResolvedProcessor processor : list) {
                    processor.perform (this);
                }
            } catch (Exception ex) {
                throw new RuntimeException (ex);
            }
        }
        if (logger.isTraceEnabled ()) {
            logger.trace ("all inject resolved processors performed");
        }

        bindShutdownHook ();

        resolved.set (true);
    }

    /**
     * 获取所有已经注册的实例
     * @return 所有已经注册的实例
     */
    public Set<Object> getAllRegisteredBeans () {
        Set<Object> set = new HashSet<> (mappedByName.values ());
        return Collections.unmodifiableSet (set);
    }

    /**
     * 获取所有针对容器的注解
     *
     * @return 所有针对容器的注解
     * @since 1.1.0
     */
    @Override
    public Annotation[] getContextAnnotation () {
        return (Annotation[]) getBean (CONTEXT_ANNOTATION_KEY);
    }

    public Set<String> getAllBeanNames () {
        Set<String> names = new HashSet<> (mappedByName.keySet ());
        return Collections.unmodifiableSet (names);
    }

    /**
     * 解决依赖注入
     * @param wrappers 扫描器的包裹类
     * @throws Exception 任何异常
     */
    void resolve (Set<ClassScanner.Wrapper> wrappers) throws Exception {
        for (ClassScanner.Wrapper w : wrappers) {
            if (logger.isTraceEnabled ()) {
                logger.trace ("injecting fields in {}...", w.type);
            }
            // 若有需要注入的字段
            if (!w.injectFields.isEmpty ()) {
                for (Field field : w.injectFields) {
                    injectField (w.bean, field);
                }
            }

            if (logger.isTraceEnabled ()) {
                logger.trace ("all fields injected.");
                logger.trace ("injecting all methods in {} ...", w.type);
            }
            // 若有需要注入的字段
            if (!w.injectMethods.isEmpty ()) {
                for (ClassScanner.MethodWrapper mw : w.injectMethods) {
                    injectMethod (mw.name, w.bean, mw.method);
                }
            }
            if (logger.isTraceEnabled ()) {
                logger.trace ("all methods injected.");
            }
        }

        if (logger.isTraceEnabled ()) {
            logger.trace ("processing all post constructs...");
        }

        for (ClassScanner.Wrapper w : wrappers) {
            if (w.isProcessor) {
                processors.add ((IInjectResolvedProcessor) w.bean);
            }
            if (w.postConstruct != null) {
                w.postConstruct.invoke (w.bean);
            }
        }
        if (logger.isTraceEnabled ()) {
            logger.trace ("all post constructs process done.");
            logger.trace ("resolving the context");
        }
    }

    /**
     * 解决依赖注入
     * @param bean 对象实例
     * @param type 对象类型
     * @return 如果该类有一个方法被标注为预处理方法，返回这个被标注的方法，否则返回 {@code null}
     * @throws InvocationTargetException 当无法注入 setter 时抛出
     * @throws IllegalAccessException 当无法调用相应方法时抛出
     * @throws InstanceNotFoundException 当注入的资源不存在时抛出
     * @throws InstantiationException 当无法实例化时抛出
     * @throws IntrospectionException 内省异常
     */
    private Method resolve (Object bean, Class<?> type) throws InvocationTargetException, IllegalAccessException, InstanceNotFoundException, InstantiationException, IntrospectionException {
        Method postConstruct = null;
        // 注入需要注入的字段
        Field[] fields = type.getDeclaredFields ();
        for (Field field : fields) {
            if (field.isAnnotationPresent (Resource.class)) {
                injectField (bean, field);
            }
        }
        // 注入需要处理的方法
        Method[] methods = type.getDeclaredMethods ();
        for (Method method : methods) {
            if (method.isAnnotationPresent (Resource.class)) {
                int code = map (method);
                switch (code) {
                    case 1: // 标注为 Resource 的方法
                        processResourceMethod (bean, method);
                        break;
                    case 2: // 标注为 PostConstruct 的方法
                        postConstruct = method;
                        break;
                }
            }
        }

        return postConstruct;
    }

    /**
     * 将资源注入字段
     * @param bean  对象实例
     * @param field 被标注为自动注入资源的字段
     * @throws InstanceNotFoundException 被注入的资源不存在时抛出
     * @throws IllegalAccessException 无法访问目标字段时抛出
     */
    private void injectField (Object bean, Field field) throws InstanceNotFoundException, IllegalAccessException {
        Class<?> ft = field.getType ();
        Object value;
        if (IObjectContext.class.isAssignableFrom (ft)) {
            value = this;
        } else {
            Resource res = field.getAnnotation (Resource.class);
            if (!StringUtil.isEmpty (res.name ())) {
                value = getBean (res.name ());
            } else {
                value = getBean (ft);
            }
        }

        if (value == null) {
            throw new InstanceNotFoundException ("field " + field + " cannot be injected. The annotated object was not registered.");
        }

        if (!field.isAccessible ()) {
            field.setAccessible (true);
        }

        field.set (bean, value);
    }

    /**
     * 将资源通过 setter 方法注入
     * @param name   资源名称. 若该参数为 "" 或 {@code null} 时将使用 setter 的参数类型为索引来查找资源
     * @param bean   对象实例
     * @param method 自动注入的 setter
     * @throws InstanceNotFoundException 被注入的资源不存在时抛出
     * @throws InvocationTargetException 无法调用 setter 时抛出
     * @throws IllegalAccessException 无法访问 setter 时抛出
     */
    private void injectMethod (String name, Object bean, Method method) throws InstanceNotFoundException, InvocationTargetException, IllegalAccessException {
        Object value;
        if (!StringUtil.isEmpty (name)) {
            value = getBean (name);
        } else {
            Class<?> type = method.getParameterTypes ()[0];
            if (IObjectContext.class.isAssignableFrom (type)) {
                value = this;
            } else {
                value = getBean (type);
            }
        }

        if (value == null) {
            throw new InstanceNotFoundException ("method " + method + " cannot be injected. The annotated object was not registered.");
        }
        method.invoke (bean, value);
    }

    /**
     * 处理被标注为资源的方法。
     *
     * <p>{@code getter} 和 {@code setter} 都可能被标注为资源</p>
     * <ul>
     * <li>当一个 setter 被标注为资源时，意味着需要将资源注入</li>
     * <li>当一个 getter 被标注为资源时，意味着返回值需要注册到托管容器内</li>
     * </ul>
     * @param bean   对象实例
     * @param method 标注为资源的 getter 或 setter
     * @throws InstantiationException 当 getter 返回 {@code null} 时抛出
     * @throws InvocationTargetException 当无法调用方法时抛出
     * @throws IllegalAccessException 当无法访问方法时抛出
     * @throws InstanceNotFoundException 内省异常： <ul>
     * <li>若一个方法以 {@code get} 开头，参数表<strong>必须</strong>为空，且返回类型不能是 {@code void}</li>
     * <li>若一个方法以 {@code set} 开头，参数表<strong>有且仅有</strong>一个参数，且<strong>不能有</strong>返回类型</li>
     *</ul>
     */
    private void processResourceMethod (Object bean, Method method) throws
            InstantiationException,
            InvocationTargetException,
            IllegalAccessException,
            InstanceNotFoundException,
            IntrospectionException
    {
        String name = method.getName ();
        Resource res = method.getAnnotation (Resource.class);

        if (name.startsWith ("get")) {  // getter 方法，意味着应该将返回值注入到容器内
            Class<?> type = method.getReturnType ();
            if (type == void.class || type == Void.class) {
                throw new IntrospectionException ("a method annotated as Resource getter MUST return something");
            }
            if (method.getParameterCount () != 0) {
                throw new IntrospectionException ("a method annotated as Resource getter CANNOT have any parameters");
            }
            String beanName = res.name ();
            if (StringUtil.isEmpty (beanName)) {
                beanName = type.getSimpleName ();
                beanName = Character.toLowerCase (beanName.charAt (0)) + beanName.substring (1);
            }
            Object value = method.invoke (bean);
            if (value == null) {
                throw new InstantiationException ("The method " + method + " returns null");
            }
            register (beanName, value);
        } else if (name.startsWith ("set")) { // setter 方法，意味着应该注入参数类型的对象
            if (method.getParameterCount () != 1) {
                throw new IntrospectionException ("a method annotated as Resource setter MUST HAVE ONLY ONE parameter");
            }
            Class<?> type = method.getParameterTypes ()[0];
            Object value;
            if (type.isAssignableFrom (getClass ())) {
                value = this;
            } else {
                if (!StringUtil.isEmpty (res.name ())) {
                    value = getBean (res.name ());
                } else {
                    value = getBean (type);
                }
            }
            if (value == null) {
                throw new InstanceNotFoundException ("cannot find bean: " + type);
            }
            method.invoke (bean, value);
        }
    }

    /**
     * 销毁一个实例。
     *
     * <p>如果给定的实例有一个方法被标注为 {@link javax.annotation.PreDestroy}，在销毁这个对象前必须调用</p>
     *
     * 销毁一个实例同时也会删除将这个实例在托管容器内的引用
     * @param bean 对象实例
     * @throws InvocationTargetException 当无法调用销毁前的处理方法时抛出
     * @throws IllegalAccessException 当无法访问销毁前处理方法时抛出
     */
    private void destroyBean (Object bean) throws InvocationTargetException, IllegalAccessException {
        Class<?> type = bean.getClass ();
        Method[] methods = type.getMethods ();
        for (Method method : methods) {
            if (map (method) == 3) {    // 标注为 PreDestroy 的方法
                if (logger.isTraceEnabled ()) {
                    logger.trace ("invoking pre-destroy method: {}", method);
                }
                method.invoke (bean);
                return;
            }
        }
    }

    /**
     * 展开类的继承树，并将每个层级的类放在集合中
     * @param baseType 基本类型
     * @param types    出参。每个层级的类型都会被放在这个集合中
     */
    private void findAllType (Class<?> baseType, Set<Class<?>> types) {
        Class<?> type = baseType;
        while (type != null && type != Object.class) {
            types.add (type);

            Class<?>[] temp = type.getInterfaces ();
            for (Class<?> t : temp) {
                String name = t.getCanonicalName ();
                if (exclude (name)) {
                    continue;
                }
                findAllType (t, types);
            }

            type = type.getSuperclass ();
        }
    }

    /**
     * 是否是排除 {@link #EXCLUDE_PREFIXES} 列表中的类
     * @param name 类的全限定名称
     * @return 若是返回 {@code true}，否则 {@code false}
     */
    private boolean exclude (String name) {
        if (name.startsWith ("java.util.concurrent.")) {
            return false;
        }
        for (String prefix : EXCLUDE_PREFIXES) {
            if (name.startsWith (prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 映射方法类型。
     * <ul>
     * <li>只有public方法才会被映射</li>
     * <li>标注为 {@link Resource} 的方法映射为 1</li>
     * <li>标注为 {@link PostConstruct} 的方法映射为 2</li>
     * <li>标注为 {@link PreDestroy} 的方法映射为 3</li>
     * <li>其他映射为 -1</li>
     * </ul>
     * @param method 需要映射的方法
     * @return 映射后的代码
     */
    private int map (Method method) {
        int code = -1;
        if (method.isAnnotationPresent (Resource.class)) {
            code = 1;
        } else if (method.isAnnotationPresent (PostConstruct.class)) {
            code = 2;
        } else if (method.isAnnotationPresent (PreDestroy.class)) {
            if (logger.isTraceEnabled ()) {
                logger.trace ("found a pre-destroy method of {}: {}", method.getDeclaringClass (), method);
            }
            code = 3;
        }

        int modifier = method.getModifiers ();
        if ((modifier & Modifier.PUBLIC) != 0) {
            return code;
        }
        return -1;
    }

    /**
     * 注入配置
     * @param conf   全局配置对象
     * @param bean   对象实例
     * @param fields 需注入配置的所有字段
     * @throws IllegalAccessException 当访问字段异常时抛出
     */
    static void configureFields (IConfiguration conf, Object bean, Collection<Field> fields) throws IllegalAccessException {
        final Logger logger = LoggerFactory.getLogger (SimpleObjectContext.class);
        Gson g = new Gson ();
        for (Field field : fields) {
            AConfigured ac = field.getAnnotation (AConfigured.class);
            String key = ac.value ();
            if (StringUtil.isEmpty (key)) {
                key = ac.key ();
            }
            if (logger.isTraceEnabled ()) {
                logger.trace ("trying inject field {} with key {}", field, key);
            }

            if (StringUtil.isEmpty (key)) {
                Class<?> type = field.getDeclaringClass ();
                key = "${" + type.getCanonicalName () + "." + field.getName () + "}";
            }
            String expression;
            if (key.startsWith ("${") && key.endsWith ("}")) {
                key = key.substring (2, key.length () - 1);
                expression = conf.getString (key);
            } else {
                expression = key.trim ();
            }
            if (expression != null) {
                Object value;
                Class<?> type = field.getType ();
                if (type.isAssignableFrom (String.class)) {
                    value = expression;
                } else {
                    try {
                        value = g.fromJson (expression, type);
                    } catch (Exception ex) {
                        logger.error ("cannot convert {} to {} when injecting {}", expression, type, field);
                        throw new RuntimeException (ex);
                    }
                }
                if (value != null) {
                    if (!field.isAccessible ()) {
                        field.setAccessible (true);
                    }

                    field.set (bean, value);
                }
            } else if (ac.required ()) {
                throw new ConfigurationNotFoundException ("configuration item [" + key + "] not found, but it is required");
            }
        }
    }

    private void bindShutdownHook () {
        int port;
        if (shutdownPort == 0) {
            if (logger.isTraceEnabled ()) {
                logger.trace ("determine shutdown port ...");
            }
            port = new SecureRandom ().nextInt (10) + 37500;
        } else if (shutdownPort > 1024) {
            if (logger.isTraceEnabled ()) {
                logger.trace ("using shutdown port what specified.");
            }
            port = shutdownPort;
        } else {
            if (logger.isTraceEnabled ()) {
                logger.warn ("invalid shutdown port: {}, ignore that", shutdownPort);
            }
            port = -1;
        }
        if (port > 0) {
            if (logger.isInfoEnabled ()) {
                logger.info ("trying to bind shutdown hook on port: {}", port);
            }

            try {
                ShutdownHook.bind (this, port);
            } catch (IOException ex) {
                logger.warn (ex.getMessage (), ex);
            }
        }
    }

    /**
     * 将java类型名称转换成 java 属性风格的字符串
     * @param type java 类型
     * @return java 属性风格的字符串
     */
    private static String getSimpleName (Class<?> type) {
        String name = type.getSimpleName ();
        if (name.length () == 1) {
            return name.toLowerCase ();
        }
        return Character.toLowerCase (name.charAt (0)) + name.substring (1);
    }

    public static final class InnerList extends ArrayList<Object> {}
}
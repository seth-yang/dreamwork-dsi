package org.dreamwork.dsi.embedded.httpd.starter;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.Wrapper;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.dreamwork.concurrent.Looper;
import org.dreamwork.dsi.embedded.httpd.annotation.AWebPackages;
import org.dreamwork.dsi.embedded.httpd.support.BackendServlet;
import org.dreamwork.dsi.embedded.httpd.support.WebComponentScanner;
import org.dreamwork.injection.AConfigured;
import org.dreamwork.injection.IObjectContext;
import org.dreamwork.injection.impl.ScannerHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServlet;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Set;

@Resource
public class EmbeddedTomcatStarter {
    private final Logger logger = LoggerFactory.getLogger (EmbeddedTomcatStarter.class);

    private final Tomcat tomcat = new Tomcat ();

    @AConfigured ("${embedded.httpd.context-path}")
    private String contextPath = "/";

    @AConfigured ("${embedded.httpd.base}")
    private String docBase = "../webapp";

    @AConfigured ("${embedded.httpd.port}")
    private int port = 9090;

    @AConfigured ("${embedded.httpd.host}")
    private String host = "127.0.0.1";

    @AConfigured ("${embedded.httpd.views.extension}")
    private String ext = ".jsp";

    @AConfigured ("${embedded.httpd.api-mapping}")
    private String mapping = "/apis";

    @AConfigured ("${embedded.httpd.delegate.enabled}")
    private boolean delegate = false;

    @Resource
    private IObjectContext context;

    private StandardContext webContext;

    @SuppressWarnings ("unused")
    public String getContextPath () {
        return contextPath;
    }

    @SuppressWarnings ("unused")
    public void setContextPath (String contextPath) {
        this.contextPath = contextPath;
    }

    @SuppressWarnings ("unused")
    public String getDocBase () {
        return docBase;
    }

    @SuppressWarnings ("unused")
    public void setDocBase (String docBase) {
        this.docBase = docBase;
    }

    @SuppressWarnings ("unused")
    public int getPort () {
        return port;
    }

    @SuppressWarnings ("unused")
    public void setPort (int port) {
        this.port = port;
    }

    @SuppressWarnings ("unused")
    public String getHost () {
        return host;
    }

    @SuppressWarnings ("unused")
    public void setHost (String host) {
        this.host = host;
    }

    @SuppressWarnings ("unused")
    public String getMapping () {
        return mapping;
    }

    @SuppressWarnings ("unused")
    public void setMapping (String mapping) {
        this.mapping = mapping;
    }

    public String getExt () {
        return ext;
    }

    public void setExt (String ext) {
        this.ext = ext;
    }

    public boolean isDelegate () {
        return delegate;
    }

    public void setDelegate (boolean delegate) {
        this.delegate = delegate;
    }

    @PostConstruct
    public void startTomcat () throws Exception {
        if (logger.isTraceEnabled ()) {
            logger.trace ("starting embedded tomcat ...");
            logger.trace ("    context-path = {}", contextPath);
            logger.trace ("        doc-base = {}", docBase);
            logger.trace ("            port = {}", port);
            logger.trace ("            host = {}", host);
        }

        File base = new File ("../.embedded-httpd");
        if (!base.exists () && !base.mkdirs ()) {
            throw new IOException ("cannot create base dir: " + base.getCanonicalPath ());
        }

        File serverRoot = new File (base, "webapps");
        if (!serverRoot.exists () && !serverRoot.mkdirs ()) {
            throw new IOException ("cannot create webapps dir: " + serverRoot.getCanonicalPath ());
        }

        File root = new File (serverRoot, "ROOT");
        if (!root.exists () && !root.mkdirs ()) {
            throw new IOException ("cannot create root dir: " + root.getCanonicalPath ());
        }

        File tmp = new File (base, "tmp");
        if (!tmp.exists () && !tmp.mkdirs ()) {
            throw new IOException ("cannot create tmp dir: " + tmp.getCanonicalPath ());
        }
        System.setProperty ("java.io.tmpdir", tmp.getCanonicalPath ());

        tomcat.setBaseDir (base.getCanonicalPath ());
        tomcat.setPort (port);
        tomcat.getServer ().setAddress (host);
        tomcat.setHostname (host);
        tomcat.getConnector ();
        if (contextPath.charAt (0) != '/') {
            contextPath = '/' + contextPath;
        }

        File webapp = new File (docBase);
        if (!webapp.exists ()) {
            webapp = new File (tmp, ".embedded-httpd");
            if (!webapp.exists () && !webapp.mkdirs ()) {
                throw new IOException ("cannot find webapp: " + webapp.getCanonicalPath ());
            }
        }

        final ClassLoader currentLoader = getClass ().getClassLoader ();
        webContext = (StandardContext) tomcat.addWebapp (contextPath, webapp.getCanonicalPath ());
        // 设置tomcat上下文的classloader为当前类的classloader，
        // 确保所有类能够在正确的作用域内被加载
        webContext.setParentClassLoader (currentLoader);
        context.register ("webContext", webContext.getServletContext ());
        ServletContext app = webContext.getServletContext ();
        webContext.addWelcomeFile ("index.htm");
        webContext.addWelcomeFile ("index.html");
        webContext.addWelcomeFile ("index.jsp");
        webContext.addWelcomeFile ("index.jasmine");
        // 在 ServletContext 中设置 IObjectContext 实例，键名为 org.dreamwork.injection.IObjectContext
        app.setAttribute (IObjectContext.class.getCanonicalName (), context);
        app.setAttribute ("embedded.httpd.views.extension", ext);
        if (delegate)
            app.setAttribute ("embedded.httpd.delegate.enabled", true);

        // @since 1.1.0
        scanWebComponents (context);

        Wrapper w = Tomcat.addServlet (webContext, "apis", BackendServlet.class.getCanonicalName ());
        w.setParentClassLoader (currentLoader);
        if (mapping.charAt (0) != '/') {
            mapping = '/' + mapping;
        }
        String leading;
        if (mapping.charAt (mapping.length () - 1) == '/') {
            leading = "*";
        } else {
            leading = "/*";
        }
        w.addMapping (mapping + leading);

        Looper.invokeLater (() -> {
            try {
                tomcat.start ();
                logger.info ("embedded tomcat started.");
            } catch (LifecycleException e) {
                e.printStackTrace ();
            }
        });
    }

    public void mapServlet (String servletName, Class<? extends HttpServlet> type, String... patterns) {
        Wrapper w = Tomcat.addServlet (webContext, servletName, type.getCanonicalName ());
        w.setParentClassLoader (getClass ().getClassLoader ());
        for (String p : patterns) {
            w.addMapping (p);
        }
    }

    @PreDestroy
    public void destroy () throws LifecycleException {
        tomcat.stop ();
        logger.info ("tomcat stopped.");
    }

    /**
     * 扫描web相关组件
     * @param context 托管容器
     * @throws Exception 如果扫描过程中发生任何异常
     * @since 1.1.0
     */
    private void scanWebComponents (IObjectContext context) throws Exception {
        ClassLoader loader = getClass ().getClassLoader ();
        Annotation[] annotations = context.getContextAnnotation ();
        if (annotations != null && annotations.length > 0) {
            Set<String> set = new HashSet<> ();
            for (Annotation annotation : annotations) {
                if (annotation instanceof AWebPackages) {
                    AWebPackages wp = (AWebPackages) annotation;
                    String[] names = wp.packageNames ();
                    if (names == null || names.length == 0) {
                        names = wp.value ();
                    }
                    for (String base : names) {
                        set.add (base);
                        if (wp.recursive ()) {
                            ScannerHelper.fillPackageNames (loader, base, set);
                        }
                    }
                }
            }
            if (!set.isEmpty ()) {
                String[] names = set.toArray (new String[0]);
                WebComponentScanner scanner = new WebComponentScanner (webContext);
                scanner.scan (names);
            }
        }
    }
}
package org.dreamwork.dsi.embedded.httpd.support.websocket;

import com.google.gson.Gson;
import org.dreamwork.config.IConfiguration;
import org.dreamwork.injection.AConfigured;
import org.dreamwork.injection.IObjectContext;
import org.dreamwork.util.ReferenceUtil;
import org.dreamwork.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.annotation.Resources;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.websocket.HandshakeResponse;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Created by game on 2017/3/4
 *
 * @since 2.1.0
 */
public class WebSocketHttpConfigurator extends ServerEndpointConfig.Configurator {
    private static final Logger logger = LoggerFactory.getLogger (WebSocketHttpConfigurator.class);

    public static final String KEY_HTTP_SESSION = "org.dreamwork.websocket.conf.KEY_HTTP_SESSION";
    public static final String KEY_HTTP_HEADER  = "org.dreamwork.websocket.conf.KEY_HTTP_HEADER";
    public static final String KEY_HTTP_CONTEXT = "org.dreamwork.websocket.conf.KEY_HTTP_CONTEXT";
    public static final String KEY_HTTP_PARAM   = "org.dreamwork.websocket.conf.KEY_HTTP_PARAM";
    public static final String KEY_HTTP_QUERY   = "org.dreamwork.websocket.conf.KEY_HTTP_QUERY_STRING";
    public static final String KEY_HTTP_URI     = "org.dreamwork.websocket.conf.KEY_HTTP_URI";

    private static final String TOMCAT_WS_REQUEST_CLASS = "org.apache.tomcat.websocket.server.WsHandshakeRequest";

    private static IObjectContext context;

    boolean httpSessionSupported, httpHeaderSupported, servletContextSupported, httpParameterSupported;

    public static void setObjectContext (IObjectContext context) {
        WebSocketHttpConfigurator.context = context;
    }

    WebSocketHttpConfigurator () {}

    WebSocketHttpConfigurator (boolean httpSessionSupported, boolean httpHeaderSupported, boolean servletContextSupported, boolean httpParameterSupported) {
        this.httpSessionSupported = httpSessionSupported;
        this.httpHeaderSupported = httpHeaderSupported;
        this.servletContextSupported = servletContextSupported;
        this.httpParameterSupported = httpParameterSupported;
    }

    @Override
    public void modifyHandshake (ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
        ServletContext webapp = null;
        HttpSession session = (HttpSession) request.getHttpSession ();
        if (session == null) {
            Class<?> type = request.getClass ();
            if (TOMCAT_WS_REQUEST_CLASS.equals (type.getCanonicalName ())) {
                try {
                    HttpServletRequest http = (HttpServletRequest) ReferenceUtil.get (request, "request");
                    webapp = http.getServletContext ();
                } catch (Exception ex) {
                    logger.warn (ex.getMessage (), ex);
                }
            }
        }
        Map<String, Object> map = sec.getUserProperties ();

        if (httpHeaderSupported) {
            map.put (KEY_HTTP_HEADER, request.getHeaders ());
            if (logger.isTraceEnabled ()) {
                logger.trace ("put HTTP HEADER as " + KEY_HTTP_HEADER);
                logger.trace ("    the headers = {}", request.getHeaders ());
            }
        }

        if (httpParameterSupported) {
            map.put (KEY_HTTP_PARAM, request.getParameterMap ());
            if (logger.isTraceEnabled ()) {
                logger.trace ("put HTTP PARAMETERS as " + KEY_HTTP_PARAM);
                logger.trace ("    the parameters = {}", request.getParameterMap ());
            }
        }

        if (httpSessionSupported && session != null) {
            map.put (KEY_HTTP_SESSION, session);
            if (logger.isTraceEnabled ()) {
                logger.trace ("put HTTP SESSION as " + KEY_HTTP_SESSION);
                logger.trace ("    the httpSession = {}", session);
            }
        }

        if (servletContextSupported) {
            if (session != null) {
                map.put (KEY_HTTP_CONTEXT, session.getServletContext ());
                if (logger.isTraceEnabled ()) {
                    logger.trace ("put Servlet Context as {}", session.getServletContext ());
                }
            } else if (webapp != null) {
                map.put (KEY_HTTP_CONTEXT, webapp);
            }
        }

        String queryString = request.getQueryString ();
        if (!StringUtil.isEmpty (queryString)) {
            map.put (KEY_HTTP_QUERY, queryString);
        }
        map.put (KEY_HTTP_URI, request.getRequestURI ());

        if (logger.isTraceEnabled ()) {
            logger.trace ("put HTTP QUERY STRING as " + KEY_HTTP_QUERY);
            logger.trace ("    the query string = {}", request.getQueryString ());
            logger.trace ("put HTTP URI as " + KEY_HTTP_URI);
            logger.trace ("    the uri = {}", request.getRequestURI ());
        }
    }

    @Override
    public <T> T getEndpointInstance (Class<T> type) throws InstantiationException {
        T instance = super.getEndpointInstance (type);
        Cache c = cache.computeIfAbsent (type, key -> parseType (type));
        injectFields (instance, c);
        injectMethod (instance, c);
        if (c.starter != null) {
            try {
                c.starter.invoke (instance);
            } catch (Exception ex) {
                throw new RuntimeException (ex);
            }
        }
        return instance;
    }

    private Cache parseType (Class<?> type) {
        try {
            Cache c = new Cache ();
            findField (type, c.fields, c.config);
            findMethods (type, c);
            return c;
        } catch (Exception ex) {
            throw new RuntimeException (ex);
        }
    }

    private static<T> void injectFields (T instance, Cache c) throws InstantiationException {
        Gson g = new Gson ();
        if (!c.fields.isEmpty ()) { // 注入字段
            for (Field field : c.fields) {
                Class<?> ft = field.getType ();
                Resource res = field.getAnnotation (Resource.class);
                Object target;
                if (ft.isAssignableFrom (context.getClass ())) {
                    target = context;
                } else {
                    String name = res.name ();
                    if (StringUtil.isEmpty (name)) {
                        target = context.getBean (ft);
                    } else {
                        target = context.getBean (name);
                    }
                }
                if (target != null) {
                    try {
                        field.set (instance, target);
                    } catch (Exception ex) {
                        throw new InstantiationException ("cannot inject field: " + field.getName ());
                    }
                }
            }
        }
        if (!c.config.isEmpty ()) { // 注入配置
            for (Field field : c.config) {
                Class<?> ft = field.getType ();
                AConfigured conf = field.getAnnotation (AConfigured.class);
                String key = getKey (field, conf);

                IConfiguration bean = context.getBean (IConfiguration.class);
                String expression = bean.getString (key);
                Object target;
                if (!StringUtil.isEmpty (expression)) {
                    if (ft.isAssignableFrom (String.class)) {
                        target = expression;
                    } else {
                        try {
                            target = g.fromJson (expression, ft);
                        } catch (Exception ex) {
                            logger.error ("cannot convert {} to {} when injecting {}", expression, field.getDeclaringClass (), field);
                            throw new RuntimeException (ex);
                        }
                    }
                    try {
                        field.set (instance, target);
                    } catch (Exception ex) {
                        throw new InstantiationException ("cannot inject field: " + field.getName ());
                    }
                } else if (conf.required ()) {
                    throw new InstantiationException ("cannot inject config for " + field);
                }
            }
        }
    }

    private static<T> void injectMethod (T instance, Cache c) throws InstantiationException {
        if (!c.methods.isEmpty ()) {
            for (Method method : c.methods) {
                int count = method.getParameterCount ();
                if (count == 1 && method.isAnnotationPresent (Resources.class)) {
                    Resource res = method.getAnnotation (Resource.class);
                    Class<?> pt  = method.getParameterTypes ()[0];
                    Object target = getValue (res, pt);
                    if (target != null) {
                        try {
                            method.invoke (instance, target);
                        } catch (Exception ex) {
                            logger.warn (ex.getMessage (), ex);
                            throw new InstantiationException ("cannot inject method: " + method + ", cause of " + ex.getMessage ());
                        }
                    }
                } else if (count > 1) {
                    Object[] args = new Object[count];
                    Annotation[][] as = method.getParameterAnnotations ();
                    Class<?>[] pts = method.getParameterTypes ();
                    for (int i = 0; i < count; i ++) {
                        Resource res = null;
                        for (Annotation[] a : as) {
                            for (Annotation annotation : a) {
                                if (annotation instanceof Resource) {
                                    res = (Resource) annotation;
                                    break;
                                }
                            }
                            if (res == null) {
                                throw new InstantiationException ("cannot find resource injected by parameter index: " + i);
                            }

                            args[i] = getValue (res, pts[i]);
                        }
                    }

                    try {
                        method.invoke (instance, args);
                    } catch (Exception ex) {
                        logger.warn (ex.getMessage (), ex);
                        throw new InstantiationException ("cannot inject resource for method: " + method);
                    }
                }
            }
        }
    }

    private static String getKey (Field field, AConfigured conf) throws InstantiationException {
        String key = conf.key ();
        boolean required = conf.required ();
        if (StringUtil.isEmpty (key)) {
            key = conf.value ();
        }
        if (key.startsWith ("${")) {
            key = key.substring (2);
        }
        if (key.endsWith ("}")) {
            key = key.substring (0, key.length () - 1);
        }
        if (required && StringUtil.isEmpty (key)) {
            throw new InstantiationException ("cannot inject config for " + field);
        }
        return key;
    }

    private static void findField (Class<?> type, Collection<Field> fieldsInjection, Collection<Field> configs) {
        Field[] fields = type.getDeclaredFields ();
        for (Field field : fields) {
            if (field.isAnnotationPresent (Resource.class)) {
                fieldsInjection.add (field);
            } else if (field.isAnnotationPresent (AConfigured.class)) {
                configs.add (field);
            }
        }

        Class<?> parent = type.getSuperclass ();
        if (parent != null && parent != Object.class) {
            findField (parent, fieldsInjection, configs);
        }
    }

    private static void findMethods (Class<?> type, Cache c) throws InstantiationException {
        Method[] methods = type.getDeclaredMethods ();
        for (Method method : methods) {
            int count = method.getParameterCount ();
            if (count == 0 && method.isAnnotationPresent (PostConstruct.class)) {
                if (c.starter != null) {
                    throw new InstantiationException ("there's more thant one method annotated by PostConstruct");
                }
                c.starter = method;
            } else if (count == 1 && method.isAnnotationPresent (Resource.class)) {
                c.methods.add (method);
            } else if (count > 1) {
                Annotation[][] pas = method.getParameterAnnotations ();
                for (Annotation[] pa : pas) {
                    boolean matches = false;
                    for (Annotation a : pa) {
                        if (a instanceof Resource) {
                            matches = true;
                            break;
                        }
                    }
                    if (!matches) {
                        throw new InstantiationException ("there's at least one parameter was not annotated by Resource annotation");
                    }
                }
                c.methods.add (method);
            }
        }

        Class<?> parent = type.getSuperclass ();
        if (parent != null && parent != Object.class) {
            findMethods (parent, c);
        }
    }

    private static Object getValue (Resource res, Class<?> type) {
        String name = res.name ();
        Object target;
        if (StringUtil.isEmpty (name)) {
            target = context.getBean (name);
        } else {
            target = context.getBean (type);
        }

        return target;
    }

    private static final class Cache {
        Collection<Field> fields = new HashSet<> ();
        Collection<Field> config = new HashSet<> ();
        Collection<Method> methods = new HashSet<> ();
        Method starter;
    }

    private static final Map<Class<?>, Cache> cache = new HashMap<> ();
}
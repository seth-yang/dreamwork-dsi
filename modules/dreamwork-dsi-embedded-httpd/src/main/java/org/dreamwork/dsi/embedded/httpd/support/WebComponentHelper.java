package org.dreamwork.dsi.embedded.httpd.support;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import jakarta.annotation.Resources;
import org.dreamwork.config.IConfiguration;
import org.dreamwork.injection.AConfigured;
import org.dreamwork.injection.IObjectContext;
import org.dreamwork.util.JsonHelper;
import org.dreamwork.util.ReferenceUtil;
import org.dreamwork.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;

public class WebComponentHelper {
    private final static Logger logger = LoggerFactory.getLogger (WebComponentHelper.class);
    private static IObjectContext context;

    public static void setContext (IObjectContext context) {
        WebComponentHelper.context = context;
    }

    public static<T> void injectFields (T instance, Cache c) {
        if (!c.fields.isEmpty ()) { // 注入字段
            for (var field : c.fields) {
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
                    ReferenceUtil.checkAccessible (field, instance);
                    try {
                        field.set (instance, target);
                    } catch (Exception ex) {
                        logger.warn (ex.getMessage (), ex);
                        throw new RuntimeException (ex);
                    }
                }
            }
        }
    }

    public static<T> void injectMethod (T instance, Cache c) throws InstantiationException {
        if (!c.methods.isEmpty ()) {
            for (var method : c.methods) {
                ReferenceUtil.checkAccessible (method, instance);
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

    private static<T> void injectConfig (T instance, Cache c) throws InstantiationException {
        if (!c.config.isEmpty ()) { // 注入配置
            for (var ao : c.config) {
                AConfigured conf = null;
                Class<?> ft = null;
                if (ao instanceof Field field) {
                    conf = field.getAnnotation (AConfigured.class);
                    ft = field.getType ();
                } else if (ao instanceof Method method) {
                    conf = method.getAnnotation (AConfigured.class);
                    ft = method.getParameterTypes ()[0];
                }

                if (conf != null) {
                    String key = getKey (ao, conf);
                    IConfiguration bean = context.getBean (IConfiguration.class);
                    String expression = bean.getString (key);
                    Object target;
                    if (!StringUtil.isEmpty (expression)) {
                        if (ft.isAssignableFrom (String.class)) {
                            target = expression;
                        } else {
                            try {
                                target = JsonHelper.fromJson (expression, ft);
                            } catch (Exception ex) {
                                logger.error ("cannot convert {} to {} when injecting {}", expression, ft, ao);
                                throw new RuntimeException (ex);
                            }
                        }
                        try {
                            ReferenceUtil.checkAccessible (ao, instance);
                            if (ao instanceof Field field) {
                                field.set (instance, target);
                            } else {
                                Method method = (Method) ao;
                                method.invoke (instance, target);
                            }
                        } catch (Exception ex) {
                            throw new InstantiationException ("cannot inject field: " + ao);
                        }
                    } else if (conf.required ()) {
                        throw new InstantiationException ("cannot inject config for " + ao);
                    }
                }
            }
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

    private static String getKey (AccessibleObject ao, AConfigured conf) throws InstantiationException {
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
            throw new InstantiationException ("cannot inject config for " + ao);
        }
        return key;
    }

    public static void findField (Class<?> type, Collection<Field> fieldsInjection, Collection<AccessibleObject> configs) {
        Field[] fields = type.getDeclaredFields ();
        for (var field : fields) {
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

    public static void findMethods (Class<?> type, Cache c) throws InstantiationException {
        Method[] methods = type.getDeclaredMethods ();
        for (var method : methods) {
            int count = method.getParameterCount ();
            if (count == 0) {
                if (method.isAnnotationPresent (PostConstruct.class)) {
                    if (c.starter != null) {
                        throw new InstantiationException ("there's more thant one method annotated by PostConstruct");
                    }
                    c.starter = method;
                } else if (method.isAnnotationPresent (PreDestroy.class)) {
                    if (c.destroyer != null) {
                        throw new InstantiationException ("there's more thant one method annotated by PreDestroy");
                    }
                    c.destroyer = method;
                }
            } else if (count == 1 && method.getName ().startsWith ("set")) { // all setters
                if (method.isAnnotationPresent (Resource.class)) {
                    c.methods.add (method);
                } else if (method.isAnnotationPresent (AConfigured.class)) {
                    c.config.add (method);
                }
            } else if (method.isAnnotationPresent (Resource.class)) {
                Annotation[][] pas = method.getParameterAnnotations ();
                for (var pa : pas) {
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

    public static Cache parseType (Class<?> type) {
        try {
            Cache c = new Cache ();
            findField (type, c.fields, c.config);
            findMethods (type, c);
            return c;
        } catch (Exception ex) {
            throw new RuntimeException (ex);
        }
    }

    public static<T> T inject (Cache c, T instance) throws InstantiationException {
        injectFields (instance, c);
        injectMethod (instance, c);
        injectConfig (instance, c);
        if (c.starter != null) {
            try {
                c.starter.invoke (instance);
            } catch (Exception ex) {
                throw new RuntimeException (ex);
            }
        }
        return instance;
    }
}
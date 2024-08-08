package org.dreamwork.dsi.embedded.httpd.support;

import org.dreamwork.dsi.embedded.httpd.annotation.*;
import org.dreamwork.util.StringUtil;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebMappedMethod {
    public static final Pattern PARSER = Pattern.compile ("^(.*?)?\\$\\{(.*?)}(.*?)?$");

    public final Method method;
    public final String pattern;
    public final HandlerType type;
    public String contentType, beanName;

    public List<String> parts;

    public List<WebParameter> parameters;
    /** @since 1.1.0 */
    public boolean wrapped;

    public WebMappedMethod (Method method, String pattern, HandlerType type) {
        this.method  = method;
        this.pattern = pattern;
        this.type    = type;

        if (pattern.startsWith ("/")) {
            pattern = pattern.substring (1);
        }

        if (pattern.contains ("/")) {
            parts = split (pattern);
        }

        if (method == null) {
            throw new NullPointerException ("method");
        }

        if (method.getParameterCount () > 0) {
            Annotation[][] parameterAnnotations = method.getParameterAnnotations ();
            if (method.getParameterCount () != parameterAnnotations.length) {
                throw new IllegalArgumentException ("some parameter of method " + method + " not annotated.");
            }

            parameters = new ArrayList<> (parameterAnnotations.length);
            for (Annotation[] as : parameterAnnotations) {
                if (as.length > 0) {
                    for (Annotation an : as) {
                        if (an instanceof AWebParameter) {
                            AWebParameter awp = (AWebParameter) an;

                            WebParameter wp = new WebParameter ();
                            if (awp.internal ()) {
                                wp.internal = true;
                            } else {
                                String name = awp.name ();
                                if (StringUtil.isEmpty (name)) {
                                    name = awp.value ();
                                }

                                if (StringUtil.isEmpty (name) && awp.location () != ParameterLocation.Body) {
                                    throw new IllegalArgumentException ("property[name] of AWebParameter is missing!");
                                }
                                wp.name = name.trim ();
                                if (!StringUtil.isEmpty (awp.defaultValue ())) {
                                    wp.defaultValue = awp.defaultValue ().trim ();
                                }
                                wp.type = awp.type ();
                                wp.location = awp.location ();
                            }
                            parameters.add (wp);

                            break;
                        } else if (an instanceof ARequestBody) {
                            WebParameter wp = new WebParameter ();
                            wp.type = ParameterType.raw;
                            wp.location = ParameterLocation.Body;
                            wp.contentType = "application/json;charset=utf-8";
                            parameters.add (wp);

                            break;
                        } else if (an instanceof AInternal) {
                            // @since 1.1.0
                            WebParameter wp = new WebParameter ();
                            wp.internal = true;
                            wp.location = ParameterLocation.Internal;
                            parameters.add (wp);
                        } else if (an instanceof ARequestAttribute) {
                            // @since 1.1.0
                            ARequestAttribute ra = (ARequestAttribute) an;
                            appendParameter (ParameterType.request_attribute, ra.name (), ra.value ());
                        } else if (an instanceof ASessionAttribute) {
                            // @since 1.1.0
                            ASessionAttribute sa = (ASessionAttribute) an;
                            appendParameter (ParameterType.session_attribute, sa.name (), sa.value ());
                        } else if (an instanceof AFormItem) {
                            // @since 1.1.1
                            AFormItem fi = (AFormItem) an;
                            WebParameter wp = createWebParameter (fi.type (), ParameterLocation.QueryString, fi.name (), fi.value ());
                            String dv = fi.defaultValue ();
                            if (!StringUtil.isEmpty (dv) && "$$EMPTY$$".equals (dv)) {
                                wp.defaultValue = dv;
                            }
                        } else if (an instanceof APathVariable) {
                            // @since 1.1.1
                            APathVariable pv = (APathVariable) an;
                            createWebParameter (pv.type (), ParameterLocation.Path, pv.name (), pv.value ());
                        } else if (an instanceof AHeaderItem) {
                            // @since 1.1.1
                            AHeaderItem hp = (AHeaderItem) an;
                            createWebParameter (hp.type (), ParameterLocation.Header, hp.name (), hp.value ());
                        } else if (an instanceof AManagedSessionAttribute) {
                            AManagedSessionAttribute msa = (AManagedSessionAttribute) an;
                            appendParameter (ParameterType.managed_session_attribute, msa.name (), msa.value ());
                        }
                    }
                } else {
                    WebParameter wp = new WebParameter ();
                    wp.internal = true;
                    wp.location = ParameterLocation.Internal;
                    parameters.add (wp);
                }
            }
        }
    }

    private WebParameter createWebParameter (ParameterType type, ParameterLocation location, String name1, String name2) {
        String name = name1;
        if (StringUtil.isEmpty (name)) {
            name = name2;
        }
        if (StringUtil.isEmpty (name)) {
            throw new IllegalArgumentException ("web parameter name is not set!");
        }
        WebParameter wp = new WebParameter ();
        wp.name = name;
        wp.type = type;
        wp.location = location;
        parameters.add (wp);
        return wp;
    }

    /**
     * 添加参数
     * @param type       参数类型
     * @param firstName  第一名称
     * @param secondName 第二名称
     * @since 1.1.0
     */
    private void appendParameter (ParameterType type, String firstName, String secondName) {
        WebParameter wp = new WebParameter ();
        wp.type = type;
        String name = firstName;
        if (StringUtil.isEmpty (name)) name = secondName;
        if (StringUtil.isEmpty (name)) {
            throw new IllegalArgumentException ("Parameter name not set");
        }
        wp.name = name;
        wp.location = ParameterLocation.Internal;
        parameters.add (wp);
    }

    public boolean matches (String pattern, Map<String, String> parsedArgs) {
        if (parts != null && !parts.isEmpty ()) {
            List<String> tmp = split (pattern);
            if (tmp.size () != parts.size ()) {
                return false;
            }
            Map<String, String> map = new HashMap<> ();
            for (int i = 0, n = tmp.size (); i < n; i ++) {
                String name = parts.get (i);
                if (name.equals ("*") && i == n - 1) {
                    // 最后一个部分是 * , 则不再比较后续部分
                    return true;
                } else if (name.contains ("${")) {
                    Matcher m = PARSER.matcher (name);
                    if (m.matches ())
                        map.put (m.group (2), tmp.get (i));
                } else if (!name.equals (tmp.get (i))) {    // 任意一部分不匹配，返回false
                    return false;
                }
            }
            if (!map.isEmpty ()) {
                parsedArgs.putAll (map);
            }
            return true;
        } else {
            return pattern.equals (this.pattern);
        }
    }

    public Object invoke (Object instance, Object... args) throws InvocationTargetException {
        try {
            return method.invoke (instance, args);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException (ex);
        }
    }

    private List<String> split (String pattern) {
        String[] tmp = pattern.split ("/");
        List<String> parts = new ArrayList<> (tmp.length);
        for (String p : tmp) {
            if (!StringUtil.isEmpty (p)) {
                parts.add (p.trim ());
            }
        }
        return parts;
    }
}
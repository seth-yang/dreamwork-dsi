package org.dreamwork.dsi.embedded.httpd.annotation;

import org.dreamwork.dsi.embedded.httpd.support.ParameterLocation;
import org.dreamwork.dsi.embedded.httpd.support.ParameterType;

import java.lang.annotation.*;

@Target ({ElementType.PARAMETER})
@Retention (RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface AWebParameter {
    String value () default ""; // alias for "name"
    String name () default "";
    // int, long, double, string, timestamp
    ParameterType type () default ParameterType.raw;
    String defaultValue () default "$$EMPTY$$";
    ParameterLocation location () default ParameterLocation.Path;

    /**
     * 指示该参数是否为web应用程序内置的类型。如
     * ServletContext, HttpSession, ServletHttpRequest, ServletHttpResponse 等
     * @return 如果是以上4种类型应该设置为 true，否则设置为 false
     */
    boolean internal () default false;
}
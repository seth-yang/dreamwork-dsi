package org.dreamwork.dsi.embedded.httpd.annotation;

import org.dreamwork.dsi.embedded.httpd.support.ParameterType;

import java.lang.annotation.*;

@Target ({ElementType.PARAMETER})
@Retention (RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface AFormItem {
    String value() default "";
    String name () default "";
    ParameterType type () default ParameterType.raw;
    String defaultValue () default "$$EMPTY$$";
}
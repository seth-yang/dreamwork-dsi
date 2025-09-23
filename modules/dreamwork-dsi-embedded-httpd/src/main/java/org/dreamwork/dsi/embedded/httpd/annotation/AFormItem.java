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

    /**
     * 指示这个参数是否可空
     * @return 是否可空
     * @since 2.1.2
     */
    boolean nullable () default true;
}
package org.dreamwork.dsi.embedded.httpd.annotation;

import org.dreamwork.dsi.embedded.httpd.support.ParameterType;

import java.lang.annotation.*;

@Target ({ElementType.PARAMETER})
@Retention (RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface AHeaderItem {
    // alias for name
    String value () default "";
    String name () default "";

    ParameterType type () default ParameterType.raw;

    /**
     * 指示这个参数是否允许为空
     * @since 2.1.2
     * @return 是否可空
     */
    boolean nullable () default true;
}

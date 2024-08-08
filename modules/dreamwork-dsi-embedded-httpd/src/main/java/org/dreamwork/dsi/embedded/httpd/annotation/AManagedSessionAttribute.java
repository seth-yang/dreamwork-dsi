package org.dreamwork.dsi.embedded.httpd.annotation;

import java.lang.annotation.*;

@Target ({ElementType.PARAMETER})
@Retention (RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface AManagedSessionAttribute {
    String value () default "";        // alias for name

    String name () default "";         // request 属性名称

    boolean nullable () default false;    // false
}
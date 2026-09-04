package org.dreamwork.dsi.embedded.httpd.annotation;

import java.lang.annotation.*;

/**
 * @since 3.0.0
 */
@Target ({ElementType.PARAMETER})
@Retention (RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface AUploadedFile {
    String value () default "";
    String name () default "";
    boolean nullable () default true;
}
package org.dreamwork.dsi.embedded.httpd.annotation;

import java.lang.annotation.*;

@Target ({ElementType.PARAMETER})
@Retention (RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface AInternal {
}
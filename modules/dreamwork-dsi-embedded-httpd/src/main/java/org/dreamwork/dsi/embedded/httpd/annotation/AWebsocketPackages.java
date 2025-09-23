package org.dreamwork.dsi.embedded.httpd.annotation;

import java.lang.annotation.*;

/**
 * 指示 {@link org.dreamwork.injection.ObjectContextFactory} 扫描 Websocket 实现的包名.
 * @since 2.1.2
 */
@Target ({ElementType.TYPE})
@Retention (RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface AWebsocketPackages {
    String[] value () default {};

    String[] packageNames () default {};
}
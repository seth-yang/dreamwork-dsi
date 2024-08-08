package org.dreamwork.dsi.embedded.httpd.annotation;

import java.lang.annotation.*;

/**
 * java web 控件的扫描注解
 * @since 1.1.0
 */
@Target ({ElementType.TYPE})
@Retention (RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface AWebPackages {
    String[] value () default {};   // alias for packageNames

    /**
     * 需要扫描的包名
     * @return 所有需要扫描的包名
     */
    String[] packageNames () default {};

    /**
     * 是否递归扫描指定的包下的所有子包
     * @return 如果支持递归扫描返回 true，否则 false
     */
    boolean recursive () default false;
}
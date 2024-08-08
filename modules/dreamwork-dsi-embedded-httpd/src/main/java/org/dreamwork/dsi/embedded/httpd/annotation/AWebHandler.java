package org.dreamwork.dsi.embedded.httpd.annotation;

import org.dreamwork.dsi.embedded.httpd.support.HandlerType;

import java.lang.annotation.*;

@Target ({ElementType.TYPE})
@Retention (RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface AWebHandler {
    String[] value () default {};
    String[] pattern () default {};

    HandlerType type () default HandlerType.API_HANDLER;

    /**
     * 返回结果是否被包裹.
     * <p>若需要包裹， {@code dis-embedded-httpd} 框架会自动将结果包裹成固定格式的 json，其结构如下：
     * <pre>
     * {
     *     "code": int,
     *     "message": "string",
     *     "result": object
     * }</pre>
     * 其中 {@code result} 即为 handler 方法的返回结果，可能为 {@code null}
     * <p>这个属性的优先级较 {@link AWebMapping#wrapped()} 低
     * @return 若需要 {@code dsi-embedded-httpd} 框架自动包裹成固定结构的json，则返回 true，否则返回 false
     * @since 1.1.0
     * @see AWebMapping#wrapped()
     */
    boolean wrapped () default false;
}

package org.dreamwork.dsi.dbcp.test;

import org.dreamwork.injection.AInjectionContext;

/**
 * 集成测试用的应用入口。
 *
 * <p>{@code org.dreamwork.dsi.dbcp.starter} 包由 {@code META-INF/dsi-dbcp-hook.properties}
 * 中声明的 {@code DBCPHook} 自动装配，因此这里不再通过 {@code scanPackages} 重复声明
 * （重复声明会导致同一个受托管对象被注册两次）。</p>
 */
@AInjectionContext (applicationName = "dbcp-unit-test")
public class DBCPApplication {
}

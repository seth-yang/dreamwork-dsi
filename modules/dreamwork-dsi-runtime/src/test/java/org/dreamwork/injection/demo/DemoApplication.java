package org.dreamwork.injection.demo;

import org.dreamwork.injection.AInjectionContext;

/**
 * 一个最小可运行的托管应用，用于端到端测试 {@code ObjectContextFactory}。
 *
 * <p>扫描器会自动扫描本包，因此同包下的 {@code DemoRepository} / {@code DemoService}
 * 会被自动装配到容器中。</p>
 */
@AInjectionContext (applicationName = "dreamwork-dsi-unit-test")
public class DemoApplication {
}

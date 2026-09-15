package org.dreamwork.injection.fixture;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.dreamwork.injection.AConfigured;
import org.dreamwork.injection.IInjectResolvedProcessor;
import org.dreamwork.injection.IObjectContext;

/**
 * 扫描器测试用的受托管对象夹具。
 *
 * <p>这个包专门提供给 {@code ObjectContextScanner} / {@code ClassScanner} 的测试使用，
 * 里面的每个内部类都代表一种被扫描的典型形态。</p>
 */
public final class FixtureSet {
    private FixtureSet () {}

    /** 一个普通的受托管对象。 */
    @Resource
    public static class Dependency {}

    /** 需要注入字段和配置，并带有后置处理方法的受托管对象。 */
    @Resource
    public static class SimpleComponent {
        @Resource
        private Dependency dependency;

        @AConfigured ("${fixture.title}")
        public String title;

        public boolean started;

        public Dependency dependency () {
            return dependency;
        }

        @PostConstruct
        public void start () {
            started = true;
        }
    }

    /** 只通过 {@code getter} 暴露的资源类型。 */
    public static class ExposedResource {
        public String name () {
            return "exposed-resource";
        }
    }

    /** 通过 {@code getter} 暴露额外资源的受托管对象。 */
    @Resource
    public static class ExposeComponent {
        @Resource (name = "exposed")
        public ExposedResource getExposed () {
            return new ExposedResource ();
        }
    }

    /** 容器注入完成后的处理器。 */
    @Resource
    public static class ProcessorComponent implements IInjectResolvedProcessor {
        public boolean performed;

        @Override
        public void perform (IObjectContext context) {
            performed = true;
        }
    }
}

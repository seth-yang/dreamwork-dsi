package org.dreamwork.dsi.embedded.httpd.support;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.dreamwork.config.IConfiguration;
import org.dreamwork.dsi.embedded.httpd.StubObjectContext;
import org.dreamwork.injection.AConfigured;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link WebComponentHelper} 的单元测试。
 * <p>覆盖类型解析（字段 / 方法 / 生命周期注解）与依赖注入流程。</p>
 */
class WebComponentHelperTest {
    // ------------------------------------------------------
    // 测试夹具
    // ------------------------------------------------------

    public interface Greeter {
        String greet ();
    }

    public static class GreeterImpl implements Greeter {
        @Override
        public String greet () {
            return "hi";
        }
    }

    public static class BaseComponent {
        @Resource
        protected Greeter inheritedGreeter;
    }

    public static class SampleComponent extends BaseComponent {
        @Resource
        private Greeter greeter;

        @AConfigured ("${sample.name}")
        public String name;

        boolean starterCalled, destroyerCalled;

        @PostConstruct
        public void start () {
            starterCalled = true;
        }

        @PreDestroy
        public void stop () {
            destroyerCalled = true;
        }
    }

    public static class BadComponent {
        @PostConstruct
        public void a () {
        }

        @PostConstruct
        public void b () {
        }
    }

    public static class Consumer {
        @Resource
        private Greeter greeter;

        boolean started;

        @PostConstruct
        public void init () {
            started = true;
        }
    }

    public static class ConfigHolder {
        @AConfigured ("${app.name}")
        public String appName;
    }

    @BeforeEach
    void setUp () {
        WebComponentHelper.setContext (new StubObjectContext ());
    }

    @AfterEach
    void tearDown () {
        WebComponentHelper.setContext (null);
    }

    // ------------------------------------------------------
    // parseType / findField / findMethods
    // ------------------------------------------------------

    @Test
    void parseTypeCollectsFieldsConfigAndLifecycle () {
        Cache c = WebComponentHelper.parseType (SampleComponent.class);

        assertEquals (2, c.fields.size ());
        assertTrue (c.fields.stream ().anyMatch (f -> f.getName ().equals ("greeter")));
        assertTrue (c.fields.stream ().anyMatch (f -> f.getName ().equals ("inheritedGreeter")));
        assertEquals (1, c.config.size ());
        assertNotNull (c.starter);
        assertEquals ("start", c.starter.getName ());
        assertNotNull (c.destroyer);
        assertEquals ("stop", c.destroyer.getName ());
        assertTrue (c.methods.isEmpty ());
    }

    @Test
    void parseTypeFindsFieldsFromSuperClass () {
        // 已由 SampleComponent 覆盖（inheritedGreeter 来自 BaseComponent），这里单独验证父类直查
        Cache c = WebComponentHelper.parseType (BaseComponent.class);
        assertEquals (1, c.fields.size ());
        assertEquals ("inheritedGreeter", c.fields.iterator ().next ().getName ());
    }

    @Test
    void parseTypeThrowsOnMultiplePostConstruct () {
        // parseType 会把 InstantiationException 包装成 RuntimeException
        assertThrows (RuntimeException.class, () -> WebComponentHelper.parseType (BadComponent.class));
    }

    // ------------------------------------------------------
    // inject
    // ------------------------------------------------------

    @Test
    void injectInjectsFieldsAndInvokesStarter () throws Exception {
        StubObjectContext context = new StubObjectContext ().add ("greeter", new GreeterImpl ());
        WebComponentHelper.setContext (context);

        Consumer consumer = new Consumer ();
        WebComponentHelper.inject (WebComponentHelper.parseType (Consumer.class), consumer);

        assertNotNull (consumer.greeter);
        assertEquals ("hi", consumer.greeter.greet ());
        assertTrue (consumer.started);
    }

    @Test
    void injectInjectsConfiguredValue () throws Exception {
        IConfiguration config = mock (IConfiguration.class);
        when (config.getString ("app.name")).thenReturn ("demo-app");
        WebComponentHelper.setContext (new StubObjectContext ().add ("config", config));

        ConfigHolder holder = new ConfigHolder ();
        WebComponentHelper.inject (WebComponentHelper.parseType (ConfigHolder.class), holder);

        assertEquals ("demo-app", holder.appName);
    }

    @Test
    void injectSkipsMissingOptionalConfig () throws Exception {
        IConfiguration config = mock (IConfiguration.class);
        when (config.getString ("app.name")).thenReturn (null);
        WebComponentHelper.setContext (new StubObjectContext ().add ("config", config));

        ConfigHolder holder = new ConfigHolder ();
        WebComponentHelper.inject (WebComponentHelper.parseType (ConfigHolder.class), holder);

        assertNull (holder.appName);
    }
}

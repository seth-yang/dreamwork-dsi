package org.dreamwork.injection.impl;

import org.dreamwork.config.IConfiguration;
import org.dreamwork.config.PropertyConfiguration;
import org.dreamwork.injection.fixture.FixtureSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.management.IntrospectionException;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ObjectContextScanner} 的单元测试：把 {@code org.dreamwork.injection.fixture} 包
 * 当作被测的扫描范围。
 */
class ObjectContextScannerTest {
    private static final String FIXTURE_PACKAGE = "org.dreamwork.injection.fixture";

    private SimpleObjectContext context;

    @BeforeEach
    void setUp () throws Exception {
        context = new SimpleObjectContext (1);

        Properties props = new Properties ();
        props.setProperty ("fixture.title", "fixture-title");
        context.register ("global-config", new PropertyConfiguration (props));
    }

    @AfterEach
    void tearDown () {
        context.dispose ();
    }

    @Test
    void scan_registersAnnotatedTypesAndInjectsThem () throws Exception {
        new ObjectContextScanner (context).scan (FIXTURE_PACKAGE);

        // 所有被 @Resource 标注的类都被注册，名称是首字母小写的简单类名
        assertNotNull (context.getBean ("dependency"));
        assertNotNull (context.getBean ("simpleComponent"));
        assertNotNull (context.getBean ("exposeComponent"));
        assertNotNull (context.getBean ("processorComponent"));
    }

    @Test
    void scan_injectsFieldAndConfigurationAndInvokesPostConstruct () throws Exception {
        new ObjectContextScanner (context).scan (FIXTURE_PACKAGE);

        FixtureSet.SimpleComponent component = context.getBean (FixtureSet.SimpleComponent.class);

        assertNotNull (component);
        assertSame (context.getBean (FixtureSet.Dependency.class), component.dependency ());
        assertEquals ("fixture-title", component.title);
        assertTrue (component.started);
    }

    @Test
    void scan_getterExposesReturnValueUnderAnnotatedName () throws Exception {
        new ObjectContextScanner (context).scan (FIXTURE_PACKAGE);

        Object exposed = context.getBean ("exposed");
        assertInstanceOf (FixtureSet.ExposedResource.class, exposed);
        // getter 的返回值同样会建立类型索引
        assertSame (exposed, context.getBean (FixtureSet.ExposedResource.class));
        assertEquals ("exposed-resource", ((FixtureSet.ExposedResource) exposed).name ());
    }

    @Test
    void scan_collectsInjectResolvedProcessors () throws Exception {
        new ObjectContextScanner (context).scan (FIXTURE_PACKAGE);

        FixtureSet.ProcessorComponent processor = context.getBean (FixtureSet.ProcessorComponent.class);
        assertNotNull (processor);
        assertFalse (processor.performed);

        context.resolve ();

        assertTrue (processor.performed);
    }

    @Test
    void scan_invalidSetterSignature_throwsIntrospectionException () {
        ObjectContextScanner scanner = new ObjectContextScanner (context);

        assertThrows (
                IntrospectionException.class,
                () -> scanner.scan ("org.dreamwork.injection.fixture.bad")
        );
    }
}

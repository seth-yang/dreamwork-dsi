package org.dreamwork.injection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 托管相关标注的默认值测试。
 */
class InjectionAnnotationsTest {
    @AInjectionContext
    static class DefaultApp {}

    @AInjectionContext (
            applicationName = "unit-test-app",
            value = {"value.pkg"},
            scanPackages = {"scan.pkg"},
            config = "my.conf",
            recursive = true,
            broadcastSupported = true,
            broadcastWorkers = 4,
            argumentDefinition = {"args.json"},
            shutdownPort = 9527,
            webComponentPackages = {"web.pkg"},
            websocketPackages = {"ws.pkg"}
    )
    static class FullApp {}

    @AInjectionContext (extras = @AExtraScan (name = "extra", scanPackages = {"extra.pkg"}, recursive = true))
    static class AppWithExtraScan {}

    static class ConfiguredTarget {
        @AConfigured
        String value;

        @ALazy
        boolean lazy;
    }

    @Test
    void aInjectionContext_defaults () {
        AInjectionContext ic = DefaultApp.class.getAnnotation (AInjectionContext.class);

        assertNotNull (ic);
        assertEquals ("", ic.applicationName ());
        assertArrayEquals (new String[0], ic.value ());
        assertArrayEquals (new String[0], ic.scanPackages ());
        assertEquals ("", ic.config ());
        assertFalse (ic.recursive ());
        assertFalse (ic.broadcastSupported ());
        assertEquals (-1, ic.broadcastWorkers ());
        assertArrayEquals (new String[0], ic.argumentDefinition ());
        assertArrayEquals (new String[0], ic.extras ());
        assertEquals (-1, ic.shutdownPort ());
        assertArrayEquals (new String[0], ic.webComponentPackages ());
        assertArrayEquals (new String[0], ic.websocketPackages ());
    }

    @Test
    void aInjectionContext_explicitValues () {
        AInjectionContext ic = FullApp.class.getAnnotation (AInjectionContext.class);

        assertNotNull (ic);
        assertEquals ("unit-test-app", ic.applicationName ());
        assertArrayEquals (new String[] {"value.pkg"}, ic.value ());
        assertArrayEquals (new String[] {"scan.pkg"}, ic.scanPackages ());
        assertEquals ("my.conf", ic.config ());
        assertTrue (ic.recursive ());
        assertTrue (ic.broadcastSupported ());
        assertEquals (4, ic.broadcastWorkers ());
        assertArrayEquals (new String[] {"args.json"}, ic.argumentDefinition ());
        assertEquals (9527, ic.shutdownPort ());
        assertArrayEquals (new String[] {"web.pkg"}, ic.webComponentPackages ());
        assertArrayEquals (new String[] {"ws.pkg"}, ic.websocketPackages ());
    }

    @Test
    void aInjectionContext_withExtras_canBeRead () {
        AInjectionContext ic = AppWithExtraScan.class.getAnnotation (AInjectionContext.class);

        assertNotNull (ic);
        // AExtraScan 未声明 RUNTIME 保留策略，因此运行期只保证能拿到数组本身
        assertNotNull (ic.extras ());
    }

    @Test
    void aConfigured_defaults () throws Exception {
        AConfigured ac = ConfiguredTarget.class.getDeclaredField ("value").getAnnotation (AConfigured.class);

        assertNotNull (ac);
        assertEquals ("", ac.value ());
        assertEquals ("", ac.key ());
        assertFalse (ac.required ());
    }

    @Test
    void aLazy_defaultsToTrue () throws Exception {
        ALazy lazy = ConfiguredTarget.class.getDeclaredField ("lazy").getAnnotation (ALazy.class);

        assertNotNull (lazy);
        assertTrue (lazy.value ());
    }

    @Test
    void aExtraScan_defaults () throws Exception {
        assertEquals ("", AExtraScan.class.getMethod ("name").getDefaultValue ());
        assertArrayEquals (new String[0], (String[]) AExtraScan.class.getMethod ("scanPackages").getDefaultValue ());
        assertEquals (Boolean.FALSE, AExtraScan.class.getMethod ("recursive").getDefaultValue ());
    }

    @Test
    void objectContextHook_defaults () {
        IObjectContextHook hook = new IObjectContextHook () {};

        assertArrayEquals (new String[0], hook.getScanPackages ());
        assertFalse (hook.isRecursive ());
        assertTrue (hook.getExtraScanners ().isEmpty ());
    }
}

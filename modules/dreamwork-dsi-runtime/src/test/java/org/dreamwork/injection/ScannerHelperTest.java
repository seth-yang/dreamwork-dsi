package org.dreamwork.injection;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ScannerHelper} 的单元测试。
 *
 * <p>递归扫描依赖 {@link ClassLoader#getResourceAsStream(String)} 返回的"包内容清单"，
 * 因此这里用一个假的类加载器来精确控制递归过程。</p>
 */
class ScannerHelperTest {
    @Test
    void fillPackageNames_recursivelyExpandsSubDirectoriesAndSkipsFiles () throws Exception {
        Map<String, String> resources = new HashMap<> ();
        resources.put ("com/example", "alpha\nbeta\nREADME.md\n");
        resources.put ("com/example/alpha", "inner\n");
        resources.put ("com/example/beta", null);

        FakeClassLoader loader = new FakeClassLoader (resources);
        Set<String> packages = new HashSet<> ();

        ScannerHelper.fillPackageNames (loader, "com.example", packages);

        assertEquals (
                Set.of ("com.example.alpha", "com.example.alpha.inner", "com.example.beta"),
                packages
        );
    }

    @Test
    void fillPackageNames_withUnknownBase_addsNothing () throws Exception {
        Set<String> packages = new HashSet<> ();

        ScannerHelper.fillPackageNames (new FakeClassLoader (Map.of ()), "com.unknown", packages);

        assertTrue (packages.isEmpty ());
    }

    @Test
    void fillPackageNames_fromAnnotation_addsDeclaringPackageAndScanPackages () throws Exception {
        AInjectionContext ic = ScanTarget.class.getAnnotation (AInjectionContext.class);

        Set<String> packages = new HashSet<> ();
        ScannerHelper.fillPackageNames (ScanTarget.class, ic, getClass ().getClassLoader (), packages);

        assertTrue (packages.contains ("org.dreamwork.injection"));
        assertTrue (packages.contains ("extra.pkg"));
        // recursive 默认为 false，因此不会向下展开
        assertEquals (2, packages.size ());
    }

    @Test
    void fillPackageNames_fromAnnotation_prefersValueOverScanPackages () throws Exception {
        AInjectionContext ic = ValueFirst.class.getAnnotation (AInjectionContext.class);

        Set<String> packages = new HashSet<> ();
        ScannerHelper.fillPackageNames (ValueFirst.class, ic, getClass ().getClassLoader (), packages);

        assertTrue (packages.contains ("value.pkg"));
        assertFalse (packages.contains ("ignored.pkg"));
    }

    @Test
    void fillPackageNames_withArray_alwaysRecurses () throws Exception {
        Map<String, String> resources = new HashMap<> ();
        resources.put ("com/example", "child\n");

        Set<String> packages = new HashSet<> ();
        ScannerHelper.fillPackageNames ("com.example", new String[] {"org.demo"}, new FakeClassLoader (resources), packages);

        assertEquals (Set.of ("com.example", "com.example.child", "org.demo"), packages);
    }

    @AInjectionContext (scanPackages = {"extra.pkg"})
    static class ScanTarget {}

    @AInjectionContext (value = {"value.pkg"}, scanPackages = {"ignored.pkg"})
    static class ValueFirst {}

    private static class FakeClassLoader extends ClassLoader {
        private final Map<String, String> resources;

        FakeClassLoader (Map<String, String> resources) {
            super (null);
            this.resources = new HashMap<> ();
            resources.forEach ((key, value) -> {
                if (value != null) {
                    this.resources.put (key, value);
                }
            });
        }

        @Override
        public InputStream getResourceAsStream (String name) {
            String content = resources.get (name);
            return content == null ? null : new ByteArrayInputStream (content.getBytes (StandardCharsets.UTF_8));
        }
    }
}

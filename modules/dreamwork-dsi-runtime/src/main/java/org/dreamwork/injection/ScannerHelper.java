package org.dreamwork.injection;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * 将类/包扫描的工具函数分离出来
 *
 * @since 1.1.0
 */
public class ScannerHelper {
    public static void fillPackageNames (ClassLoader loader, String base, Set<String> list) throws IOException {
        String path = base.replace ('.', '/');
        try (InputStream in = loader.getResourceAsStream (path)) {
            if (in != null) {
                BufferedReader reader = new BufferedReader (new InputStreamReader (in));
                String line;
                while ((line = reader.readLine ()) != null) {
                    if (!line.contains (".")) {
                        list.add (base + "." + line);

                        fillPackageNames (loader, base + "." + line, list);
                    }
                }
            }
        }
    }

    public static void fillPackageNames (Class<?> type, AInjectionContext ic, ClassLoader loader, Set<String> packages) throws IOException {
        String base = type.getPackage ().getName ();
        packages.add (base);
        if (ic.recursive ()) {
            fillPackageNames (loader, base, packages);
        }

        String[] array = ic.value ();
        if (array.length == 0) {
            array = ic.scanPackages ();
        }

        for (String packageName : array) {
            packages.add (packageName);
            if (ic.recursive ()) {
                fillPackageNames (loader, packageName, packages);
            }
        }
    }

    public static void fillPackageNames (String base, String[] array, ClassLoader loader, Set<String> packages) throws IOException {
        packages.add (base);
        fillPackageNames (loader, base, packages);
        for (String packageName : array) {
            packages.add (packageName);
            fillPackageNames (loader, packageName, packages);
        }
    }

    public static String createAddModuleInfoMessage (AccessibleObject ao) {
        final String pattern = "please add \"opens %s to %s\" to your module-info.java";
        String packageName = "";
        if (ao instanceof Method method) {
            packageName = method.getDeclaringClass ().getPackageName ();
        } else if (ao instanceof Field field) {
            packageName = field.getDeclaringClass ().getPackageName ();
        } else {
            packageName = "<your-package-name>";
        }
        return String.format (pattern, packageName, ScannerHelper.class.getModule ().getName ());
    }
}
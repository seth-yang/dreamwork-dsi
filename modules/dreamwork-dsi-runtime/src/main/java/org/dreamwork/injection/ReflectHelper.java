package org.dreamwork.injection;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ReflectHelper {
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
        return String.format (pattern, packageName, ReflectHelper.class.getModule ().getName ());
    }

    public static void checkAccessible (AccessibleObject ao, Object instance) {
        if (ao == null || instance == null) return;
        if (!ao.canAccess (instance)) {
            try {
                ao.setAccessible (true);
            } catch (SecurityException e) {
                throw new RuntimeException (createAddModuleInfoMessage (ao));
            }
        }
    }
}

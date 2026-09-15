package org.dreamwork.injection;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ReflectHelper} 的单元测试。
 */
class ReflectHelperTest {
    public String sampleField;

    public void sampleMethod () {}

    @Test
    void createAddModuleInfoMessage_usesDeclaringPackageOfMethod () throws Exception {
        Method method = ReflectHelperTest.class.getDeclaredMethod ("sampleMethod");

        String message = ReflectHelper.createAddModuleInfoMessage (method);

        assertTrue (message.startsWith ("please add \"opens org.dreamwork.injection to "));
    }

    @Test
    void createAddModuleInfoMessage_usesDeclaringPackageOfField () throws Exception {
        Field field = ReflectHelperTest.class.getDeclaredField ("sampleField");

        String message = ReflectHelper.createAddModuleInfoMessage (field);

        assertTrue (message.startsWith ("please add \"opens org.dreamwork.injection to "));
    }

    @Test
    void createAddModuleInfoMessage_forOtherAccessibleObject_usesPlaceholder () throws Exception {
        Constructor<ReflectHelperTest> constructor = ReflectHelperTest.class.getDeclaredConstructor ();

        String message = ReflectHelper.createAddModuleInfoMessage (constructor);

        assertTrue (message.startsWith ("please add \"opens <your-package-name> to "));
    }

    @Test
    void checkAccessible_ignoresNullArguments () {
        assertDoesNotThrow (() -> ReflectHelper.checkAccessible (null, null));
        assertDoesNotThrow (() -> ReflectHelper.checkAccessible (null, new Object ()));
        assertDoesNotThrow (() -> ReflectHelper.checkAccessible (ReflectHelperTest.class.getDeclaredConstructor (), null));
    }
}

package org.dreamwork.injection.impl;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link EnhancedConfiguration} 的单元测试：验证 {@code ${...}} 占位符替换。
 */
class EnhancedConfigurationTest {

    @Test
    void resolvesPlaceholderFromAnotherKey () {
        Properties props = new Properties ();
        props.setProperty ("app.home", "/opt/app");
        props.setProperty ("app.log", "${app.home}/logs");

        EnhancedConfiguration conf = new EnhancedConfiguration (props);

        assertEquals ("/opt/app/logs", conf.getString ("app.log"));
    }

    @Test
    void resolvesMultiplePlaceholdersInSingleValue () {
        Properties props = new Properties ();
        props.setProperty ("a", "1");
        props.setProperty ("b", "2");
        props.setProperty ("c", "${a}-${b}-${a}");

        EnhancedConfiguration conf = new EnhancedConfiguration (props);

        assertEquals ("1-2-1", conf.getString ("c"));
        // 显式传入空参数以覆盖 Object... 重载
        assertEquals ("1-2-1", conf.getString ("c", new Object[0]));
    }

    @Test
    void leavesPlainValueUntouched () {
        Properties props = new Properties ();
        props.setProperty ("plain", "no-placeholder-here");

        EnhancedConfiguration conf = new EnhancedConfiguration (props);

        assertEquals ("no-placeholder-here", conf.getString ("plain"));
    }

    @Test
    void unknownKeyReturnsNull () {
        EnhancedConfiguration conf = new EnhancedConfiguration (new Properties ());

        assertNull (conf.getString ("missing"));
    }

    @Test
    void unresolvedPlaceholderIsKeptAsIs () {
        Properties props = new Properties ();
        props.setProperty ("value", "${missing}/logs");

        EnhancedConfiguration conf = new EnhancedConfiguration (props);

        // 无法解析的占位符保持原样
        assertEquals ("${missing}/logs", conf.getString ("value"));
    }

    @Test
    void resolvableAndUnresolvablePlaceholderCanBeMixed () {
        Properties props = new Properties ();
        props.setProperty ("root", "/var/data");
        props.setProperty ("value", "${root}/${missing}");

        EnhancedConfiguration conf = new EnhancedConfiguration (props);

        assertEquals ("/var/data/${missing}", conf.getString ("value"));
    }

    @Test
    void numericAndBooleanAccessorsReadRawValues () {
        Properties props = new Properties ();
        props.setProperty ("port", "8080");
        props.setProperty ("port-ref", "${port}");
        props.setProperty ("enabled", "true");

        EnhancedConfiguration conf = new EnhancedConfiguration (props);

        assertEquals (8080, conf.getInt ("port", -1));
        assertTrue (conf.getBoolean ("enabled", false));
        assertFalse (conf.getBoolean ("unknown-flag", false));
        // 数值读取不会经过占位符解析，无法解析时返回默认值
        assertEquals (-1, conf.getInt ("port-ref", -1));
    }
}

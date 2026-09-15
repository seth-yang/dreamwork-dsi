package org.dreamwork.dsi.dbcp.test;

import org.dreamwork.dsi.dbcp.starter.DBCPConfiguration;
import org.dreamwork.dsi.dbcp.starter.DBCPConfigurationTest;
import org.dreamwork.injection.IObjectContext;
import org.dreamwork.injection.ObjectContextFactory;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 端到端测试：由托管容器加载配置并自动创建数据库连接池。
 */
class DBCPApplicationTest {

    @Test
    void contextCreatesAndRegistersThePool () throws Throwable {
        Path config = prepareConfigFile ();
        Map<String, String> settings = DBCPConfigurationTest.h2Settings ("dsi-integration");
        Files.writeString (config, settingsText (settings), StandardCharsets.UTF_8);

        IObjectContext context = ObjectContextFactory.start (
                DBCPApplication.class,
                "-c", config.toString (),
                "without-logs"
        );

        try {
            // DBCPConfiguration 由扫描器自动注册，并在 @PostConstruct 中创建连接池
            assertNotNull (context.getBean (DBCPConfiguration.class));

            Object registered = context.getBean ("dsi-integration");
            assertInstanceOf (DataSource.class, registered);
            // 连接池确实可用
            assertTrue (DBCPConfigurationTest.canQuery ((DataSource) registered));
        } finally {
            context.dispose ();
        }
    }

    @Test
    void withoutAnyPoolSettings_noPoolIsRegistered () throws Throwable {
        Path config = prepareConfigFile ();
        Files.writeString (config, "demo.title=nothing-here\n", StandardCharsets.UTF_8);

        IObjectContext context = ObjectContextFactory.start (
                DBCPApplication.class,
                "-c", config.toString (),
                "without-logs"
        );

        try {
            assertNotNull (context.getBean (DBCPConfiguration.class));
            assertFalse (context.getAllBeanNames ().contains ("dsi-integration"));
        } finally {
            context.dispose ();
        }
    }

    private String settingsText (Map<String, String> settings) {
        StringBuilder builder = new StringBuilder ();
        settings.forEach ((key, value) -> builder.append (key).append ('=').append (value).append ('\n'));
        return builder.toString ();
    }

    private Path prepareConfigFile () throws Exception {
        Path dir = Path.of ("target", "test-config");
        Files.createDirectories (dir);
        return dir.resolve ("object-context.conf");
    }
}

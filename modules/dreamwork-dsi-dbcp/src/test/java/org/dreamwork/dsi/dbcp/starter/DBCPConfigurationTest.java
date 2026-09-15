package org.dreamwork.dsi.dbcp.starter;

import org.dreamwork.config.PropertyConfiguration;
import org.dreamwork.injection.IObjectContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.SQLException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link DBCPConfiguration} 的单元测试。
 *
 * <p>使用内存数据库 H2 来验证真正的连接池创建过程，使用动态代理来观察注册到容器内的对象。</p>
 */
public class DBCPConfigurationTest {

    @Test
    void withoutAnyPoolSettings_doesNothing () throws Exception {
        DBCPConfiguration configuration = new DBCPConfiguration ();
        set (configuration, "conf", new PropertyConfiguration (new Properties ()));

        assertDoesNotThrow (configuration::createDatabaseConnectionPool);
    }

    @Test
    void whenExtraConfigFileDoesNotExist_doesNothing () throws Exception {
        DBCPConfiguration configuration = new DBCPConfiguration ();
        set (configuration, "extraConfig", Path.of ("target", "no-such-file.properties").toString ());

        assertDoesNotThrow (configuration::createDatabaseConnectionPool);
    }

    @Test
    void invalidDriver_poolIsRegisteredButCannotOpenConnection (@TempDir Path tempDir) throws Exception {
        Map<String, String> settings = new LinkedHashMap<> ();
        settings.put ("dbcp.broken.driver", "no.such.jdbc.Driver");
        settings.put ("dbcp.broken.url", "jdbc:no-such://127.0.0.1:1/none");

        Map<String, Object> registered = new LinkedHashMap<> ();
        DBCPConfiguration configuration = new DBCPConfiguration ();
        set (configuration, "extraConfig", writeSettings (tempDir, "dbcp.properties", settings).toString ());
        set (configuration, "context", recordingContext (registered));

        // 驱动类是延迟加载的，构建连接池本身不会失败
        configuration.createDatabaseConnectionPool ();

        Object dataSource = registered.get ("broken");
        assertInstanceOf (DataSource.class, dataSource);
        assertThrows (SQLException.class, () -> ((DataSource) dataSource).getConnection ());
    }

    @Test
    void extraConfigFile_createsAndRegistersPool (@TempDir Path tempDir) throws Exception {
        Path settings = writeSettings (tempDir, "dbcp.properties", h2Settings ("h2-extra"));

        Map<String, Object> registered = new LinkedHashMap<> ();
        DBCPConfiguration configuration = new DBCPConfiguration ();
        set (configuration, "extraConfig", settings.toString ());
        set (configuration, "context", recordingContext (registered));

        configuration.createDatabaseConnectionPool ();

        assertEquals (1, registered.size ());
        Object dataSource = registered.get ("h2-extra");
        assertInstanceOf (DataSource.class, dataSource);
        assertTrue (canQuery ((DataSource) dataSource));
    }

    public static boolean canQuery (DataSource dataSource) throws Exception {
        try (var connection = dataSource.getConnection (); var statement = connection.createStatement ()) {
            statement.execute ("create table if not exists probe(id int primary key)");
            statement.execute ("delete from probe");
            statement.execute ("insert into probe(id) values (1)");
            try (var rs = statement.executeQuery ("select count(*) from probe")) {
                return rs.next () && rs.getInt (1) == 1;
            }
        }
    }

    public static Map<String, String> h2Settings (String poolName) {
        Map<String, String> settings = new LinkedHashMap<> ();
        settings.put ("dbcp." + poolName + ".driver", "org.h2.Driver");
        settings.put ("dbcp." + poolName + ".url", "jdbc:h2:mem:" + poolName + ";DB_CLOSE_DELAY=-1");
        settings.put ("dbcp." + poolName + ".user", "sa");
        settings.put ("dbcp." + poolName + ".password", "");
        settings.put ("dbcp." + poolName + ".max.total", "4");
        settings.put ("dbcp." + poolName + ".max.idle", "2");
        settings.put ("dbcp." + poolName + ".max.wait", "3000");
        settings.put ("dbcp." + poolName + ".remove.timeout", "30");
        settings.put ("dbcp." + poolName + ".log.abandoned", "false");
        return settings;
    }

    static void set (Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass ().getDeclaredField (fieldName);
        field.setAccessible (true);
        field.set (target, value);
    }

    static Path writeSettings (Path dir, String fileName, Map<String, String> settings) throws IOException {
        Properties props = new Properties ();
        settings.forEach (props::setProperty);

        Path file = dir.resolve (fileName);
        try (var out = Files.newOutputStream (file)) {
            props.store (out, "dreamwork-dsi unit test");
        }
        return file;
    }

    /**
     * 生成一个只记录 {@code register(String, Object)} 调用的容器代理。
     */
    static IObjectContext recordingContext (Map<String, Object> registered) {
        return (IObjectContext) Proxy.newProxyInstance (
                DBCPConfigurationTest.class.getClassLoader (),
                new Class<?>[] {IObjectContext.class},
                (proxy, method, args) -> {
                    if ("register".equals (method.getName ()) && args != null && args.length == 2) {
                        registered.put ((String) args[0], args[1]);
                    }
                    return null;
                }
        );
    }
}

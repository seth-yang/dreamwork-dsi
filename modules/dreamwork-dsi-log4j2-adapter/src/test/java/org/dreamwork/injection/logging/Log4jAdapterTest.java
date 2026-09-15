package org.dreamwork.injection.logging;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.dreamwork.config.PropertyConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link Log4jAdapter} 的单元测试。
 *
 * <p>适配器会把配置文件写到 {@code ../.dreamwork-dsi-logging-service/log4j2.xml}，
 * 测试结束后会清理掉这个临时产物。</p>
 *
 * <p>日志文件不放在 {@code @TempDir} 下：log4j2 的 RollingFile appender 会一直持有
 * 文件句柄，Windows 上会导致临时目录无法删除。这里统一写在 {@code target/} 下。</p>
 */
class Log4jAdapterTest {
    /** 与 Log4jAdapter 中生成配置文件的路径保持一致。 */
    private static final Path SERVICE_DIR = Path.of ("..", ".dreamwork-dsi-logging-service");

    @AfterEach
    void cleanUp () throws IOException {
        Files.deleteIfExists (SERVICE_DIR.resolve ("log4j2.xml"));
        if (Files.isDirectory (SERVICE_DIR)) {
            try (Stream<Path> stream = Files.list (SERVICE_DIR)) {
                if (stream.findAny ().isEmpty ()) {
                    Files.deleteIfExists (SERVICE_DIR);
                }
            }
        }
    }

    @Test
    void init_generatesAndAppliesLog4j2Configuration () throws Exception {
        Path logDir = createLogDir ("applied");

        Properties props = new Properties ();
        props.setProperty ("logger.pattern", "%d [%p] %c - %m%n");
        props.setProperty ("logger.pattern.colored", "%d %highlight{[%p]} - %m%n");
        props.setProperty ("logger.level.org.dreamwork", "trace");
        props.setProperty ("logger.level.com.example", "warn");
        props.setProperty ("unrelated.key", "ignored");

        new Log4jAdapter ().init (
                getClass ().getClassLoader (),
                "info",
                logDir.resolve ("my-app.log").toString (),
                null,
                new PropertyConfiguration (props)
        );

        Path xml = SERVICE_DIR.resolve ("log4j2.xml");
        assertTrue (Files.isRegularFile (xml), "log4j2.xml should be generated at " + xml.toAbsolutePath ());

        String content = Files.readString (xml, StandardCharsets.UTF_8);
        // home 取自日志文件的父目录
        assertTrue (content.contains (logDir.getFileName ().toString ()), content);
        // 应用名取自日志文件名（去掉扩展名）
        assertTrue (content.contains ("<Property name=\"APP_NAME\">my-app</Property>"), content);
        assertTrue (content.contains ("<Root level=\"info\">"), content);
        assertTrue (content.contains ("%d [%p] %c - %m%n"), content);
        assertTrue (content.contains ("%d %highlight{[%p]} - %m%n"), content);
        assertTrue (content.contains ("<Logger name=\"org.dreamwork\" level=\"trace\" additivity=\"false\">"), content);
        assertTrue (content.contains ("<Logger name=\"com.example\" level=\"warn\" additivity=\"false\">"), content);
        assertFalse (content.contains ("unrelated"), content);

        // 生成的配置已经被 log4j2 真正加载
        LoggerContext ctx = (LoggerContext) LogManager.getContext (getClass ().getClassLoader (), false);
        assertNotNull (ctx);
        assertNotNull (ctx.getConfiguration ());
        assertTrue (ctx.getConfiguration ().getName ().contains ("log4j2.xml"), ctx.getConfiguration ().getName ());
    }

    @Test
    void init_withoutOptionalProperties_usesDefaults () throws Exception {
        Path logDir = createLogDir ("defaults");

        new Log4jAdapter ().init (
                getClass ().getClassLoader (),
                "debug",
                logDir.resolve ("plain.log").toString (),
                null,
                new PropertyConfiguration (new Properties ())
        );

        String content = Files.readString (SERVICE_DIR.resolve ("log4j2.xml"), StandardCharsets.UTF_8);
        assertTrue (content.contains ("<Property name=\"APP_NAME\">plain</Property>"), content);
        assertTrue (content.contains ("<Root level=\"debug\">"), content);
        // 未提供 logger.level.* 时不会生成额外的 Logger 节点
        assertFalse (content.contains ("<Logger name="), content);
    }

    @Test
    void init_removesPreviouslyGeneratedFile () throws Exception {
        Path logDir = createLogDir ("stale");
        Files.createDirectories (SERVICE_DIR);
        Path xml = SERVICE_DIR.resolve ("log4j2.xml");
        Files.writeString (xml, "<stale/>", StandardCharsets.UTF_8);

        new Log4jAdapter ().init (
                getClass ().getClassLoader (),
                "info",
                logDir.resolve ("fresh.log").toString (),
                null,
                new PropertyConfiguration (new Properties ())
        );

        String content = Files.readString (xml, StandardCharsets.UTF_8);
        assertFalse (content.contains ("<stale/>"), content);
        assertTrue (content.startsWith ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"), content);
    }

    private Path createLogDir (String name) throws IOException {
        Path dir = Path.of ("target", "log4j-adapter-test", name);
        Files.createDirectories (dir);
        return dir;
    }
}

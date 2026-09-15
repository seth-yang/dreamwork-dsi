package org.dreamwork.injection.logging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link CustomConfigurationBuilder} 的单元测试：验证生成的 log4j2 配置内容与格式。
 */
class CustomConfigurationBuilderTest {

    @Test
    void write_usesDefaultValues (@TempDir Path tempDir) throws Exception {
        Path xml = tempDir.resolve ("log4j2.xml");

        new CustomConfigurationBuilder (xml.toString (), false).write ();

        String content = read (xml);
        assertTrue (content.contains ("<Configuration status=\"warn\" monitorInterval=\"0\">"), content);
        assertTrue (content.contains ("<Property name=\"LOG_HOME\">../logs</Property>"), content);
        assertTrue (content.contains ("<Property name=\"APP_NAME\">dreamwork-dsi</Property>"), content);
        assertTrue (content.contains ("[%d{yyyy-MM-dd HH:mm:ss.SSS}][%-5p][%t][%C:%L] - %m%n"), content);
        assertTrue (content.contains ("[%d{yyyy-MM-dd HH:mm:ss.SSS}]%highlight{[%-5p]}[%t][%C:%L] - %m%n"), content);
        assertTrue (content.contains ("<Root level=\"info\">"), content);
    }

    @Test
    void write_appendsConsoleAndRollingFileAppenders (@TempDir Path tempDir) throws Exception {
        Path xml = tempDir.resolve ("log4j2.xml");

        new CustomConfigurationBuilder (xml.toString (), false).write ();

        String content = read (xml);
        assertTrue (content.contains ("<Console name=\"Console\" target=\"SYSTEM_OUT\">"), content);
        assertTrue (content.contains ("<RollingFile name=\"RollingFileAll\""), content);
        assertTrue (content.contains ("fileName=\"${LOG_HOME}/${APP_NAME}.log\""), content);
        assertTrue (content.contains ("<AppenderRef ref=\"Console\"/>"), content);
        assertTrue (content.contains ("<AppenderRef ref=\"RollingFileAll\"/>"), content);
        assertTrue (content.endsWith ("</Configuration>"), content);
    }

    @Test
    void write_usesCustomValuesAndLoggerLevels (@TempDir Path tempDir) throws Exception {
        Path xml = tempDir.resolve ("log4j2.xml");

        new CustomConfigurationBuilder (xml.toString (), true)
                .home ("/var/log/my-app")
                .name ("my-app")
                .level ("debug")
                .pattern ("%d [%p] %c - %m%n")
                .patternColored ("%d %highlight{[%p]} - %m%n")
                .appendLoggerLevel ("org.dreamwork", "trace")
                .appendLoggerLevel ("com.example", "warn")
                .write ();

        String content = read (xml);
        assertTrue (content.contains ("<Configuration status=\"debug\" monitorInterval=\"30\">"), content);
        assertTrue (content.contains ("<Property name=\"LOG_HOME\">/var/log/my-app</Property>"), content);
        assertTrue (content.contains ("<Property name=\"APP_NAME\">my-app</Property>"), content);
        assertTrue (content.contains ("%d [%p] %c - %m%n"), content);
        assertTrue (content.contains ("%d %highlight{[%p]} - %m%n"), content);
        assertTrue (content.contains ("<Root level=\"debug\">"), content);
        assertTrue (content.contains ("<Logger name=\"org.dreamwork\" level=\"trace\" additivity=\"false\">"), content);
        assertTrue (content.contains ("<Logger name=\"com.example\" level=\"warn\" additivity=\"false\">"), content);
    }

    @Test
    void write_createsMissingParentDirectories (@TempDir Path tempDir) throws Exception {
        Path xml = tempDir.resolve ("nested").resolve ("deeper").resolve ("log4j2.xml");
        assertFalse (Files.exists (xml.getParent ()));

        new CustomConfigurationBuilder (xml.toString (), false).write ();

        assertTrue (Files.isRegularFile (xml));
    }

    @Test
    void write_isWellFormedXml (@TempDir Path tempDir) throws Exception {
        Path xml = tempDir.resolve ("log4j2.xml");

        new CustomConfigurationBuilder (xml.toString (), true)
                .home ("/var/log/app")
                .name ("app")
                .appendLoggerLevel ("com.example", "info")
                .write ();

        Document document = parse (xml);
        assertEquals ("Configuration", document.getDocumentElement ().getTagName ());
    }

    @Test
    void write_rewritesFileKeepingAccumulatedSettings (@TempDir Path tempDir) throws Exception {
        Path xml = tempDir.resolve ("log4j2.xml");

        CustomConfigurationBuilder builder = new CustomConfigurationBuilder (xml.toString (), false);
        builder.appendLoggerLevel ("first", "info").write ();
        assertTrue (read (xml).contains ("name=\"first\""));

        builder.appendLoggerLevel ("second", "warn").write ();
        String content = read (xml);
        assertTrue (content.contains ("name=\"first\""), content);
        assertTrue (content.contains ("name=\"second\""), content);
    }

    @Test
    void builderMethods_areChainable (@TempDir Path tempDir) {
        CustomConfigurationBuilder builder = new CustomConfigurationBuilder (tempDir.resolve ("x.xml").toString (), false);

        assertSame (builder, builder.home ("h"));
        assertSame (builder, builder.name ("n"));
        assertSame (builder, builder.level ("l"));
        assertSame (builder, builder.pattern ("p"));
        assertSame (builder, builder.patternColored ("pc"));
        assertSame (builder, builder.appendLoggerLevel ("a", "b"));
    }

    private String read (Path file) throws IOException {
        return Files.readString (file, StandardCharsets.UTF_8);
    }

    private Document parse (Path file) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance ();
        factory.setValidating (false);
        factory.setNamespaceAware (false);
        // 关闭外部实体解析，避免测试环境依赖网络
        factory.setFeature ("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        return factory.newDocumentBuilder ().parse (file.toFile ());
    }
}

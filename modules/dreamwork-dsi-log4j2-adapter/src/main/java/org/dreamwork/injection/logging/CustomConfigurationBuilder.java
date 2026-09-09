package org.dreamwork.injection.logging;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class CustomConfigurationBuilder {
    public static final String LOG_HOME = "LOG_HOME";
    public static final String LOG_NAME = "LOG_NAME";
    public static final String LOG_PATTERN = "LOG_PATTERN";
    public static final String LOG_LEVEL   = "LOG_LEVEL";
    public static final String LOG_PATTERN_COLORED = "LOG_PATTERN_COLORED";

//    private static final String DEFAULT_PATTERN = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n";
    private static final String DEFAULT_PATTERN = "[%d{yyyy-MM-dd HH:mm:ss.SSS}][%-5p][%t][%C:%L] - %m%n";
//    private static final String DEFAULT_PATTERN_COLORED = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %highlight{%-5level} %logger{36} - %msg%n";
    private static final String DEFAULT_PATTERN_COLORED = "[%d{yyyy-MM-dd HH:mm:ss.SSS}]%highlight{[%-5p]}[%t][%C:%L] - %m%n";

    private final String filename;
    private final boolean debug;

    private final Map<String, String> props = new HashMap<> ();
    private final Map<String, String> levels = new HashMap<> ();

    public CustomConfigurationBuilder (String filename, boolean debug) {
        this.filename = filename;
        this.debug    = debug;
    }

    public CustomConfigurationBuilder pattern (String pattern) {
        props.put (LOG_PATTERN, pattern);
        return this;
    }

    public CustomConfigurationBuilder patternColored (String pattern) {
        props.put (LOG_PATTERN_COLORED, pattern);
        return this;
    }

    public CustomConfigurationBuilder home (String dir) {
        props.put (LOG_HOME, dir);
        return this;
    }

    public CustomConfigurationBuilder name (String name) {
        props.put (LOG_NAME, name);
        return this;
    }

    public CustomConfigurationBuilder level (String level) {
        props.put (LOG_LEVEL, level);
        return this;
    }

    public CustomConfigurationBuilder appendLoggerLevel (String name, String level) {
        levels.put (name, level);
        return this;
    }

    public void write () throws IOException {
        var path = Paths.get (filename);
        var parent = path.getParent ();
        if (Files.notExists (parent)) {
            Files.createDirectories (parent);
        }

        try (Writer writer = Files.newBufferedWriter (path, StandardCharsets.UTF_8)) {
            PrintWriter pw = new PrintWriter (writer, true);
            pw.println ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            pw.printf (
                    "<Configuration status=\"%s\" monitorInterval=\"%d\">%n",
                    debug ? "debug" : "warn",
                    debug ? 30 : 0
            );

            writeProperties (pw);
            writeAppend (pw);
            writeLevels (pw);

            pw.print ("</Configuration>");
        }
    }

    private void writeProperties (PrintWriter pw) {
        pw.println ("<Properties>");

        pw.printf ("<Property name=\"LOG_HOME\">%s</Property>", props.getOrDefault (LOG_HOME, "../logs"));
        pw.printf ("<Property name=\"APP_NAME\">%s</Property>", props.getOrDefault (LOG_NAME, "dreamwork-dsi"));

        pw.println ("<Property name=\"LOG_PATTERN\">");
        pw.println (props.getOrDefault (LOG_PATTERN, DEFAULT_PATTERN));
        pw.println ("</Property>");

        pw.println ("<Property name=\"LOG_PATTERN_COLORED\">");
        pw.println (props.getOrDefault (LOG_PATTERN_COLORED, DEFAULT_PATTERN_COLORED));
        pw.println ("</Property>");

        pw.println ("</Properties>");
    }

    private void writeAppend (PrintWriter pw) {
        pw.println ("<Appenders>");
        pw.println ("""
                <Console name="Console" target="SYSTEM_OUT">
                    <PatternLayout pattern="${LOG_PATTERN_COLORED}"/>
                </Console>
                """);
        pw.println ("""
        <RollingFile name="RollingFileAll"
                     fileName="${LOG_HOME}/${APP_NAME}.log"
                     filePattern="${LOG_HOME}/${APP_NAME}-%d{yyyy-MM-dd}-%i.log">
            <PatternLayout pattern="${LOG_PATTERN}"/>

            <Policies>
                <TimeBasedTriggeringPolicy interval="1" modulate="true"/>
                <SizeBasedTriggeringPolicy size="100MB"/>
            </Policies>

            <!-- ✅ 正确位置 -->
            <DefaultRolloverStrategy max="7">
                <Delete basePath="${LOG_HOME}" maxDepth="1">
                    <IfAccumulatedFileSize exceeds="700MB"/>
                </Delete>
            </DefaultRolloverStrategy>
        </RollingFile>
        """);
        pw.println ("</Appenders>");
    }

    private void writeLevels (PrintWriter pw) {
        pw.println ("<Loggers>");
        for (var e : levels.entrySet ()) {
            pw.printf ("""
            <Logger name="%s" level="%s" additivity="false">
                <AppenderRef ref="Console"/>
                <AppenderRef ref="RollingFileAll"/>
            </Logger>%n
            """, e.getKey (), e.getValue ());
        }

        pw.printf ("""
        <Root level="%s">
            <AppenderRef ref="Console"/>
            <AppenderRef ref="RollingFileAll"/>
        </Root>
        """, props.getOrDefault (LOG_LEVEL, "info"));
        pw.println ("</Loggers>");
    }
}
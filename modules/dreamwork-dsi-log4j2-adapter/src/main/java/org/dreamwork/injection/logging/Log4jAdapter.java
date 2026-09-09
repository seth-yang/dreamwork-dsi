package org.dreamwork.injection.logging;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.dreamwork.cli.ArgumentParser;
import org.dreamwork.config.PropertyConfiguration;
import org.dreamwork.injection.ILoggingService;
import org.dreamwork.util.FileInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Log4jAdapter implements ILoggingService {
    @Override
    public void init (ClassLoader loader, String logLevel, String logFile, ArgumentParser parser, PropertyConfiguration conf) throws IOException {
        // logFile 在进来前，已经经过处理了，其 parent 一定存在
        var path = Paths.get (logFile);
        var parent = path.getParent ().toRealPath ().toAbsolutePath ().toString ();

        var tmp = Paths.get ("..", ".dreamwork-dsi-logging-service");
        if (Files.notExists (tmp)) {
            Files.createDirectories (tmp);
        }
        var xml = Paths.get (tmp.toString (), "log4j2.xml");
        if (Files.exists (xml)) {
            Files.delete (xml);
        }
        final var home = xml.toAbsolutePath ().toString ();

        String appName = FileInfo.getFileNameWithoutPath (logFile);
        appName = FileInfo.getFileNameWithoutExtension (appName);

        CustomConfigurationBuilder builder = new CustomConfigurationBuilder (home, false);
        builder.home (parent).level (logLevel).name (appName);

        if (conf.contains ("logger.pattern")) {
            builder.pattern (conf.getString ("logger.pattern"));
        }
        if (conf.contains ("logger.pattern.colored")) {
            builder.patternColored (conf.getString ("logger.pattern.colored"));
        }

        var props = conf.getRawProperties ();
        final int prefixLength = "logger.level.".length ();
        for (var key : props.stringPropertyNames ()) {
            if (key.startsWith ("logger.level.")) {
                builder.appendLoggerLevel (key.substring (prefixLength), props.getProperty (key));
            }
        }

        builder.write ();

        LoggerContext ctx = (LoggerContext) LogManager.getContext (loader, false);
        ctx.setConfigLocation (xml.toUri ());
        ctx.reconfigure ();
    }
}
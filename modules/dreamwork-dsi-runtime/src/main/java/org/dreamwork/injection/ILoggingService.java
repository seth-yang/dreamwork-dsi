package org.dreamwork.injection;

import org.dreamwork.cli.ArgumentParser;
import org.dreamwork.config.PropertyConfiguration;

public interface ILoggingService {
    void init (ClassLoader loader,
               String logLevel,
               String logFile,
               ArgumentParser parser,
               PropertyConfiguration conf) throws Exception;
}
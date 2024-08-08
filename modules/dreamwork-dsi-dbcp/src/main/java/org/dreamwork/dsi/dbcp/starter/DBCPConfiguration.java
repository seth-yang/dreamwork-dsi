package org.dreamwork.dsi.dbcp.starter;

import org.apache.tomcat.dbcp.dbcp2.BasicDataSourceFactory;
import org.dreamwork.config.IConfiguration;
import org.dreamwork.config.PropertyConfiguration;
import org.dreamwork.db.PostgreSQL;
import org.dreamwork.db.WrappedSQLite;
import org.dreamwork.injection.AConfigured;
import org.dreamwork.injection.IObjectContext;
import org.dreamwork.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Resource
public class DBCPConfiguration {
    private static final Pattern PATTERN = Pattern.compile ("^dbcp\\.(.*?)\\.driver$");
    private final Logger logger = LoggerFactory.getLogger (DBCPConfiguration.class);

    @SuppressWarnings ("unused")
    @AConfigured ("${dbcp.conf}")
    private String extraConfig;

    @Resource
    private IObjectContext context;

    @Resource
    private IConfiguration conf;

    @PostConstruct
    public void createDatabaseConnectionPool () {
        if (logger.isTraceEnabled ()) {
            logger.trace ("trying to create database connection pool...");
        }

        Properties props = null;
        if (!StringUtil.isEmpty (extraConfig)) {
            File file = new File (extraConfig);
            if (file.exists ()) {
                props = new Properties ();
                try (InputStream in = Files.newInputStream (file.toPath ())) {
                    props.load (in);
                } catch (IOException ex) {
                    logger.warn (ex.getMessage (), ex);
                    throw new RuntimeException (ex);
                }
            }
        } else {
            props = ((PropertyConfiguration) conf).getRawProperties ();
        }
        createConnectionPool (props);
    }

    private void createConnectionPool (Properties props) {
        if (props == null) {
            logger.warn ("there's no configurations");
            return;
        }

        Set<String> set = new HashSet<> ();
        for (String key : props.stringPropertyNames ()) {
            Matcher m = PATTERN.matcher (key);
            if (m.matches ()) {
                set.add (m.group (1));
            }
        }
        if (logger.isTraceEnabled ()) {
            logger.trace ("found {} pool settings: {}", set.size (), set);
        }
        if (!set.isEmpty ()) {
            set.forEach (name -> createConnectionPool (new PropertyConfiguration (props), name));
        }
    }

    private void createConnectionPool (IConfiguration conf, String name) {
        String driver   = conf.getString ("dbcp." + name + ".driver");
        String url      = conf.getString ("dbcp." + name + ".url");
        String user     = conf.getString ("dbcp." + name + ".user");
        String password = conf.getString ("dbcp." + name + ".password");

        int total       = conf.getInt ("dbcp." + name + ".max.total", 30);
        int idle        = conf.getInt ("dbcp." + name + ".max.idle", 10);
        int wait        = conf.getInt ("dbcp." + name + ".max.wait", 10000);
        int timeout     = conf.getInt ("dbcp." + name + ".remove.timeout", 60);
        boolean abandon = conf.getBoolean ("dbcp." + name + ".log.abandoned", true);

        Properties props = new Properties ();
        props.setProperty ("maxTotal", String.valueOf (total));
        props.setProperty ("maxIdle", String.valueOf (idle));
        props.setProperty ("maxWaitMillis", String.valueOf (wait));
        props.setProperty ("logAbandoned", String.valueOf (abandon));
        props.setProperty ("removeAbandonedTimeout", String.valueOf (timeout));
        if (!StringUtil.isEmpty (user))
            props.setProperty ("username", user);
        if (!StringUtil.isEmpty (password))
            props.setProperty ("password", password);
        props.setProperty ("driverClassName", driver);
        props.setProperty ("url", url);
        if (logger.isTraceEnabled ()) {
            logger.trace ("creating connection pool[{}] use parameters: {", name);
            for (String key : props.stringPropertyNames ()) {
                logger.trace ("\t{} => {}", key, props.getProperty (key));
            }
            logger.trace ("}");
        }
        try {
            DataSource ds = BasicDataSourceFactory.createDataSource (props);
            context.register (name, ds);
            if (driver.contains ("postgresql")) {
                context.register ("postgresql/" + name, new PostgreSQL (ds));
            } else if (driver.contains ("sqlite")) {
                context.register ("sqlite/" + name, new WrappedSQLite (ds));
            }
        } catch (Exception ex) {
            logger.warn (ex.getMessage (), ex);
            throw new RuntimeException (ex);
        }
    }
}
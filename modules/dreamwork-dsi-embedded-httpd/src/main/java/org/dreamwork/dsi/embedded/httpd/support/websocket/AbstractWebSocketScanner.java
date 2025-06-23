package org.dreamwork.dsi.embedded.httpd.support.websocket;

import org.dreamwork.dsi.embedded.httpd.starter.WebSocketManager;
import org.dreamwork.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.Endpoint;
import javax.websocket.server.ServerApplicationConfig;
import javax.websocket.server.ServerEndpointConfig;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by game on 2017/2/16
 */
public abstract class AbstractWebSocketScanner implements ServerApplicationConfig {
    private static final Logger logger = LoggerFactory.getLogger (AbstractWebSocketScanner.class);

    protected abstract Set<Class<? extends Endpoint>> getSupportedEndpointClasses ();
    protected Set<Class<?>> getSupportedAnnotatedEndpointClasses () {
        return null;
    }

    @Override
    public Set<ServerEndpointConfig> getEndpointConfigs (Set<Class<? extends Endpoint>> endpointClasses) {
        Set<ServerEndpointConfig> set = new HashSet<> ();
        Set<Class<? extends Endpoint>> filters = getSupportedEndpointClasses ();
        for (Class<? extends Endpoint> type : endpointClasses) {
            if (type.isAnnotationPresent (AWebSocket.class)) {
                int modifier = type.getModifiers ();
                if (Modifier.isAbstract (modifier) || type.isInterface ()) {
                    continue;
                }

                AWebSocket ws = type.getAnnotation (AWebSocket.class);
                String mapping = ws.endpoint ();
                if (StringUtil.isEmpty (mapping)) {
                    mapping = ws.value ();
                }

                if (StringUtil.isEmpty (mapping) || mapping.charAt (0) != '/') {
                    logger.warn ("Invalid url mapping: {}, ignore the class: {}", mapping, type);
                    continue;
                }

                if (filters.contains (type)) {
                    Class<? extends ServerEndpointConfig.Configurator> typeOfCfg = ws.configurator ();
                    ServerEndpointConfig.Configurator cfg;
                    if (typeOfCfg == WebSocketHttpConfigurator.class) {
                        cfg = new WebSocketHttpConfigurator (
                                ws.httpSession (),
                                ws.header (),
                                ws.servletContext (),
                                ws.parameter ()
                        );
                    } else {
                        cfg = loadConfigurator (typeOfCfg);
                    }
                    ServerEndpointConfig.Builder builder = ServerEndpointConfig.Builder.create (type, mapping);
                    builder.configurator (cfg);
                    ServerEndpointConfig config = builder.build ();
                    set.add (config);
                    if (logger.isTraceEnabled ()) {
                        logger.trace ("got a web socket class: {} bound as {}", type, ws.value ());
                    }

                    if (AbstractWebSocket.class.isAssignableFrom (type)) {
                        if (ws.cache () && ws.messageCacheTimeout () > 0) {
                            // 扫描到消息可缓存的 websocket 类型
                            @SuppressWarnings ("unchecked")
                            Class<? extends AbstractWebSocket<?>> aws = (Class<? extends AbstractWebSocket<?>>) type;
                            WebSocketManager.enableCache (aws, ws.messageCacheTimeout ());
                        }
                    }
                }
            }
        }

        return set;
    }

    @Override
    public Set<Class<?>> getAnnotatedEndpointClasses (Set<Class<?>> scanned) {
        Set<Class<?>> set = new HashSet<> ();
        Set<Class<?>> filters = getSupportedAnnotatedEndpointClasses ();

        if (filters == null || filters.isEmpty ()) {
            return scanned;
        }

        for (Class<?> type : scanned) {
            if (filters.contains (type)) {
                set.add (type);
            }
        }

        return set;
    }

    private ServerEndpointConfig.Configurator loadConfigurator (Class<? extends ServerEndpointConfig.Configurator> type) {
        try {
            return type.newInstance ();
        } catch (Exception ex) {
            throw new RuntimeException (ex);
        }
    }
}
package org.dreamwork.dsi.embedded.httpd.support.websocket;

import org.dreamwork.dsi.embedded.httpd.starter.WebSocketManager;
import org.dreamwork.injection.IObjectContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.websocket.*;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Created by game on 2017/2/16
 *
 * @since 2.1.0
 */
public abstract class AbstractWebSocket<T extends IWebsocketCommand>
        extends Endpoint
        implements IWebSocketExecutor<T>, MessageHandler.Whole<String> {
    protected Session session;
    protected ServletContext application;
    protected HttpSession httpSession;
    protected Map<String, List<String>> parameters;
    protected Map<String, List<String>> headers;
    protected String queryString;
    protected URI uri;

    protected WebSocketManager manager;

    protected IObjectContext context;

    private static final Logger logger = LoggerFactory.getLogger (AbstractWebSocket.class);

    @Override
    @SuppressWarnings ("unchecked")
    public final void onOpen (Session session, EndpointConfig config) {
        AWebSocket ws = getClass ().getAnnotation (AWebSocket.class);
        if (logger.isDebugEnabled ()) {
            logger.debug ("opening the web socket: {} var {}", ws.value (), this);
        }
        this.session = session;
        // 把自己注册成 Websocket 监听器
        session.addMessageHandler (this);
        if (logger.isDebugEnabled ()) {
            logger.debug ("caching myself into manager");
        }
        WebSocketManager.addService (this);
        // 注入参数
        Map<String, Object> props = config.getUserProperties ();
        if (ws.header ()) {
            headers = (Map<String, List<String>>) props.get (WebSocketHttpConfigurator.KEY_HTTP_HEADER);
        }
        if (ws.parameter ()) {
            parameters = (Map<String, List<String>>) props.get (WebSocketHttpConfigurator.KEY_HTTP_PARAM);
        }
        if (ws.servletContext ()) {
            application = (ServletContext) props.get (WebSocketHttpConfigurator.KEY_HTTP_CONTEXT);
        }
        if (ws.httpSession ()) {
            httpSession = (HttpSession) props.get (WebSocketHttpConfigurator.KEY_HTTP_SESSION);
        }
        queryString = (String) props.get (WebSocketHttpConfigurator.KEY_HTTP_QUERY);
        uri = (URI) props.get (WebSocketHttpConfigurator.KEY_HTTP_URI);
        onConnected (session);
    }

    @Override
    public final void onClose (Session session, CloseReason reason) {
        AWebSocket ws = getClass ().getAnnotation (AWebSocket.class);
        if (logger.isTraceEnabled ()) {
            logger.trace ("closing the web socket: {} => {}", ws.value (), this);
        }
        // 从 WebsocketManager 中删除本实例
        WebSocketManager.removeService (this);
        if (logger.isTraceEnabled ()) {
            logger.trace ("remove myself from manager");
        }

        try {
            this.session.removeMessageHandler (this);
            this.session.close ();
        } catch (Exception ex) {
            logger.warn (ex.getMessage (), ex);
        } finally {
            onDisconnected (reason);
            this.session = null;
        }
    }

    @Override
    public void onError (Throwable error) {
        logger.warn (error.getMessage (), error);
    }

    @Override
    public final void send (T message) {
        if (session == null || session.getBasicRemote () == null || !session.isOpen ()) {
            logger.warn ("the websocket session was closed. ignore this require");
        } else {
            String text = null;
            try {
                text = cast (message);
                session.getBasicRemote ().sendText (text);
                onMessageSent (message);
                if (logger.isTraceEnabled ()) {
                    logger.trace ("message send: {}", text);
                }
            } catch (Exception ex) {
                logger.error ("text = {}", text);
                logger.warn (ex.getMessage (), ex);
                onError (ex);
            }
        }
    }

    @Override
    public final void close () throws IOException {
        if (session != null && session.isOpen ()) {
            session.close (new CloseReason (CloseReason.CloseCodes.NORMAL_CLOSURE, null));
        }
    }

    @Override
    public void onMessageSent (T message) {

    }

    @Override
    public final void onMessage (String text) {
        try {
            handleMessage (parse (text));
        } catch (Throwable ex) {
            onError (ex);
        }
    }

    @Override
    public Session getSession () {
        return this.session;
    }

    @Override
    public void setContext (IObjectContext context) {
        this.context = context;
    }

    @Override
    public void setWebsocketManager (WebSocketManager manager) {
        this.manager = manager;
    }

    @Override
    public void onConnected (Session session) {
        if (logger.isTraceEnabled ()) {
            logger.trace ("a websocket connected: {}", session.getId ());
        }
    }

    @Override
    public void onDisconnected (CloseReason reason) {
        if (logger.isTraceEnabled ()) {
            logger.trace ("a websocket disconnected: id = {}, season = {}", session.getId (), reason);
        }
    }

    protected abstract String cast (T message);
    protected abstract T parse (String text);
}
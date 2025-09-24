package org.dreamwork.dsi.embedded.httpd.support.websocket;

import org.dreamwork.dsi.embedded.httpd.support.Cache;
import org.dreamwork.util.ReferenceUtil;
import org.dreamwork.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.websocket.HandshakeResponse;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;
import java.util.HashMap;
import java.util.Map;

import static org.dreamwork.dsi.embedded.httpd.support.WebComponentHelper.inject;
import static org.dreamwork.dsi.embedded.httpd.support.WebComponentHelper.parseType;

/**
 * Created by game on 2017/3/4
 *
 * @since 2.1.0
 */
public class WebSocketHttpConfigurator extends ServerEndpointConfig.Configurator {
    private static final Logger logger = LoggerFactory.getLogger (WebSocketHttpConfigurator.class);

    public static final String KEY_HTTP_SESSION = "org.dreamwork.websocket.conf.KEY_HTTP_SESSION";
    public static final String KEY_HTTP_HEADER  = "org.dreamwork.websocket.conf.KEY_HTTP_HEADER";
    public static final String KEY_HTTP_CONTEXT = "org.dreamwork.websocket.conf.KEY_HTTP_CONTEXT";
    public static final String KEY_HTTP_PARAM   = "org.dreamwork.websocket.conf.KEY_HTTP_PARAM";
    public static final String KEY_HTTP_QUERY   = "org.dreamwork.websocket.conf.KEY_HTTP_QUERY_STRING";
    public static final String KEY_HTTP_URI     = "org.dreamwork.websocket.conf.KEY_HTTP_URI";

    private static final String TOMCAT_WS_REQUEST_CLASS = "org.apache.tomcat.websocket.server.WsHandshakeRequest";

    boolean httpSessionSupported, httpHeaderSupported, servletContextSupported, httpParameterSupported;

    WebSocketHttpConfigurator (boolean httpSessionSupported, boolean httpHeaderSupported, boolean servletContextSupported, boolean httpParameterSupported) {
        this.httpSessionSupported = httpSessionSupported;
        this.httpHeaderSupported = httpHeaderSupported;
        this.servletContextSupported = servletContextSupported;
        this.httpParameterSupported = httpParameterSupported;
    }

    @Override
    public void modifyHandshake (ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
        ServletContext webapp = null;
        HttpSession session = (HttpSession) request.getHttpSession ();
        if (session == null) {
            Class<?> type = request.getClass ();
            if (TOMCAT_WS_REQUEST_CLASS.equals (type.getCanonicalName ())) {
                try {
                    HttpServletRequest http = (HttpServletRequest) ReferenceUtil.get (request, "request");
                    webapp = http.getServletContext ();
                } catch (Exception ex) {
                    logger.warn (ex.getMessage (), ex);
                }
            }
        }
        Map<String, Object> map = sec.getUserProperties ();

        if (httpHeaderSupported) {
            map.put (KEY_HTTP_HEADER, request.getHeaders ());
            if (logger.isTraceEnabled ()) {
                logger.trace ("put HTTP HEADER as " + KEY_HTTP_HEADER);
                logger.trace ("    the headers = {}", request.getHeaders ());
            }
        }

        if (httpParameterSupported) {
            map.put (KEY_HTTP_PARAM, request.getParameterMap ());
            if (logger.isTraceEnabled ()) {
                logger.trace ("put HTTP PARAMETERS as " + KEY_HTTP_PARAM);
                logger.trace ("    the parameters = {}", request.getParameterMap ());
            }
        }

        if (httpSessionSupported && session != null) {
            map.put (KEY_HTTP_SESSION, session);
            if (logger.isTraceEnabled ()) {
                logger.trace ("put HTTP SESSION as " + KEY_HTTP_SESSION);
                logger.trace ("    the httpSession = {}", session);
            }
        }

        if (servletContextSupported) {
            if (session != null) {
                map.put (KEY_HTTP_CONTEXT, session.getServletContext ());
                if (logger.isTraceEnabled ()) {
                    logger.trace ("put Servlet Context as {}", session.getServletContext ());
                }
            } else if (webapp != null) {
                map.put (KEY_HTTP_CONTEXT, webapp);
            }
        }

        String queryString = request.getQueryString ();
        if (!StringUtil.isEmpty (queryString)) {
            map.put (KEY_HTTP_QUERY, queryString);
        }
        map.put (KEY_HTTP_URI, request.getRequestURI ());

        if (logger.isTraceEnabled ()) {
            logger.trace ("put HTTP QUERY STRING as " + KEY_HTTP_QUERY);
            logger.trace ("    the query string = {}", request.getQueryString ());
            logger.trace ("put HTTP URI as " + KEY_HTTP_URI);
            logger.trace ("    the uri = {}", request.getRequestURI ());
        }
    }

    @Override
    public <T> T getEndpointInstance (Class<T> type) throws InstantiationException {
        T instance = super.getEndpointInstance (type);
        Cache c = cache.computeIfAbsent (type, key -> parseType (type));
        return inject (c, instance);
    }

    private static final Map<Class<?>, Cache> cache = new HashMap<> ();
}
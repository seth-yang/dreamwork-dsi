package org.dreamwork.dsi.embedded.httpd.support.websocket;

import javax.websocket.server.ServerEndpointConfig;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @since 2.1.0
 */
@Retention (RetentionPolicy.RUNTIME)
@Target (ElementType.TYPE)
public @interface AWebSocket {
    /** endpoint 的简易名称 */
    String value ()             default ""; // alias to endpoint ()
    /** websocket 的 路径 */
    String endpoint ()          default ""; // alias to value ()
    /** 是否自动注入 http 头 */
    boolean header ()           default false;
    /** 是否自动注入 web 参数，从 HttpServletRequest.getParameter 能够获取的 */
    boolean parameter ()        default false;
    /** 是否自动注入 http session 对象 */
    boolean httpSession ()      default false;
    /** 是否自动注入 servlet context 对象 */
    boolean servletContext ()   default false;
    /** 指定 websocket 心跳的间隔，0 或 负值表示不启用自动心跳，单位毫秒。默认启用并设置为 50s */
    long heartbeat ()           default 50000;
    /** 是否自动缓存在 WebSocketManager 中，默认为 true */
    boolean cache ()            default true;
    /** 如果 cache () 返回 true，则该字段指示消息缓存的最大时长，单位毫秒。默认 50s */
    long messageCacheTimeout () default 50000;

    /** WebSocket 的配置类型，当且仅当为 WebSocketHttpConfigurator.class 时，以上那些 boolean 类型的自动注入才能生效 */
    Class<? extends ServerEndpointConfig.Configurator> configurator () default WebSocketHttpConfigurator.class;
}
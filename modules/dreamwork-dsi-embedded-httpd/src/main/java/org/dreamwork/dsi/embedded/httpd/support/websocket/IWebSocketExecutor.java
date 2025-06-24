package org.dreamwork.dsi.embedded.httpd.support.websocket;

import org.dreamwork.dsi.embedded.httpd.starter.WebSocketManager;
import org.dreamwork.injection.IObjectContext;

import javax.websocket.CloseReason;
import javax.websocket.Session;
import java.io.IOException;

/**
 * Created by game on 2017/2/16
 *
 * @since 2.1.0
 */
public interface IWebSocketExecutor<T extends IWebsocketCommand> {
    /**
     * 发送一个消息
     * @param message 消息
     */
    void send (T message);

    /**
     * 关闭当前 websocket 实例
     * @throws IOException 任何错误
     */
    void close () throws IOException;

    /**
     * 获取当前 websocket 的会话
     * @return 当前 websocket 的会话
     */
    Session getSession ();

    /**
     * 设置托管对象容器
     * @param context 托管对象容器
     */
    void setContext (IObjectContext context);

    /** 设置 websocket 管理器 */
    void setWebsocketManager (WebSocketManager manager);

    /**
     * websocket 在收发消息，握手等任意一个生命周期内发送错误的处理函数
     * @param error 错误消息
     */
    void onError (Throwable error);

    /**
     * 当一个消息被成功发送时触发的处理器函数
     * @param message 消息
     */
    void onMessageSent (T message);

    /**
     * 当一个 websocket 握手成功后触发的处理函数
     * @param session websocket 会话
     */
    void onConnected (Session session);

    /**
     * 当一个 websocket 断开连接时触发的处理器函数
     * @param reason 关闭原因
     */
    void onDisconnected (CloseReason reason);

    /**
     * 判断指定 ID 的 消息{@code message} 是否满足要求.
     * {@link WebSocketManager} 使用这个方法的返回值来判断，消息 {@code message} 是否应该发送到当前 websocket 上
     * @param id      一个特定的id，可能为空
     * @param message 一个具体消息
     * @return 若当前 websocket 应该处理这个消息时应该返回 {@code true}，否则返回 {@code false}
     */
    boolean matches (String id, T message);

    /**
     * 处理接收到的消息
     * @param message 接收到的消息
     */
    void handleMessage (T message);
}
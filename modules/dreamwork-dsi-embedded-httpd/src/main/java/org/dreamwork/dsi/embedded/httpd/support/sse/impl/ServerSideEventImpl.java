package org.dreamwork.dsi.embedded.httpd.support.sse.impl;

import org.dreamwork.dsi.embedded.httpd.support.sse.IServerSideEvent;
import org.dreamwork.dsi.embedded.httpd.support.sse.NoAvailableException;
import org.dreamwork.dsi.embedded.httpd.support.sse.SseFrame;
import org.dreamwork.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link IServerSideEvent} 的实现：把接口调用转发给 {@link SseHub}。
 *
 * <p>每个 SSE 请求对应一个实例，由 {@code BackendServlet} 在注册会话后创建并注入到业务方法参数中：
 * 它会先通过 {@link SseHub#register} 拿到 {@link SseSession}，再调用 {@link #setHub} / {@link #setSession}
 * 完成装配。因此这些 setter 属于框架内部装配用，业务代码不需要（也不应该）调用。</p>
 *
 * @see IServerSideEvent
 */
public class ServerSideEventImpl implements IServerSideEvent {
    private final Logger logger = LoggerFactory.getLogger (ServerSideEventImpl.class);

    /**
     * 本次连接的唯一标识。
     * <p>它同时承担两个角色：作为该连接的<b>私有通道名</b>（{@link #send(SseFrame)} 的目标通道），
     * 以及作为 {@link SseHub#commit(String, Runnable)} 的<b>任务标识</b>。</p>
     */
    public final String uuid = StringUtil.uuid ();

    /** 本次连接对应的会话，由框架装配（见 {@link #setSession}）。 */
    private SseSession session;
    /** 全局 SSE 调度中心，由框架装配（见 {@link #setHub}）。 */
    private SseHub hub;

    /**
     * 框架内部使用：绑定本次连接对应的会话。
     *
     * @param session SSE 会话
     */
    public void setSession (SseSession session) {
        this.session = session;
    }

    /**
     * 框架内部使用：绑定全局 SSE Hub。
     *
     * @param hub SSE Hub 实例
     */
    public void setHub (SseHub hub) {
        this.hub = hub;
    }

    @Override
    public void send (SseFrame frame) {
        // 私有消息：以本连接的 uuid 作为通道名，只投递给自己的会话
        send (uuid, frame);
    }

    @Override
    public void send (String channel, SseFrame frame) {
        // hub 为空说明会话未装配成功（例如业务方法被非 SSE 方式调用），此处静默忽略
        if (hub != null) {
            hub.publish (channel, frame);
        }
    }

    @Override
    public void close () {
        if (hub != null) {
            hub.unregister (session);
        }
    }

    @Override
    public void closeWithError (Throwable error) {
        if (error != null && session != null) {
            String message = error.getMessage ();
            logger.warn (message, error);

            // 先把异常信息推给客户端，再关闭，保证前端能拿到失败原因
            SseFrame frame = new SseFrame.Builder ()
                    .id (StringUtil.uuid ())
                    .event ("exception")
                    .json (true)
                    .data (message).build ();
            session.enqueue (frame.toString ());
        }
        close ();
    }

    @Override
    public void attach (String... channels) throws NoAvailableException {
        // 已关闭的会话不再附着，避免把死会话挂进频道
        if (hub != null && session != null && !session.isClosed ()) {
            hub.attach (session, channels);
        }
    }
}

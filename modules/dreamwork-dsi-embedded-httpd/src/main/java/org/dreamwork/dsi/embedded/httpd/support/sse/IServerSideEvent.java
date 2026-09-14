package org.dreamwork.dsi.embedded.httpd.support.sse;

public interface IServerSideEvent {
    /** 发送一条私有消息 */
    void send (SseFrame frame);
    /** 向通道 {@code channel} 发送一条消息 */
    void send (String channel, SseFrame frame);
    /** 当前 sse 任务正常结束 */
    void close ();
    /** 当出错是，结束当前 sse 任务 */
    void closeWithError (Throwable error);
    /** 附着到指定的通道 */
    void attach (String... channels);
}

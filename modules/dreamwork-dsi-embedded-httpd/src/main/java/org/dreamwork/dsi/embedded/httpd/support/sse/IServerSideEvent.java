package org.dreamwork.dsi.embedded.httpd.support.sse;

/**
 * SSE 服务端事件接口：供业务方法向客户端主动推送数据。
 *
 * <p>当一个控制器方法被 {@code @AServerSideEvent} 标记后，框架会自动完成三件事：</p>
 * <ol>
 *   <li>把该请求升级为 SSE 长连接（对应 {@code SseHub#register}）；</li>
 *   <li>为本次连接创建一个本接口的实现，并作为方法参数注入
 *       （只需把方法参数声明为 {@code IServerSideEvent} 类型即可）；</li>
 *   <li>在独立的线程池中异步调用该业务方法，不阻塞 HTTP 请求线程。</li>
 * </ol>
 *
 * <p>典型用法：</p>
 * <pre>{@code
 * @AServerSideEvent (channels = "chat")
 * public void onConnect (IServerSideEvent event) {
 *     event.send (new SseFrame.Builder ().event ("welcome").data ("hello").build ());
 *     // ... 后续可以用 event.send(...) 继续推送
 *     event.close ();
 * }
 * }</pre>
 *
 * <p><b>注意：</b>本接口的实例与单次 SSE 连接一一对应，不要跨请求共享或在连接关闭后继续调用。</p>
 *
 * @see SseFrame
 * @see NoAvailableException
 */
public interface IServerSideEvent {
    /**
     * 发送一条私有消息：只投递给当前连接自己的会话（即以其私有通道为目标的广播），
     * 同频道内的其他订阅者不会收到。
     *
     * <p>方法内部不阻塞，数据先入队，由 Hub 的事件循环异步写出。</p>
     *
     * @param frame 待发送的数据帧
     */
    void send (SseFrame frame);

    /**
     * 向通道 {@code channel} 发送一条消息：该通道内的所有会话（包括自己）都会收到。
     *
     * @param channel 目标通道名；可以是本连接声明过的通道，也可以是任意已存在的通道
     * @param frame   待发送的数据帧
     */
    void send (String channel, SseFrame frame);

    /**
     * 当前 sse 任务正常结束：向客户端补发一个 {@code .complete} 事件并关闭连接，
     * 同时把会话从所有通道中摘除。
     *
     * <p>重复调用是安全的（内部幂等）。</p>
     */
    void close ();

    /**
     * 当出错是，结束当前 sse 任务：先把异常信息以 {@code exception} 事件推送给客户端，
     * 然后再关闭连接。
     *
     * @param error 导致任务结束的异常；为 {@code null} 时等价于 {@link #close()}
     */
    void closeWithError (Throwable error);

    /**
     * 附着到指定的通道：在连接建立后动态地把本会话挂到更多通道上。
     *
     * <p>典型场景是订阅者根据请求参数决定要订阅哪些通道。注意：若本连接是
     * 「订阅者 + 不允许动态附着」，则只有<b>已存在生产者</b>的通道才能附着成功。</p>
     *
     * @param channels 目标通道名
     * @throws NoAvailableException 没有任何可用的通道
     */
    void attach (String... channels) throws NoAvailableException;
}

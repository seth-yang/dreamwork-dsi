package org.dreamwork.dsi.embedded.httpd.support.sse.impl;

import jakarta.servlet.AsyncContext;
import org.dreamwork.dsi.embedded.httpd.support.sse.SseRole;
import org.dreamwork.dsi.embedded.httpd.support.sse.SseFrame;
import org.dreamwork.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 一个 SSE 连接的运行时载体。
 *
 * <p>它把 Servlet 的 {@link AsyncContext} 与「待发送队列 + 所属频道 + 角色」绑定在一起，
 * 对外提供两类操作：</p>
 * <ul>
 *   <li><b>入队</b>：{@link #enqueue(String)} 由 {@link SseHub} 在任意业务线程调用，只入队不写网络；</li>
 *   <li><b>写出</b>：{@link #flush()} 和 {@link #ping()} 只允许由 {@code SseHub} 的单线程事件循环调用，
 *       因此这些方法本身不做同步。</li>
 * </ul>
 *
 * <p><b>背压：</b>队列无界，生产与写出解耦；慢客户端只会让队列变长，不会阻塞发布方。</p>
 *
 * <p><b>生命周期：</b>由 {@code SseHub#register} 创建，在连接结束（正常/超时/出错）或空闲超时后
 * 由 {@link #close()} 关闭；关闭操作幂等，并在关闭前向客户端补发一个 {@code .complete} 事件。</p>
 *
 * @see SseHub
 */
public class SseSession {
    private final Logger logger = LoggerFactory.getLogger (SseSession.class);
    /** Servlet 异步上下文，连接的所有权凭证。 */
    private final AsyncContext ac;
    /** 响应输出流；只由事件循环线程使用。 */
    private final PrintWriter writer;
    /** 本会话所属的频道名（创建时的快照，只读）。 */
    private final Set<String> channels;
    /** 待发送队列：业务线程入队，事件循环出队写出。 */
    private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<> ();
    /** 关闭标记，保证 {@link #close()} 只真正执行一次。 */
    private final AtomicBoolean closed = new AtomicBoolean ();
    /** 空闲超时时间（毫秒），超过该时长没有任何数据写出即判定连接失效。 */
    private final long TIMEOUT;
    /** 会话在 SSE 中的角色（生产者 / 订阅者），挂在会话上供 Hub 判定频道是否还有生产者。 */
    public final SseRole role;
    /** 是否允许动态附着到新通道，见 {@code SseHub#attach}。 */
    public final boolean allowDynamic;

    /** 最后一次成功写出数据的时间戳；为 {@code null} 表示尚未写出过任何数据。 */
    private transient Long timestamp;

    /**
     * 创建一个会话。
     *
     * <p>{@code channels} 会被复制保存（{@code Set.copyOf}），之后外部对该集合的修改不会影响本会话。</p>
     *
     * @param ac           已开启的 Servlet 异步上下文
     * @param role         会话在 SSE 中的角色
     * @param channels     初始所属的频道名集合
     * @param timeout      空闲超时时间（毫秒）
     * @param allowDynamic 是否允许动态附着到新通道
     * @throws IOException 获取响应的 {@code Writer} 失败
     */
    SseSession (AsyncContext ac, SseRole role, Set<String> channels, long timeout, boolean allowDynamic) throws IOException {
        this.ac       = ac;
        this.writer   = ac.getResponse ().getWriter ();
        this.channels = Set.copyOf (channels);
        this.role     = role;
        this.TIMEOUT  = timeout;
        this.allowDynamic = allowDynamic;
    }

    /**
     * 把一帧已渲染好的数据放入发送队列。
     *
     * <p>线程安全（无界 {@link ConcurrentLinkedQueue}），只入队不做网络写出，
     * 因此可以被任意业务线程调用而不会阻塞。</p>
     *
     * @param frame 已渲染为 SSE 文本的数据帧
     */
    void enqueue (String frame) {
        queue.offer (frame);
    }

    /**
     * 由 EventLoop 调用
     *
     * <p>把队列中待发送的数据刷给客户端，流程为：</p>
     * <ol>
     *   <li>若曾经写出过数据且已静默超过 {@link #TIMEOUT}，判定连接失效：
     *       补发一个 {@code SESSION_TIMEOUT} 错误事件，然后关闭会话并直接返回；</li>
     *   <li>否则一次性排空队列并写出；只有确实写出了数据才更新 {@link #timestamp}。</li>
     * </ol>
     *
     * <p>本方法只能由 {@code SseHub} 的单线程事件循环调用，不允许并发。</p>
     *
     * @throws IOException 写出失败（通常意味着客户端已断开），由调用方负责关闭并摘除会话
     */
    void flush () throws IOException {
        long now = System.currentTimeMillis ();
        if (timestamp != null) {
            // 曾经写过
            if (now - timestamp > TIMEOUT) {
                logger.warn ("SSE timed out after {} ms no data flushed.", TIMEOUT);
                // 把超时原因告诉客户端，便于前端区分「服务器判死」和「网络断开」
                var data = Map.of (
                        "message", "session timeout after " + TIMEOUT + "  ms not data flushed.",
                        "code", "SESSION_TIMEOUT"
                );
                var builder = new SseFrame.Builder ()
                        .id (StringUtil.uuid ()).event ("error")
                        .json (true).data (data);
                enqueue (builder.build ().toString ());
                close ();
                return;
            }
        }

        String frame;
        boolean written = false;
        while ((frame = queue.poll ()) != null) {
            if (logger.isDebugEnabled ()) {
                logger.debug ("writing {} to client", frame);
            }
            writer.write (frame);
            written = true;
        }

        // 只有真正写出过数据才刷新并更新存活时间戳
        if (written) {
            writer.flush ();
            timestamp = now;
        }
    }

    /**
     * 会话是否已关闭。
     *
     * @return {@code true} 表示已关闭
     */
    boolean isClosed () {
        return closed.get ();
    }

    /**
     * 关闭会话：向客户端补发一个 {@code .complete} 事件，关闭输出流并结束异步上下文。
     *
     * <p>通过 CAS 保证幂等，重复调用不会重复发送事件；内部异常只记录日志，不向外抛出。</p>
     */
    void close () {
        if (logger.isDebugEnabled ()) {
            logger.debug ("closing the session: {}", this);
        }

        if (closed.compareAndSet (false, true)) {
            try {
                if (logger.isDebugEnabled ()) {
                    logger.debug ("send the complete event to client ...");
                }

                // 通知客户端：本次 SSE 任务已正常结束
                var builder = new SseFrame.Builder ()
                        .id (StringUtil.uuid ())
                        .json (true)
                        .event (".complete");
                enqueue (builder.build ().toString ());
                if (logger.isDebugEnabled ()) {
                    logger.debug ("complete event push into the queue");
                }

                // 借助 flush 把队列里（含上面的 .complete）剩余数据一次性写出
                flush ();
                writer.close ();
            } catch (Exception ex) {
                logger.warn (ex.getMessage (), ex);
            }
            ac.complete ();
        }
    }

    /**
     * 返回本会话所属的频道名集合（创建时的快照，只读）。
     *
     * @return 频道名集合
     */
    Set<String> channels () {
        return channels;
    }

    /**
     * 心跳
     *
     * <p>向客户端发送一条 SSE 注释帧（{@code : ping}），仅用于保活连接并尽早探测出已失效的连接。</p>
     *
     * @throws IOException 写出失败，说明连接已不可用
     */
    void ping () throws IOException {
        writer.write (": ping\n\n");
        writer.flush ();
    }
}

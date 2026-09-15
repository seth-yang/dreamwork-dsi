package org.dreamwork.dsi.embedded.httpd.support.sse.impl;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.dreamwork.dsi.embedded.httpd.support.sse.SseRole;
import org.dreamwork.dsi.embedded.httpd.support.sse.NoAvailableException;
import org.dreamwork.dsi.embedded.httpd.support.sse.SseFrame;
import org.dreamwork.util.DateUtil;
import org.dreamwork.util.IDisposable;
import org.dreamwork.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.dreamwork.util.CollectionHelper.isEmpty;
import static org.dreamwork.util.CollectionHelper.isNotEmpty;

/**
 * SSE（Server-Sent Events）服务端的核心调度中心（Hub），进程内单例。
 *
 * <p>它维护「频道（channel）→ 会话（{@link SseSession}）集合」的映射关系，是整个 SSE 能力的枢纽。
 * 一次典型的使用链路为：</p>
 * <ol>
 *   <li>业务方法被 {@code @AServerSideEvent} 标记后，由 {@code BackendServlet} 调用
 *       {@link #register} 把 HTTP 长连接升级为 SSE 会话，并按角色挂载到若干频道上；</li>
 *   <li>生产端调用 {@link #publish}，把 {@link SseFrame} 投递到频道内所有会话的发送队列；</li>
 *   <li>{@link #loop()} 在独立的调度线程中按 {@code 50ms} 的节奏，把各会话队列中的数据真正写到客户端；
 *       同时 {@link #heartbeat()} 每 {@code 15s} 发送一次心跳（SSE 注释帧）保活连接。</li>
 * </ol>
 *
 * <p><b>线程模型：</b></p>
 * <ul>
 *   <li>{@link #eventLoop}：单线程调度器，串行执行 {@link #loop()} 与 {@link #heartbeat()}，
 *       保证同一个会话的写操作不会并发；</li>
 *   <li>{@link #taskPool}：缓存线程池，用于异步执行业务侧的 SSE 处理方法（见 {@link #commit}），
 *       避免长时间占用 HTTP 请求线程。</li>
 * </ul>
 *
 * <p><b>背压（back pressure）：</b>每个 {@link SseSession} 内部维护一个待发送队列，{@link #publish}
 * 只负责入队，真正的网络写出由 {@link #loop()} 以固定节奏完成，从而把「生产速度」与「写出速度」解耦。</p>
 *
 * <p><b>生命周期：</b>由容器在 {@code @PostConstruct} / {@code @PreDestroy} 阶段调用
 * {@link #init()} / {@link #dispose()}；通常需要先通过 {@link #setTimeout(long)} 设置默认空闲超时，
 * 再调用 {@link #init()} 完成初始化。单个连接还可以在 {@link #register} 时通过 {@code sseTimeout}
 * 覆盖该默认值。</p>
 *
 * @see SseSession
 * @see SseFrame
 * @see SseRole
 */
public class SseHub implements IDisposable {
    /** 进程内单例，保证所有频道/会话共享同一份运行时状态。 */
    private static final SseHub INSTANCE = new SseHub ();

    /**
     * 获取全局唯一的 Hub 实例。
     *
     * @return SSE Hub 单例
     */
    public static SseHub instance () {
        return INSTANCE;
    }

    /** 频道名 → 该频道下的所有会话；使用写时复制集合，读取（遍历）时无需加锁。 */
    private ConcurrentMap<String, CopyOnWriteArraySet<SseSession>> channels;
    /** 任务标识 → 异步任务句柄，用于 {@link #killTask(String)} 取消业务侧的长任务。 */
    private ConcurrentMap<String, Future<?>> futures;
    /** 单线程事件循环：负责周期性刷数据（{@link #loop()}）与心跳（{@link #heartbeat()}）。 */
    private ScheduledExecutorService eventLoop;
    /** 业务侧 SSE 处理方法的执行线程池。 */
    private ExecutorService taskPool;
    /**
     * 空闲超时的<b>全局默认值</b>（毫秒）：单连接未指定（{@code <= 0}）时生效。
     * 超过该时长没有任何数据写出即判定连接失效，默认 30s。
     */
    private long timeout = 30000L; // 默认 30s 超时

    /**
     * 最近一次事件循环中抛出的异常。
     * <p>用于诊断，避免 {@link #loop()} 里的异常被静默吞掉导致事件循环“静默死亡”。</p>
     */
    private final AtomicReference<Throwable> failure = new AtomicReference<> ();
    /** 初始化标记，保证 {@link #init()} 幂等（重复调用不会重复创建线程池）。 */
    private final AtomicBoolean initialed = new AtomicBoolean ();

    private final Logger logger = LoggerFactory.getLogger (SseHub.class);

    /**
     * 初始化 Hub：创建频道表、事件循环线程、任务线程池，并注册定时任务。
     *
     * <p>该方法幂等，重复调用只会执行一次。启动后会在事件循环上添加两个定时任务：</p>
     * <ul>
     *   <li>心跳：每 {@code 15s} 一次；</li>
     *   <li>刷数据：固定延迟 {@code 50ms}（即上一轮结束后再等 50ms，避免任务堆积）。</li>
     * </ul>
     */
    @PostConstruct
    public void init () {
        if (logger.isTraceEnabled ()) {
            logger.trace ("initialing the SSE Hub ...");
        }
        // CAS 保证并发调用（如容器与业务手动同时初始化）时只真正初始化一次
        if (initialed.compareAndSet (false, true)) {
            channels = new ConcurrentHashMap<> ();
            // 单线程调度器：刷数据与心跳串行执行，天然避免对同一会话的并发写
            eventLoop = Executors.newSingleThreadScheduledExecutor (r -> {
                Thread t = new Thread (r, "sse-event-loop");
                t.setDaemon (true);
                return t;
            });
            futures = new ConcurrentHashMap<> ();
            // 业务任务线程池：按需创建，线程命名便于排查问题
            taskPool = Executors.newCachedThreadPool (new ThreadFactory () {
                final AtomicInteger ai = new AtomicInteger (1);
                final ThreadGroup group = new ThreadGroup ("sse.executors");
                @Override
                public Thread newThread (Runnable runner) {
                    if (runner == null) {
                        throw new NullPointerException ();
                    }
                    String name = String.format ("%s.%d", group.getName (), ai.getAndIncrement ());
                    return new Thread (group, runner, name);
                }
            });

            // 心跳：所有连接每 15s
            eventLoop.scheduleAtFixedRate (this::heartbeat, 15, 15, TimeUnit.SECONDS);
            // 真正的广播循环（背压靠队列）
            eventLoop.scheduleWithFixedDelay (this::loop, 0, 50, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 销毁 Hub：关闭事件循环与任务线程池，并取消所有尚未结束的任务。
     * <p>由容器在 {@code @PreDestroy} 阶段调用。注意其中的线程池都使用 {@code shutdownNow()}，
     * 因此正在执行的任务会被中断。</p>
     */
    @PreDestroy
    @Override
    public void dispose () {
        logger.info ("destroying sse hub ...");
        if (eventLoop != null) {
            eventLoop.shutdownNow ();
        }
        if (taskPool != null) {
            taskPool.shutdownNow ();
        }
        // 取消所有在跑的业务任务，避免线程池关闭后仍有残留任务
        if (isNotEmpty (futures)) {
            for (var e : futures.entrySet ()) {
                e.getValue ().cancel (true);
            }
            futures.clear ();
        }
        logger.info ("sse hub destroyed");
    }

    /**
     * 设置会话空闲超时时间的<b>全局默认值</b>。
     *
     * <p>{@link #register} 支持按连接传入独立的超时时间（来自 {@code @AServerSideEvent#timeout ()}），
     * 仅当该值不大于 {@code 0} 时才回退到这里的默认值。</p>
     *
     * <p><b>注意：</b>超时值是在创建会话时被快照传入 {@link SseSession} 的，
     * 因此本方法对已经建立的连接不生效，建议在 {@link #init()} 之前调用。</p>
     *
     * @param timeout 默认的空闲超时时间，单位毫秒
     */
    public void setTimeout (long timeout) {
        this.timeout = timeout;
    }

    /**
     * Servlet 里调用：注册一个连接
     *
     * <p>该方法的处理过程：</p>
     * <ol>
     *   <li>先把 {@code channelNames} <b>复制</b>一份作为工作集合，后续操作全部基于副本；
     *       若当前是「订阅者 + 不允许动态附着」，则在该副本上做一次过滤，只保留那些<b>已存在生产者</b>的频道；</li>
     *   <li>把请求升级为 Servlet 异步请求，并写入 SSE 所需的响应头；</li>
     *   <li>若最终没有任何可加入的频道，则向客户端回一个 {@code error} 事件后结束请求；</li>
     *   <li>否则创建 {@link SseSession}、挂载频道、注册异步监听器，并立即发送一条连接成功注释帧。</li>
     * </ol>
     *
     * <p>会话的空闲超时取 {@code sseTimeout > 0 ? sseTimeout : timeout}，即优先使用本次连接的设置，
     * 否则回退到 {@link #setTimeout(long)} 配置的全局默认值。</p>
     *
     * <p><b>注意：</b>入参 {@code channelNames} 不会被修改；{@link SseSession} 持有的是内部副本，
     * 因此注册完成后再改动该集合也不会影响已建立的连接。</p>
     *
     * @param req           当前 HTTP 请求，用于开启异步上下文
     * @param resp          当前 HTTP 响应
     * @param role          该连接在 SSE 中的角色（生产者 / 订阅者）
     * @param channelNames  希望加入的频道集合，调用方可以安全地复用该集合（方法内部只读）
     * @param allowDynamic  是否允许动态附着（即允许把自己挂到运行期才出现/新增的频道上）
     * @param sseTimeout    本次连接的空闲超时时间（毫秒）；不大于 {@code 0} 时使用全局默认值
     * @return 注册成功的会话；若没有任何可加入的频道则返回 {@code null}
     * @throws IOException          写响应或获取 {@code Writer} 失败
     * @throws NoAvailableException 订阅者且不允许动态附着时，找不到包含生产者的频道
     */
    public SseSession register (HttpServletRequest req, HttpServletResponse resp,
                               SseRole role, Set<String> channelNames,
                                boolean allowDynamic, long sseTimeout) throws IOException, NoAvailableException {
        if (logger.isDebugEnabled ()) {
            logger.debug ("registering a new sse into {}, role = {}", channelNames, role);
        }

        // 先复制一份，后续的过滤/挂载都基于副本，绝不修改调用方传入的集合
        Set<String> copied = new HashSet<> (channelNames);
        if (role == SseRole.Subscriber && !allowDynamic) {
            // 订阅者，且不允许动态附着，不能创建新的通道，只能加入已有的通道
            // 这里的做法是：对请求的频道做一次「校验 + 过滤」，只保留当前已存在生产者的频道
            Set<String> toCheck = Set.copyOf (copied);
            copied.clear ();
            for (var name : toCheck) {
                var channel = channels.get (name);
                if (isNotEmpty (channel)) {
                    for (var session : channel) {
                        if (session.role == SseRole.Producer) {
                            // 生产者，可以加入这个频道
                            copied.add (name);
                            break;
                        }
                    }
                }
            }
        }

        // Tomcat 要求显式声明该请求支持异步，否则 startAsync 会抛异常
        req.setAttribute ("org.apache.catalina.ASYNC_SUPPORTED", true);
        AsyncContext ac = req.startAsync (req, resp);

        // SSE 协议要求的响应头；X-Accel-Buffering 用于关闭 Nginx 的响应缓冲
        resp.setContentType ("text/event-stream");
        resp.setCharacterEncoding ("UTF-8");
        resp.setHeader ("Cache-Control", "no-cache");
        resp.setHeader ("Connection", "keep-alive");
        resp.setHeader ("X-Accel-Buffering", "no");

        if (isEmpty (copied)) {
            // 没有可加入的频道，通知客户端，然后退出
            var data = Map.of (
                    "message", "no channel can be joined",
                    "code", "NO_AVAILABLE_CHANNEL"
            );
            var builder = new SseFrame.Builder ().id (StringUtil.uuid ()).event ("error")
                    .json (true).data (data);
            SseFrame frame = builder.build ();
            var writer = ac.getResponse ().getWriter ();
            writer.write (frame.toString ());
            writer.flush ();
            ac.complete ();
            return null;
        }

        // 按连接的超时优先，未指定（<= 0）时回退到全局默认值
        long sessionTimeout = sseTimeout > 0 ? sseTimeout : timeout;
        // 注意：会话持有的是频道集合的副本，后续即使调用方再改动入参集合也不会影响已建立的连接
        SseSession session = new SseSession (ac, role, copied, sessionTimeout, allowDynamic);

        // 加入 channel
        attach (session, copied);
        // 连接断开/超时/出错时，由该监听器负责收尾（关闭会话并从频道中摘除）
        ac.addListener (new SseAsyncListener (session));
        // 关闭容器的异步超时机制，连接的存活完全由 Hub 自己的空闲超时控制
        ac.setTimeout (0);
        if (logger.isDebugEnabled ()) {
            logger.debug ("session registered.");
        }

        // 立即发送连接成功
        session.enqueue (": connected\n\n");
        return session;
    }

    /**
     * 注销会话：关闭连接并从所有频道中摘除。
     * <p>业务侧通常通过 {@code IServerSideEvent#close()} 间接触发；重复调用是安全的。</p>
     *
     * @param session 待注销的会话，允许为 {@code null}
     */
    public void unregister (SseSession session) {
        if (logger.isDebugEnabled ()) {
            logger.debug ("unregistering the session: {}", session);
        }
        if (session != null) {
            if (!session.isClosed ()) {
                session.close ();
            }
            remove (session);
        }
    }

    /**
     * 业务线程调用
     *
     * <p>把一帧数据广播给指定频道内的所有会话。此处只做入队，真正的写出由 {@link #loop()} 完成，
     * 因此本方法不会因为慢客户端而阻塞。</p>
     *
     * @param channel 目标频道名
     * @param frame   待发送的数据帧
     */
    public void publish (String channel, SseFrame frame) {
        Set<SseSession> sessions = channels.get (channel);
        if (sessions == null) {
            logger.warn ("there's no session associated to channel: {}", channel);
            return;
        }
        // 帧内容对所有会话相同，只序列化一次
        String encoded = frame.toString ();
        for (SseSession s : sessions) {
            if (!s.isClosed ()) {
                s.enqueue (encoded);
            } else {
                logger.warn ("the session[channel={}] was closed.", channel);
            }
        }
    }

    /**
     * 提交一个异步任务，并以 {@code key} 作为其标识。
     *
     * <p>典型用法是执行 {@code @AServerSideEvent} 标记的业务方法，使其不阻塞 HTTP 请求线程；
     * 之后可通过 {@link #killTask(String)} 用同一个 {@code key} 取消任务。</p>
     *
     * @param key    任务标识（通常使用会话的 uuid）
     * @param runner 任务体
     */
    public void commit (String key, Runnable runner) {
        Future<?> future = this.taskPool.submit (runner);
        // 任务句柄必须保存，否则无法取消
        futures.put (key, future);
    }

    /**
     * 取消并移除由 {@link #commit(String, Runnable)} 提交的任务。
     *
     * @param key 任务标识
     */
    public void killTask (String key) {
        if (isNotEmpty (futures)) {
            var future = futures.remove (key);
            if (future != null) {
                future.cancel (true);
            } else {
                logger.warn ("there's no task mapped as {}", key);
            }
        }
    }

    /**
     * 把会话挂载到一个或多个频道上。
     *
     * <p>与会话创建后的「动态附着」流程对应。若会话是「订阅者 + 不允许动态附着」，
     * 则要求目标频道中至少存在一个生产者，且<b>只有校验通过的频道才会被挂载</b>；
     * 若一个都不满足，抛出 {@link NoAvailableException}。此外，对于新的频道名，
     * 会顺带创建对应的频道条目（即允许生产者在运行期扩展频道）。</p>
     *
     * @param session      待挂载的会话
     * @param channelNames 目标频道名
     * @throws NoAvailableException 订阅者且不允许动态附着时，没有任何可用频道
     */
    void attach (SseSession session, String... channelNames) throws NoAvailableException {
        Set<String> available = new HashSet<> (channelNames.length);
        if (session.role == SseRole.Subscriber && !session.allowDynamic) {
            // 订阅者，并且不允许动态附着，需要校验通道是否有生产者
            for (String ch : channelNames) {
                var channel = channels.get (ch);
                if (isNotEmpty (channel)) {
                    for (var ses: channel) {
                        if (ses.role == SseRole.Producer) {
                            // 频道里有至少一个生产者
                            available.add (ch);
                            break;
                        }
                    }
                }
            }

            if (isEmpty (available)) {
                throw new NoAvailableException ("No Available");
            }
            // 只保留校验通过的频道
            channelNames = available.toArray (new String[0]);
        }
        // 附着到指定/过滤过的频道上
        for (var channel : channelNames) {
            // 频道不存在时自动创建（生产者可以在运行期开新频道）
            channels.computeIfAbsent (channel, k -> new CopyOnWriteArraySet<> ())
                    .add (session);
        }
    }

    /**
     * {@link #attach(SseSession, String...)} 的集合重载。
     *
     * @param session      待挂载的会话
     * @param channelNames 目标频道集合
     * @throws NoAvailableException 订阅者且不允许动态附着时，没有任何可用频道
     */
    void attach (SseSession session, Set<String> channelNames) throws NoAvailableException {
        attach (session, channelNames.toArray (new String[0]));
    }

    /** 上一次打印频道/会话快照的时间戳，用于把 trace 日志限流为每 20s 一次。 */
    private long timestamp = 0;
    /**
     * 单线程执行
     *
     * <p>事件循环的主体：遍历所有频道的所有会话，把各自队列中的数据刷到客户端；
     * 写失败说明连接已不可用，直接关闭会话并摘除。同时以 20s 为周期打印一次频道/会话快照（trace 级别）。</p>
     */
    private void loop () {
        try {
            for (var entry : channels.entrySet ()) {
                for (SseSession session : entry.getValue ()) {
                    if (session.isClosed ()) continue;
                    try {
                        session.flush ();
                    } catch (IOException e) {
                        // 写失败（通常是客户端已断开），关闭并摘除该会话
                        session.close ();
                        remove (session);
                    }
                }
            }

            long now = System.currentTimeMillis ();
            if (now - timestamp >= 20000) {
                if (logger.isTraceEnabled ()) {
                    logger.trace ("---------------------- {} ----------------------", DateUtil.formatDateTime (new java.util.Date (now)));

                    // 20s 打印一次
                    for (var entry : channels.entrySet ()) {
                        logger.trace ("channel name: {}", entry.getKey ());
                        for (var session : entry.getValue ()) {
                            logger.trace ("\t{}", session);
                        }
                    }
                    timestamp = now;
                    logger.trace ("------------------------------------------------");
                }
            }
        } catch (Throwable t) {
            failure.set (t); // 防止吞异常
        }
    }

    /**
     * 心跳：向所有存活的会话发送一条 SSE 注释帧（{@code : ping}），以维持连接并尽早发现死连接。
     * <p>由事件循环每 {@code 15s} 调用一次。</p>
     */
    private void heartbeat () {
        for (var entry : channels.entrySet ()) {
            for (SseSession s : entry.getValue ()) {
                if (s.isClosed ()) continue;
                try {
                    s.ping ();
                } catch (IOException e) {
                    // 心跳都写不出去，说明连接已失效
                    s.close ();
                    remove (s);
                }
            }
        }
    }

    /**
     * 把会话从它所属的所有频道中摘除。
     *
     * <p>对每个频道分两种情况处理，最终都会把频道条目从表中删除（避免残留空频道）：</p>
     * <ul>
     *   <li>摘除后频道已为空 —— 直接删除该频道条目；</li>
     *   <li>频道仍非空但<b>已不存在任何生产者</b> —— 先关闭该频道内剩余的所有会话，
     *       再清空集合并删除频道条目，因为「没有生产者的频道」对订阅者已无意义。</li>
     * </ul>
     *
     * @param s 待摘除的会话
     */
    private void remove (SseSession s) {
        Set<String> keys = new HashSet<> ();
        for (String ch : s.channels ()) {
            Set<SseSession> set = channels.get (ch);
            if (set != null) set.remove (s);
            if (isEmpty (set)) {
                // 整个频道都空了，直接删除频道
                keys.add (ch);
            } else {
                // 频道还非空，检查里面还有没有生产者
                boolean found = false;
                for (var session : set) {
                    if (session.role == SseRole.Producer) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    // 整个频道里没有生产者了，关闭频道内剩余的所有会话
                    for (var session : set) {
                        session.close ();
                    }
                    // 清空频道内容，并标记该频道待删除
                    set.clear ();
                    keys.add (ch);
                }
            }
        }
        if (isNotEmpty (keys)) {
            for (var key : keys) {
                channels.remove (key);
            }
        }
        if (logger.isTraceEnabled ()) {
            logger.trace ("session [{}] removed from channels: {}", s, keys);
        }
        keys.clear ();
    }

    /**
     * 异步请求监听器：连接完成 / 超时 / 出错时统一做收尾处理（关闭会话并摘除）。
     * <p>覆盖 Servlet 异步生命周期的各个回调，保证任何终止路径都能释放会话资源。</p>
     */
    private class SseAsyncListener implements jakarta.servlet.AsyncListener {
        private final SseSession session;

        SseAsyncListener (SseSession session) {
            this.session = session;
        }

        @Override
        public void onComplete (AsyncEvent event) {
            if (logger.isTraceEnabled ()) {
                logger.trace ("sse completed.");
            }
            cleanup ();
        }

        @Override
        public void onTimeout (AsyncEvent event) {
            if (logger.isTraceEnabled ()) {
                logger.trace ("sse timeout: event = {}", event);
            }
            cleanup ();
        }

        @Override
        public void onError (AsyncEvent event) {
            logger.error ("got an error: {}", event);
            // 打印底层异常（若有），便于定位连接异常原因
            if (event.getThrowable () != null) {
                var ex = event.getThrowable ();
                logger.error (ex.getMessage (), ex);
            }
            cleanup ();
        }

        @Override
        public void onStartAsync (AsyncEvent event) {
            // 本实现不重启异步周期，无需处理
        }

        /** 会话收尾：先关闭（触发 complete 事件并释放 Writer），再从频道中摘除。 */
        private void cleanup () {
            session.close ();
            remove (session);
        }
    }
}

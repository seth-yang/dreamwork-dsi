package org.dreamwork.dsi.embedded.httpd.support.sse.impl;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.dreamwork.dsi.embedded.httpd.support.sse.SseFrame;
import org.dreamwork.util.DateUtil;
import org.dreamwork.util.IDisposable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.dreamwork.util.CollectionHelper.isEmpty;
import static org.dreamwork.util.CollectionHelper.isNotEmpty;

public class SseHub implements IDisposable {
    private static final SseHub INSTANCE = new SseHub ();

    public static SseHub instance () {
        return INSTANCE;
    }

    private ConcurrentMap<String, CopyOnWriteArraySet<SseSession>> channels;
    private ConcurrentMap<String, Future<?>> futures;
    private ScheduledExecutorService eventLoop;
    private ExecutorService taskPool;

    private final AtomicReference<Throwable> failure = new AtomicReference<> ();
    private final AtomicBoolean initialed = new AtomicBoolean ();

    private final Logger logger = LoggerFactory.getLogger (SseHub.class);

    @PostConstruct
    public void init () {
        if (logger.isTraceEnabled ()) {
            logger.trace ("initialing the SSE Hub ...");
        }
        if (initialed.compareAndSet (false, true)) {
            channels = new ConcurrentHashMap<> ();
            eventLoop = Executors.newSingleThreadScheduledExecutor (r -> {
                Thread t = new Thread (r, "sse-event-loop");
                t.setDaemon (true);
                return t;
            });
            futures = new ConcurrentHashMap<> ();
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
        if (isNotEmpty (futures)) {
            for (var e : futures.entrySet ()) {
                e.getValue ().cancel (true);
            }
            futures.clear ();
        }
        logger.info ("sse hub destroyed");
    }

    /**
     * Servlet 里调用：注册一个连接
     */
    public SseSession register (HttpServletRequest req,
                                HttpServletResponse resp,
                                Set<String> channelNames) throws IOException {
        if (logger.isDebugEnabled ()) {
            logger.debug ("registering a new sse into {}", channelNames);
        }
        req.setAttribute ("org.apache.catalina.ASYNC_SUPPORTED", true);
        AsyncContext ac = req.startAsync (req, resp);

        resp.setContentType ("text/event-stream");
        resp.setCharacterEncoding ("UTF-8");
        resp.setHeader ("Cache-Control", "no-cache");
        resp.setHeader ("Connection", "keep-alive");
        resp.setHeader ("X-Accel-Buffering", "no");

        SseSession session = new SseSession (ac, channelNames);

        // 加入 channel
        attach (session, channelNames);
        ac.addListener (new SseAsyncListener (session, channelNames));
        ac.setTimeout (0);
        if (logger.isDebugEnabled ()) {
            logger.debug ("session registered.");
        }

        // 立即发送连接成功
        session.enqueue (": connected\n\n");
        return session;
    }

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
     */
    public void publish (String channel, SseFrame frame) {
        Set<SseSession> sessions = channels.get (channel);
        if (sessions == null) {
            logger.warn ("there's no session associated to channel: {}", channel);
            return;
        }
        String encoded = frame.toString ();
        for (SseSession s : sessions) {
            if (!s.isClosed ()) {
                s.enqueue (encoded);
            } else {
                logger.warn ("the session[channel={}] was closed.", channel);
            }
        }
    }

    public void commit (String key, Runnable runner) {
        Future<?> future = this.taskPool.submit (runner);
        futures.put (key, future);
    }

    public void killTask (String key) {
        if (isNotEmpty (futures)) {
            var future = futures.remove (key);
            if (future != null) {
                future.cancel (true);
            }
        }
    }

    void attach (SseSession session, String... channelNames) {
        for (String ch : channelNames) {
            channels.computeIfAbsent (ch, k -> new CopyOnWriteArraySet<> ())
                    .add (session);
        }
    }

    void attach (SseSession session, Set<String> channelNames) {
        attach (session, channelNames.toArray (new String[0]));
    }

    private long timestamp = 0;
    /**
     * 单线程执行
     */
    private void loop () {
        try {
            for (var entry : channels.entrySet ()) {
                for (SseSession session : entry.getValue ()) {
                    if (session.isClosed ()) continue;
                    try {
                        session.flush ();
                    } catch (IOException e) {
                        session.close ();
                        remove (session);
                    }
                }
            }

            long now = System.currentTimeMillis ();
            if (now - timestamp >= 20000) {
                logger.info ("---------------------- {} ----------------------", DateUtil.formatDateTime (new java.util.Date (now)));
                // 20s 打印一次
                for (var entry : channels.entrySet ()) {
                    logger.info ("channel name: {}", entry.getKey ());
                    for (var session : entry.getValue ()) {
                        logger.info ("\t{}", session);
                    }
                }
                timestamp = now;
                logger.info ("------------------------------------------------");
            }
        } catch (Throwable t) {
            failure.set (t); // 防止吞异常
        }
    }

    private void heartbeat () {
        for (var entry : channels.entrySet ()) {
            for (SseSession s : entry.getValue ()) {
                if (s.isClosed ()) continue;
                try {
                    s.ping ();
                } catch (IOException e) {
                    s.close ();
                    remove (s);
                }
            }
        }
    }

    private void remove (SseSession s) {
        Set<String> keys = new HashSet<> ();
        for (String ch : s.channels ()) {
            Set<SseSession> set = channels.get (ch);
            if (set != null) set.remove (s);
            if (isEmpty (set)) {
                keys.add (ch);
            }
        }
        if (isNotEmpty (keys)) {
            for (var key : keys) {
                channels.remove (key);
            }
        }
        logger.info ("session [{}] removed from channels: {}", s, keys);
        keys.clear ();
    }

    private class SseAsyncListener implements jakarta.servlet.AsyncListener {
        private final SseSession session;
        private final Set<String> channelNames;

        SseAsyncListener (SseSession session, Set<String> channelNames) {
            this.session = session;
            this.channelNames = channelNames;
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
            if (event.getThrowable () != null) {
                var ex = event.getThrowable ();
                logger.error (ex.getMessage (), ex);
            }
            cleanup ();
        }

        @Override
        public void onStartAsync (AsyncEvent event) {
        }

        private void cleanup () {
            session.close ();
            remove (session);
        }
    }
}
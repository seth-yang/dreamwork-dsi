package org.dreamwork.injection.impl;

import org.dreamwork.concurrent.broadcast.LocalBroadcaster;
import org.dreamwork.injection.IObjectContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class BroadcastServiceHook {
    private final Logger logger = LoggerFactory.getLogger (BroadcastServiceHook.class);

    // org.dreamwork.dsi.broadcast.workers
    public int workers = 8;

    // org.dreamwork.dsi.broadcast.enabled
    public boolean enabled;

    private ExecutorService executor;
    private LocalBroadcaster broadcaster;
    private Future<?> task;

    private final IObjectContext context;

    public BroadcastServiceHook (IObjectContext context) {
        this.context = context;
    }

    public void init () {
        if (!enabled) {
            logger.warn ("local broadcast server is not enabled.");
            return;
        }

        int corePoolSize = Math.max (workers / 2, 2);
        int max = Math.max (workers, 2);
        final AtomicInteger ai = new AtomicInteger (0);
        ThreadGroup group = new ThreadGroup ("broadcaster");
        executor = new ThreadPoolExecutor (
                corePoolSize,           // 核心线程数为max的一半，保证基本并发能力
                max,                    // 最大线程数
                60L, TimeUnit.SECONDS,  // 空闲线程存活时间
                new ArrayBlockingQueue<> (1024),  // 队列容量为max的2倍，提供更大缓冲
                r -> {
                    String name = String.format ("%s.worker#%02d", group.getName (), ai.getAndIncrement ());
                    Thread thread = new Thread (group, r, name);
                    thread.setDaemon (true);
                    return thread;
                },
                new ThreadPoolExecutor.CallerRunsPolicy ()  // 拒绝策略：调用者运行，实现背压机制防止丢包
        );

        broadcaster = new LocalBroadcaster (executor);
        task = executor.submit (broadcaster);
        try {
            context.register (broadcaster);
        } catch (Exception ex) {
            logger.error ("cannot init local broadcast service");
            logger.error (ex.getMessage (), ex);
            throw new RuntimeException (ex);
        }
    }

    public void destroy () {
        if (enabled) {
            if (executor != null) {
                executor.shutdownNow ();
            }

            if (task != null) {
                task.cancel (true);
            }

            if (broadcaster != null) {
                broadcaster.shutdownNow ();
            }
        }
    }
}

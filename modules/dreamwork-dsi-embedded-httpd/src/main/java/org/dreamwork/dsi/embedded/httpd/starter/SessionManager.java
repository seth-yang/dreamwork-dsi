package org.dreamwork.dsi.embedded.httpd.starter;

import org.dreamwork.concurrent.Looper;
import org.dreamwork.dsi.embedded.httpd.support.ManagedSession;
import org.dreamwork.injection.AConfigured;
import org.dreamwork.util.StringUtil;
import org.dreamwork.util.ThreadHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Resource
public class SessionManager {
    @AConfigured ("${dsi.embedded.httpd.session.timeout}")
    private long timeout = 30 * 60 * 1000;

    @AConfigured ("${dsi.embedded.httpd.managed.session.enabled}")
    private boolean enabled = true;

    private volatile boolean running = true;

    private final Object LOCKER = new byte[0];
    private final Logger logger = LoggerFactory.getLogger (SessionManager.class);
    private final String LOOP_NAME = "ManagedSession";

    private final Map<String, ManagedSession> session = new ConcurrentHashMap<> ();

    private final Lock locker = new ReentrantLock (false);
    private final Condition c = locker.newCondition ();

    @PostConstruct
    public void startMonitor () {
        if (!enabled) {
            logger.warn ("the managed session is not enabled");
        }
        if (logger.isTraceEnabled ()) {
            logger.trace ("session timeout = {} ms.", timeout);
            logger.trace ("starting the session check monitor");
        }
        Looper.create (LOOP_NAME, 1, 1);
        Looper.runInLoop (LOOP_NAME, this::mainLoop);
    }

    @PreDestroy
    public void stopMonitor () {
        if (logger.isTraceEnabled ()) {
            logger.trace ("trying to stop session manager");
        }
        running = false;
        synchronized (LOCKER) {
            LOCKER.notifyAll ();
        }
        try {
            locker.lockInterruptibly ();
            c.signalAll ();
        } catch (InterruptedException ex) {
            if (logger.isTraceEnabled ()) {
                logger.warn ("warn in pre-destroy");
                logger.warn (ex.getMessage (), ex);
            }
        } finally {
            locker.unlock ();
        }
    }

    public ManagedSession create (String key) {
        ManagedSession ms = StringUtil.isEmpty (key) ?
                new ManagedSession () : new ManagedSession (key);
        synchronized (LOCKER) {
            session.put (ms.id, ms);
            if (logger.isTraceEnabled ()) {
                logger.trace ("a new session created, it = {}", ms.id);
            }
            LOCKER.notifyAll ();
        }
        return ms;
    }

    public ManagedSession create () {
        return create (null);
    }

    public ManagedSession get (String key) {
        synchronized (LOCKER) {
            return session.get (key);
        }
    }

    public void remove (String key) {
        synchronized (LOCKER) {
            session.remove (key);
        }
    }

    private void mainLoop () {
        while (running) {
            try {
                locker.lockInterruptibly ();
                while (running && session.isEmpty ()) {
                    c.await ();
                    if (logger.isTraceEnabled ()) {
                        logger.trace ("session manager awake and running = {}", running);
                    }
                }
            } catch (InterruptedException ex) {
                if (logger.isTraceEnabled ()) {
                    logger.warn (ex.getMessage (), ex);
                }
            } finally {
                locker.unlock ();
            }

            if (running) {  // double check
                Map<String, ManagedSession> copied;
                synchronized (LOCKER) {
                    copied = new HashMap<> (session);
                }
                if (!copied.isEmpty ()) {
                    Map<String, ManagedSession> temp = new HashMap<> ();
                    long now = System.currentTimeMillis ();
                    for (Map.Entry<String, ManagedSession> e : copied.entrySet ()) {
                        ManagedSession item = e.getValue ();
                        if (now - item.timestamp > timeout) {
                            temp.put (e.getKey (), item);
                        }
                    }

                    if (!temp.isEmpty ()) {
                        synchronized (LOCKER) {
                            for (Map.Entry<String, ManagedSession> e : temp.entrySet ()) {
                                ManagedSession item = e.getValue ();
                                if (now - item.timestamp > timeout) {
                                    session.remove (e.getKey ());
                                    if (logger.isTraceEnabled ()) {
                                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat ("yyyy-MM-dd HH:mm:ss.SSS");
                                        logger.trace ("the session:{id={}, create at: {}, now = {}} has expired, clear it success",
                                                e.getKey (), sdf.format (item.timestamp), sdf.format (now));
                                    }
                                }
                            }
                        }
                    }
                }

                ThreadHelper.delay (2000);
            }
        }
        if (logger.isTraceEnabled ()) {
            logger.trace ("the session monitor stopped.");
        }
        Looper.destory (LOOP_NAME);
    }
}
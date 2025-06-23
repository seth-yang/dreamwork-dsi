package org.dreamwork.dsi.embedded.httpd.starter;

import org.dreamwork.concurrent.Looper;
import org.dreamwork.dsi.embedded.httpd.support.websocket.AWebSocket;
import org.dreamwork.dsi.embedded.httpd.support.websocket.AbstractWebSocket;
import org.dreamwork.dsi.embedded.httpd.support.websocket.IWebSocketExecutor;
import org.dreamwork.dsi.embedded.httpd.support.websocket.IWebsocketCommand;
import org.dreamwork.injection.IObjectContext;
import org.dreamwork.util.ReferenceUtil;
import org.dreamwork.util.ThreadHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by game on 2017/2/16
 */
@Resource
public class WebSocketManager {
    private static final String LOOP_HEARTBEAT_NAME = "dsi.ws.heartbeat";
    private static final String LOOP_SENDER_NAME = "dsi.ws.sender";
    private static final String HEARTBEAT = "{\"action\":\"heartbeat\"}";
    private static final Set<Class<? extends AbstractWebSocket<?>>> messageCacheableSockets = new HashSet<> ();

    private static WebSocketManager instance;

    @SuppressWarnings ("unchecked")
    public static void addService (AbstractWebSocket<?> socket) {
        Class<? extends AbstractWebSocket<?>> type = (Class<? extends AbstractWebSocket<?>>) socket.getClass ();
        instance.addWebSocket (type, socket);
    }

    @SuppressWarnings ("unchecked")
    public static void removeService (AbstractWebSocket<?> socket) {
        instance.removeWebSocket ((Class<? extends AbstractWebSocket<?>>) socket.getClass (), socket);
    }

    public static<T extends IWebsocketCommand> void update (Class<? extends AbstractWebSocket<T>> type, String id, T message) {
        if (message == null) {
            throw new NullPointerException ("message is null");
        }

        instance.notify (type, id, message);
    }

    public static<T extends IWebsocketCommand> void update (Class<? extends AbstractWebSocket<T>> type, T message) {
        if (message == null) {
            throw new NullPointerException ("message is null");
        }

        instance.notify (type,  null, message);
    }

    public static void enableCache (Class<? extends AbstractWebSocket<?>> type, long timeout) {
        if (type != null && timeout > 0) {
            if (instance != null) {
                instance.enableMessageCache (type, timeout);
            } else {
                messageCacheableSockets.add (type);
            }
        }
    }

    public static void disableCache (Class<? extends AbstractWebSocket<?>> type) {
        if (type != null) {
            if (instance != null) {
                instance.disableMessageCache (type);
            }
        }
    }

    public static<T extends IWebsocketCommand> List<T> cachedMessage (Class<? extends AbstractWebSocket<T>> type, String id) {
        return instance.getCachedMessage (type, id);
    }

    /////////////////////// instance fields ////////////////////////////////
    private transient boolean running = true;
    private final Object LOCKER = new byte[0], SENDER_LOCKER = new byte[0];
    /** 缓存的 websocket 实例 */
    private final Map<Class<? extends AbstractWebSocket<?>>, Set<WebsocketWrapper>> cache = new ConcurrentHashMap<> ();
    /** 缓存的消息 */
    private final Map<Class<? extends AbstractWebSocket<?>>, CacheInfo> messageCache = new HashMap<> ();
    /** 待发送的消息 */
    private final Map<Class<? extends AbstractWebSocket<?>>, List<MessageWrapper>> messages = new HashMap<> ();
    private final Logger logger = LoggerFactory.getLogger (WebSocketManager.class);

    @Resource
    private IObjectContext context;

    /////////////////////// instance methods ////////////////////////////////
    public WebSocketManager () {
        synchronized (WebSocketManager.class) {
            if (instance == null)
                WebSocketManager.instance = this;
            else
                throw new IllegalStateException ("the web socket manager has been initialed.");
        }
    }

    @PostConstruct
    public void init () {
        // 激活已经注册的消息可缓存的 websocket
        if (!messageCacheableSockets.isEmpty ()) {
            messageCacheableSockets.forEach (c -> {
                AWebSocket ws = c.getAnnotation (AWebSocket.class);
                enableCache (c, ws.messageCacheTimeout ());
            });

            messageCacheableSockets.clear ();
        }

        // 心跳维护线程
        Looper.create (LOOP_HEARTBEAT_NAME, 1, 1);
        Looper.runInLoop (LOOP_HEARTBEAT_NAME, () -> {
            while (running) {
                Set<Set<WebsocketWrapper>> set = new HashSet<> (cache.values ());
                Set<WebsocketWrapper> sockets = new HashSet<> ();
                set.forEach (sockets::addAll);
                final long now = System.currentTimeMillis ();
                sockets.stream().filter (w -> w.timeout != null && now - w.timestamp > w.timeout).forEach (w -> {
                    try {
                        w.socket.getSession ().getBasicRemote ().sendText (HEARTBEAT);
                    } catch (IOException ex) {
                        logger.warn (ex.getMessage (), ex);
                    } finally {
                        w.timestamp = now;
                    }
                });

                ThreadHelper.delay (10);
            }
        });

        // 发送线程
        Looper.create (LOOP_SENDER_NAME, 64, 1);
        Looper.runInLoop (LOOP_SENDER_NAME, () -> {
            List<WebSocketManager.MessageWrapper> copy = new ArrayList<> ();
            Set<AbstractWebSocket<IWebsocketCommand>> sockets = new HashSet<> ();
            while (running) {
                // 复制需要发送的消息集合
                synchronized (messages) {
                    messages.forEach ((key, list) -> {
                        @SuppressWarnings ("unchecked")
                        Class<? extends AbstractWebSocket<IWebsocketCommand>> type = (Class<? extends AbstractWebSocket<IWebsocketCommand>>) key;
                        sockets.addAll (getSockets (type));
                        synchronized (SENDER_LOCKER) {
                            if (!list.isEmpty ()) {
                                copy.addAll (list);
                                list.clear ();
                            }
                        }
                    });
                }
                if (!copy.isEmpty ()) {
                    // 发送消息
                    try {
                        sockets.forEach (socket -> {
                            for (MessageWrapper wrapper : copy) {
                                socket.send (wrapper.message);
                            }
                        });
                    } finally {
                        sockets.clear ();
                        copy.clear ();
                    }
                }

                ThreadHelper.delay (10);
            }
        });
    }

    @PreDestroy
    public void destroy () {
        running = false;
        synchronized (LOCKER) {
            LOCKER.notifyAll ();
        }
        Looper.destory (LOOP_HEARTBEAT_NAME);
    }

    synchronized private void addWebSocket (Class<? extends AbstractWebSocket<?>> type, AbstractWebSocket<?> socket) {
        if (logger.isDebugEnabled ()) {
            logger.debug ("adding websocket: {}", socket);
        }

        Set<WebsocketWrapper> set = cache.computeIfAbsent (type, key -> new HashSet<> ());
        AWebSocket ws = type.getAnnotation (AWebSocket.class);
        set.add (new WebsocketWrapper (socket, ws.heartbeat ()));
        // 注入容器
        socket.setContext (context);
        // 注入管理器实例
        socket.setWebsocketManager (instance);
        // 自动注入其他字段
        Collection<Field> fields = ReferenceUtil.getFields (type);
        fields.forEach (field -> {
            if (field.isAnnotationPresent (Resource.class)) {
                Object o = context.getBean (field.getType ());
                if (!field.isAccessible ()) {
                    field.setAccessible (true);
                }
                try {
                    field.set (socket, o);
                } catch (Exception ex) {
                    logger.warn (ex.getMessage (), ex);
                }
            }
        });
    }

    synchronized private void removeWebSocket (Class<? extends AbstractWebSocket<?>> type, AbstractWebSocket<?> socket) {
        if (!cache.containsKey (type)) {
            return;
        }

        Set<WebsocketWrapper> set = cache.get (type);
        WebsocketWrapper wrapper = null;
        for (WebsocketWrapper w : set) {
            if (w.socket == socket) {
                wrapper = w;
                break;
            }
        }
        if (wrapper != null) {
            set.remove (wrapper);
            if (logger.isDebugEnabled ()) {
                logger.debug ("websocket {} removed", socket);
            }
        }
    }

    /**
     * 获取指定类型的所有已经被缓存的 {@link IWebSocketExecutor} 实例
     * @param type websocket的类型
     * @return 所有指定类型的 websocket 实例的集合
     * @param <T> ws的命令类型
     */
    @SuppressWarnings ("unchecked")
    synchronized private<T extends IWebsocketCommand> Set<AbstractWebSocket<T>> getSockets (Class<? extends AbstractWebSocket<T>> type) {
        Set<WebsocketWrapper> wrappers = cache.get (type);
        if (wrappers == null)
            return Collections.emptySet ();
        Set<AbstractWebSocket<T>> copied = new HashSet<> (wrappers.size ());
        for (WebsocketWrapper w : wrappers) {
            copied.add ((AbstractWebSocket<T>) w.socket);
        }
        return copied;
    }

    private<T extends IWebsocketCommand> void notify (Class<? extends AbstractWebSocket<T>> type, String id, T message) {
        Set<AbstractWebSocket<T>> sockets = getSockets (type);
        if (sockets.isEmpty ()) {
            // 这个类型的websocket还没被缓存，看看消息是否可缓存
            synchronized (messageCache) {
                if (messageCache.containsKey (type)) {
                    cacheMessage (type, id, message);
                }
            }
        } else {
            sockets.forEach (socket -> {
                if (socket.matches (id, message)) {
                    synchronized (messages) {
                        List<MessageWrapper> list = messages.computeIfAbsent (type, key -> new ArrayList<> ());
                        MessageWrapper wrapper = new MessageWrapper ();
                        wrapper.id = id;
                        wrapper.message = message;
                        list.add (wrapper);
                    }
                }
            });
        }
    }

    private void enableMessageCache (Class<? extends AbstractWebSocket<?>> type, long timeout) {
        synchronized (messageCache) {
            messageCache.computeIfAbsent (type, key -> new CacheInfo (timeout));
        }
    }

    private void disableMessageCache (Class<? extends AbstractWebSocket<? extends IWebsocketCommand>> type) {
        synchronized (messageCache) {
            CacheInfo info = messageCache.get (type);
            if (info != null) {
                info.messages.clear ();
            }

            messageCache.remove (type);
        }
    }

    private<T extends IWebsocketCommand> void cacheMessage (Class<? extends AbstractWebSocket<T>> type, String id, T message) {
        synchronized (messageCache) {
            CacheInfo info = messageCache.get (type);
            if (info != null) {
                MessageWrapper wrapper = new MessageWrapper ();
                wrapper.timeout = System.currentTimeMillis () + info.timeout;
                wrapper.id = id;
                wrapper.message = message;
                info.messages.add (wrapper);
            }
        }
    }

    @SuppressWarnings ("unchecked")
    private<T extends IWebsocketCommand> List<T> getCachedMessage (Class<? extends AbstractWebSocket<T>> type, String id) {
        synchronized (messageCache) {
            CacheInfo info = messageCache.get (type);
            if (info != null) {
                List<T> copy = new ArrayList<> ();
                List<MessageWrapper> toDelete = new LinkedList<> ();
                for (MessageWrapper mw : info.messages) {
                    if (mw.id.equals (id)) {
                        copy.add ((T) mw.message);
                        toDelete.add (mw);
                    }
                }

                if (!toDelete.isEmpty ()) {
                    info.messages.removeAll (toDelete);
                }
                return copy;
            }

            return Collections.emptyList ();
        }
    }

    private static final class MessageWrapper {
        long timeout;
        String id;
        IWebsocketCommand message;
    }

    private static final class CacheInfo {
        long timeout;
        final List<MessageWrapper> messages = new LinkedList<> ();

        public CacheInfo (long timeout) {
            this.timeout = timeout;
        }
    }

    private static final class WebsocketWrapper {
        long timestamp;
        Long timeout;
        IWebSocketExecutor<?> socket;

        public WebsocketWrapper (IWebSocketExecutor<?> socket, Long timeout) {
            this.socket  = socket;
            this.timeout = timeout;
            timestamp    = System.currentTimeMillis ();
        }
    }
}
package org.dreamwork.dsi.embedded.httpd.starter;

import com.google.gson.Gson;
import org.dreamwork.concurrent.Looper;
import org.dreamwork.dsi.embedded.httpd.annotation.AWebSocket;
import org.dreamwork.dsi.embedded.httpd.support.websocket.AbstractWebSocket;
import org.dreamwork.dsi.embedded.httpd.support.websocket.IWebSocketExecutor;
import org.dreamwork.dsi.embedded.httpd.support.websocket.IWebsocketCommand;
import org.dreamwork.injection.AConfigured;
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
 * Websocket 管理器
 * <p>
 *
 * </p>
 *
 * @since 2.1.0
 */
@Resource
public class WebSocketManager {
    private static final String LOOP_HEARTBEAT_NAME = "dsi.ws.heartbeat";
    private static final String LOOP_SENDER_NAME = "dsi.ws.sender";
    private static final String HEARTBEAT = "{\"action\":\"heartbeat\"}";

    private static WebSocketManager instance;

    /**
     * 向管理器内添加一个 websocket 实例
     * @param socket websocket 实例
     * @param <T> 消息类型
     */
    @SuppressWarnings ("unchecked")
    public static<T extends IWebsocketCommand> void addService (AbstractWebSocket<T> socket) {
        Class<? extends AbstractWebSocket<T>> type = (Class<? extends AbstractWebSocket<T>>) socket.getClass ();
        instance.addWebSocket (type, socket);
    }

    /**
     * 从管理器内删除一个 websocket 实例
     * @param socket websocket 实例
     */
    @SuppressWarnings ("unchecked")
    public static void removeService (AbstractWebSocket<? extends IWebsocketCommand> socket) {
        instance.removeWebSocket (
                (Class<? extends AbstractWebSocket<IWebsocketCommand>>) socket.getClass (),
                (AbstractWebSocket<IWebsocketCommand>) socket
        );
    }

    /**
     * 通知指定类型的 websocket 处理一个指定 id 的消息
     * @param type    websocket 类型
     * @param id      websocket 实例用于判断是否处理该消息的唯一标识
     * @param message 消息
     * @param <T>     消息类型
     */
    public static<T extends IWebsocketCommand> void update (Class<? extends AbstractWebSocket<T>> type, String id, T message) {
        if (message == null) {
            throw new NullPointerException ("message is null");
        }

        instance.notify (type, id, message);
    }

    /**
     * 通知指定类型的 websocket 处理一个消息
     * @param type    websocket 类型
     * @param message 消息
     * @param <T>     消息类型
     */
    public static<T extends IWebsocketCommand> void update (Class<? extends AbstractWebSocket<T>> type, T message) {
        if (message == null) {
            throw new NullPointerException ("message is null");
        }

        instance.notify (type,  null, message);
    }

    /**
     * 获取所有正在连接的 websocket 实例集合
     * @return 正在连接的 websocket 实例集合
     */
    public static Set<WebsocketWrapper<?>> getAllWebsockets () {
        return instance.getAllClients ();
    }

    /**
     * 在控制台显示当前已缓存的消息，主要用于调试
     */
    public static void showCache () {
        Gson g = new Gson ();
        System.out.println ("messages: {");
        instance.messages.forEach ((key, list) -> {
            System.out.printf ("    %s: [%n", key);
            list.forEach (w -> {
                System.out.printf ("        - { %s }%n", g.toJson (w.message));
            });
            System.out.println ("    ]");
        });
        System.out.println ("}");
    }

    /////////////////////// instance fields ////////////////////////////////
    private transient boolean running = true;
    private final Object LOCKER = new byte[0], SENDER_LOCKER = new byte[0];
    /** 缓存的 websocket 实例 */
    private final Map<Class<? extends AbstractWebSocket<? extends IWebsocketCommand>>, Set<WebsocketWrapper<? extends IWebsocketCommand>>> cache = new ConcurrentHashMap<> ();
    /** 待发送的消息 */
    private final Map<Class<? extends AbstractWebSocket<?>>, List<MessageWrapper<IWebsocketCommand>>> messages = new HashMap<> ();
    private final Logger logger = LoggerFactory.getLogger (WebSocketManager.class);

    @AConfigured ("${embedded.httpd.websocket.enabled}")
    private boolean enabled = true;

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
        if (!enabled) {
            logger.warn ("the websocket supported is not enabled, nothing to do.");
            return;
        }
        if (logger.isTraceEnabled ()) {
            logger.trace ("starting the websocket manager");
        }

        // 心跳维护线程
        Looper.create (LOOP_HEARTBEAT_NAME, 1, 1);
        Looper.runInLoop (LOOP_HEARTBEAT_NAME, () -> {
            while (running) {
                Set<Set<WebsocketWrapper<? extends IWebsocketCommand>>> set = new HashSet<> (cache.values ());
                Set<WebsocketWrapper<? extends IWebsocketCommand>> sockets = new HashSet<> ();
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
            logger.info ("the heartbeat loop complete.");
            Looper.destory (LOOP_HEARTBEAT_NAME);
        });

        // 发送线程
        Looper.create (LOOP_SENDER_NAME, 64, 1);
        Looper.runInLoop (LOOP_SENDER_NAME, () -> {
            while (running) {
                List<MessageWrapper<IWebsocketCommand>> copy = new ArrayList<> ();
                Set<AbstractWebSocket<IWebsocketCommand>> sockets = new HashSet<> ();
                // 复制需要发送的消息集合
                synchronized (messages) {
                    messages.forEach ((key, list) -> {
                        if (list != null && !list.isEmpty ()) {
                            @SuppressWarnings ("unchecked")
                            Class<? extends AbstractWebSocket<IWebsocketCommand>> type =
                                    (Class<? extends AbstractWebSocket<IWebsocketCommand>>) key;
                            // 获取匹配类型的消息集合
                            sockets.addAll (getSockets (type));
                            synchronized (SENDER_LOCKER) {
                                if (!list.isEmpty ()) {
                                    copy.addAll (list);
                                    list.clear ();
                                }
                            }

                            if (!copy.isEmpty ()) {
                                // 发送消息
                                try {
                                    sockets.forEach (socket -> {
                                        for (MessageWrapper<IWebsocketCommand> wrapper : copy) {
                                            if (socket.matches (wrapper.id, wrapper.message)) {
                                                socket.send (wrapper.message);
                                            }
                                        }
                                    });
                                } finally {
                                    sockets.clear ();
                                    copy.clear ();
                                }
                            }
                        }
                    });
                }

                ThreadHelper.delay (10);
            }

            logger.info ("the sender loop complete.");
            Looper.destory (LOOP_SENDER_NAME);
        });
    }

    @PreDestroy
    public void destroy () {
        running = false;
        synchronized (LOCKER) {
            LOCKER.notifyAll ();
        }
    }

    /**
     * 添加一个 websocket 实例到管理器
     * @param type   websocket 的类型
     * @param socket websocket 实例
     * @param <T> 消息类型
     */
    @SuppressWarnings ("unchecked")
    synchronized private<T extends IWebsocketCommand> void addWebSocket (Class<? extends AbstractWebSocket<T>> type, AbstractWebSocket<T> socket) {
        if (logger.isDebugEnabled ()) {
            logger.debug ("adding websocket: {}", socket);
        }

        Set<WebsocketWrapper<? extends IWebsocketCommand>> set = cache.computeIfAbsent (type, key -> new HashSet<> ());
        AWebSocket ws = type.getAnnotation (AWebSocket.class);
        long time = ws.heartbeat ();
        Long timeout = time > 0 ? time : null;
        set.add (new WebsocketWrapper<> ((AbstractWebSocket<IWebsocketCommand>) socket, timeout));
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

    /**
     * 从管理器中删除一个 websocket 实例
     * @param type   websocket 的类型
     * @param socket websocket 实例
     */
    synchronized private void removeWebSocket (Class<? extends AbstractWebSocket<IWebsocketCommand>> type, AbstractWebSocket<IWebsocketCommand> socket) {
        if (!cache.containsKey (type)) {
            return;
        }
        Set<WebsocketWrapper<? extends IWebsocketCommand>> set = cache.get (type);
        WebsocketWrapper<? extends IWebsocketCommand> wrapper = null;
        for (WebsocketWrapper<? extends IWebsocketCommand> w : set) {
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
        Set<WebsocketWrapper<? extends IWebsocketCommand>> wrappers = cache.get (type);
        if (wrappers == null)
            return Collections.emptySet ();
        Set<AbstractWebSocket<T>> copied = new HashSet<> (wrappers.size ());
        for (WebsocketWrapper<? extends IWebsocketCommand> w : wrappers) {
            copied.add ((AbstractWebSocket<T>) w.socket);
        }
        return copied;
    }

    /**
     * 对指定类型的 websocket 发起一次通知
     * @param type    websocket的具体类型
     * @param id      websocket实例用于判断是否该接收该消息的唯一标识
     * @param message 需下行的消息
     * @param <T>     消息类型
     */
    @SuppressWarnings ("unchecked")
    synchronized private<T extends IWebsocketCommand> void notify (Class<? extends AbstractWebSocket<T>> type, String id, T message) {
        if (logger.isTraceEnabled ()) {
            logger.trace ("sending message: {} with id[{}] to {}", message, id, type);
        }
        if (cache.containsKey (type)) { // 当且仅当有这种类型的websocket实例被缓存才需要通知
            List<MessageWrapper<IWebsocketCommand>> list = messages.computeIfAbsent (type, key -> new ArrayList<> ());
            MessageWrapper<T> wrapper = new MessageWrapper<> ();
            wrapper.id = id;
            wrapper.message = message;
            list.add ((MessageWrapper<IWebsocketCommand>) wrapper);
            if (logger.isTraceEnabled ()) {
                logger.trace ("message save to type: {}", type);
            }
        } else if (logger.isTraceEnabled ()) {
            logger.trace ("there's no websocket instance with type: {}", type);
        }
    }

    /**
     * 获取当前所有正在连接的 websocket
     * @return 所有正在连接的 websocket 实例
     */
    synchronized private Set<WebsocketWrapper<?>> getAllClients () {
        Set<WebsocketWrapper<?>> set = new HashSet<> ();
        cache.values ().forEach (set::addAll);
        return set;
    }

    private static final class MessageWrapper<T extends IWebsocketCommand> {
        long timeout;
        String id;
        T message;
    }

    public static final class WebsocketWrapper<T extends IWebsocketCommand> {
        public long timestamp;
        public Long timeout;
        public IWebSocketExecutor<T> socket;

        public WebsocketWrapper (AbstractWebSocket<T> socket, Long timeout) {
            this.socket  = socket;
            this.timeout = timeout;
            timestamp    = System.currentTimeMillis ();
        }
    }
}
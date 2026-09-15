package org.dreamwork.dsi.embedded.httpd.support;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Servlet API 的动态代理桩件。
 *
 * <p>测试 SSE 相关逻辑时只需要 {@code Writer}、响应头以及 {@code AsyncContext} 的调用记录，
 * 因此这里用动态代理构造轻量的桩对象，避免引入额外的 mock 依赖。</p>
 */
public final class ServletMocks {
    private ServletMocks () {}

    /** 记录响应写入内容的桩件。 */
    public static class ResponseState {
        public final StringWriter buffer = new StringWriter ();
        public final PrintWriter writer = new PrintWriter (buffer, true);
        public final Map<String, String> headers = new LinkedHashMap<> ();
        public String contentType, characterEncoding;
        public int status = -1;

        public String text () {
            return buffer.toString ();
        }
    }

    /** 记录异步上下文调用的桩件。 */
    public static class AsyncState {
        public boolean completed;
        public long timeout = -1;
        public final List<AsyncListener> listeners = new ArrayList<> ();
    }

    /** 记录请求属性与异步上下文的桩件。 */
    public static class RequestState {
        public final Map<String, Object> attributes = new LinkedHashMap<> ();
        public AsyncContext asyncContext;
    }

    public static HttpServletResponse response (ResponseState state) {
        return mock (HttpServletResponse.class, (proxy, method, args) -> {
            switch (method.getName ()) {
                case "getWriter":
                    return state.writer;
                case "setContentType":
                    state.contentType = (String) args[0];
                    return null;
                case "setCharacterEncoding":
                    state.characterEncoding = (String) args[0];
                    return null;
                case "setHeader":
                    state.headers.put ((String) args[0], (String) args[1]);
                    return null;
                case "setStatus":
                    state.status = (Integer) args[0];
                    return null;
                default:
                    return null;
            }
        });
    }

    public static AsyncContext asyncContext (HttpServletResponse response, AsyncState state) {
        return mock (AsyncContext.class, (proxy, method, args) -> {
            switch (method.getName ()) {
                case "getResponse":
                    return response;
                case "complete":
                    state.completed = true;
                    return null;
                case "setTimeout":
                    state.timeout = (Long) args[0];
                    return null;
                case "getTimeout":
                    return state.timeout;
                case "addListener":
                    state.listeners.add ((AsyncListener) args[0]);
                    return null;
                default:
                    return null;
            }
        });
    }

    public static HttpServletRequest request (RequestState state) {
        return mock (HttpServletRequest.class, (proxy, method, args) -> {
            switch (method.getName ()) {
                case "setAttribute":
                    state.attributes.put ((String) args[0], args[1]);
                    return null;
                case "getAttribute":
                    return state.attributes.get ((String) args[0]);
                case "startAsync":
                    return state.asyncContext;
                default:
                    return null;
            }
        });
    }

    @SuppressWarnings ("unchecked")
    private static <T> T mock (Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance (
                ServletMocks.class.getClassLoader (),
                new Class<?>[] {type},
                (proxy, method, args) -> {
                    switch (method.getName ()) {
                        case "hashCode":
                            return System.identityHashCode (proxy);
                        case "equals":
                            return proxy == args[0];
                        case "toString":
                            return "mock:" + type.getSimpleName ();
                        default:
                            return handler.invoke (proxy, method, args);
                    }
                }
        );
    }

    private interface Handler {
        Object invoke (Object proxy, Method method, Object[] args) throws Throwable;
    }
}

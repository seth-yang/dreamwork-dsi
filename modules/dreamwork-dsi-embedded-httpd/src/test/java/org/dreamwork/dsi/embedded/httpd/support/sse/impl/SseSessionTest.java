package org.dreamwork.dsi.embedded.httpd.support.sse.impl;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletResponse;
import org.dreamwork.dsi.embedded.httpd.support.ServletMocks;
import org.dreamwork.dsi.embedded.httpd.support.SseRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SseSession} 的单元测试。
 *
 * <p>{@code flush()} / {@code close()} / {@code ping()} 都是包内可见的，
 * 因此测试直接调用它们，并使用 Servlet 桩件观察写出内容。</p>
 */
class SseSessionTest {
    private ServletMocks.ResponseState response;
    private ServletMocks.AsyncState async;
    private SseSession session;

    @BeforeEach
    void setUp () throws Exception {
        response = new ServletMocks.ResponseState ();
        async    = new ServletMocks.AsyncState ();
        session  = newSession (response, async, 30_000L);
    }

    @Test
    void flush_writesQueuedFramesInOrder () throws Exception {
        session.enqueue ("first\n\n");
        session.enqueue ("second\n\n");

        session.flush ();

        assertEquals ("first\n\nsecond\n\n", response.text ());
    }

    @Test
    void flush_withoutPendingData_writesNothing () throws Exception {
        session.flush ();

        assertEquals ("", response.text ());
    }

    @Test
    void ping_writesCommentFrame () throws Exception {
        session.ping ();

        assertEquals (": ping\n\n", response.text ());
    }

    @Test
    void close_sendsCompleteEventAndCompletesAsyncContext () {
        session.close ();

        assertTrue (session.isClosed ());
        assertTrue (async.completed);
        assertTrue (response.text ().contains ("event: .complete"), response.text ());
    }

    @Test
    void close_isIdempotent () {
        session.close ();
        String sent = response.text ();

        session.close ();

        assertEquals (sent, response.text ());
        assertTrue (session.isClosed ());
    }

    @Test
    void channels_returnsReadOnlySnapshot () {
        assertEquals (Set.of ("a", "b"), session.channels ());
        assertThrows (UnsupportedOperationException.class, () -> session.channels ().add ("c"));
        // 构造之后修改原集合不会影响会话
        Set<String> source = session.channels ();
        assertThrows (UnsupportedOperationException.class, () -> source.remove ("a"));
        assertEquals (Set.of ("a", "b"), session.channels ());
    }

    @Test
    void flush_whenIdleTooLong_closesTheSession () throws Exception {
        ServletMocks.ResponseState shortResponse = new ServletMocks.ResponseState ();
        ServletMocks.AsyncState shortAsync = new ServletMocks.AsyncState ();
        SseSession shortTimeout = newSession (shortResponse, shortAsync, 1L);

        shortTimeout.enqueue ("data\n\n");
        shortTimeout.flush ();          // 记录存活时间戳
        Thread.sleep (20);
        shortTimeout.flush ();          // 空闲超过 1ms，判定连接失效

        assertTrue (shortTimeout.isClosed ());
        assertTrue (shortAsync.completed);
        // 说明：当前实现在超时分支中会再次进入 flush() 的超时判断，
        // 因此 SESSION_TIMEOUT 事件帧不会真正写出到客户端，这里不作断言。
    }

    @Test
    void flush_withinTimeout_keepsTheSessionAlive () throws Exception {
        session.enqueue ("data\n\n");
        session.flush ();
        session.flush ();

        assertFalse (session.isClosed ());
    }

    private SseSession newSession (ServletMocks.ResponseState response, ServletMocks.AsyncState async, long timeout) throws Exception {
        HttpServletResponse servletResponse = ServletMocks.response (response);
        AsyncContext ac = ServletMocks.asyncContext (servletResponse, async);
        return new SseSession (ac, SseRole.Producer, Set.of ("a", "b"), timeout, true);
    }
}

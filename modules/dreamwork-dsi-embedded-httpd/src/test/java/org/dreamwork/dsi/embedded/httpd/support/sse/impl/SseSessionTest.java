package org.dreamwork.dsi.embedded.httpd.support.sse.impl;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletResponse;
import org.dreamwork.dsi.embedded.httpd.support.sse.SseRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SseSession} 的单元测试。
 * <p>使用 Mockito 模拟 {@link AsyncContext}，用 {@link StringWriter} 捕获真实写出内容。</p>
 */
class SseSessionTest {
    private AsyncContext ac;
    private StringWriter buffer;

    @BeforeEach
    void setUp () throws Exception {
        ac = mock (AsyncContext.class);
        HttpServletResponse response = mock (HttpServletResponse.class);
        buffer = new StringWriter ();
        when (ac.getResponse ()).thenReturn (response);
        when (response.getWriter ()).thenReturn (new PrintWriter (buffer, true));
    }

    private SseSession newSession (SseRole role, Set<String> channels, long timeout) throws IOException {
        return new SseSession (ac, role, channels, timeout, true);
    }

    @Test
    void constructorCopiesChannelSet () throws Exception {
        Set<String> channels = new HashSet<> ();
        channels.add ("chat");
        SseSession session = newSession (SseRole.Producer, channels, 30000);

        channels.add ("mutated");
        // 外部修改不应影响会话内部的快照
        assertEquals (Set.of ("chat"), session.channels ());
        assertEquals (SseRole.Producer, session.role);
        assertTrue (session.allowDynamic);
        assertFalse (session.isClosed ());
    }

    @Test
    void flushWritesEnqueuedFrames () throws Exception {
        SseSession session = newSession (SseRole.Producer, Set.of ("chat"), 30000);
        session.enqueue ("data: hello\n\n");
        session.enqueue ("data: world\n\n");

        session.flush ();

        assertEquals ("data: hello\n\ndata: world\n\n", buffer.toString ());
    }

    @Test
    void flushWithEmptyQueueWritesNothing () throws Exception {
        SseSession session = newSession (SseRole.Producer, Set.of ("chat"), 30000);
        session.flush ();
        assertTrue (buffer.toString ().isEmpty ());
    }

    @Test
    void pingWritesHeartbeatComment () throws Exception {
        SseSession session = newSession (SseRole.Producer, Set.of ("chat"), 30000);
        session.ping ();
        assertEquals (": ping\n\n", buffer.toString ());
    }

    @Test
    void closeSendsCompleteEventAndCompletesAsyncContext () throws Exception {
        SseSession session = newSession (SseRole.Producer, Set.of ("chat"), 30000);
        session.enqueue ("data: last\n\n");

        session.close ();

        // 队列中的最后一帧和 .complete 事件都应被写出
        String output = buffer.toString ();
        assertTrue (output.contains ("data: last"));
        assertTrue (output.contains ("event: .complete"));
        assertTrue (session.isClosed ());
        verify (ac).complete ();
    }

    @Test
    void closeIsIdempotent () throws Exception {
        SseSession session = newSession (SseRole.Producer, Set.of ("chat"), 30000);
        session.close ();
        session.close ();

        verify (ac, times (1)).complete ();
        assertEquals (1, outputCount ("event: .complete"));
    }

    @Test
    void flushClosesSessionAfterIdleTimeout () throws Exception {
        SseSession session = newSession (SseRole.Producer, Set.of ("chat"), 0);
        // 第一次写出：设置存活时间戳
        session.enqueue ("data: x\n\n");
        session.flush ();
        assertFalse (session.isClosed ());

        Thread.sleep (50);
        // 超过 0ms 空闲超时后再次 flush，应触发超时分支并关闭会话
        session.flush ();

        assertTrue (session.isClosed ());
        verify (ac, atLeastOnce ()).complete ();
    }

    private int outputCount (String token) {
        int count = 0, index = 0;
        String output = buffer.toString ();
        while ((index = output.indexOf (token, index)) >= 0) {
            count++;
            index += token.length ();
        }
        return count;
    }
}

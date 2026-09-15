package org.dreamwork.dsi.embedded.httpd.support.sse.impl;

import org.dreamwork.dsi.embedded.httpd.support.sse.SseFrame;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ServerSideEventImpl} 的单元测试。
 */
class ServerSideEventImplTest {

    @Test
    void sendUsesOwnUuidAsPrivateChannel () {
        SseHub hub = mock (SseHub.class);
        ServerSideEventImpl sse = new ServerSideEventImpl ();
        sse.setHub (hub);

        SseFrame frame = new SseFrame.Builder ().event ("chat").data ("hi").build ();
        sse.send (frame);

        verify (hub).publish (sse.uuid, frame);
    }

    @Test
    void sendToNamedChannel () {
        SseHub hub = mock (SseHub.class);
        ServerSideEventImpl sse = new ServerSideEventImpl ();
        sse.setHub (hub);

        SseFrame frame = new SseFrame.Builder ().data ("hi").build ();
        sse.send ("chat", frame);

        verify (hub).publish ("chat", frame);
    }

    @Test
    void sendWithoutHubIsSilentlyIgnored () {
        ServerSideEventImpl sse = new ServerSideEventImpl ();
        assertDoesNotThrow (() -> sse.send (new SseFrame (null, "hi", null)));
        assertDoesNotThrow (() -> sse.send ("chat", new SseFrame (null, "hi", null)));
    }

    @Test
    void closeUnregistersSessionFromHub () {
        SseHub hub = mock (SseHub.class);
        SseSession session = mock (SseSession.class);
        ServerSideEventImpl sse = new ServerSideEventImpl ();
        sse.setHub (hub);
        sse.setSession (session);

        sse.close ();

        verify (hub).unregister (session);
    }

    @Test
    void closeWithErrorNotifiesClientThenUnregisters () {
        SseHub hub = mock (SseHub.class);
        SseSession session = mock (SseSession.class);
        ServerSideEventImpl sse = new ServerSideEventImpl ();
        sse.setHub (hub);
        sse.setSession (session);

        sse.closeWithError (new RuntimeException ("boom"));

        // 异常信息应被推送给客户端
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass (String.class);
        verify (session).enqueue (captor.capture ());
        assertTrue (captor.getValue ().contains ("event: exception"));
        assertTrue (captor.getValue ().contains ("boom"));
        // 随后注销会话
        verify (hub).unregister (session);
    }

    @Test
    void closeWithErrorWithoutSessionStillCloses () {
        SseHub hub = mock (SseHub.class);
        ServerSideEventImpl sse = new ServerSideEventImpl ();
        sse.setHub (hub);

        sse.closeWithError (new RuntimeException ("boom"));

        verify (hub).unregister (null);
    }

    @Test
    void attachDelegatesToHub () throws Exception {
        SseHub hub = mock (SseHub.class);
        SseSession session = mock (SseSession.class);
        when (session.isClosed ()).thenReturn (false);
        ServerSideEventImpl sse = new ServerSideEventImpl ();
        sse.setHub (hub);
        sse.setSession (session);

        sse.attach ("a", "b");

        verify (hub).attach (session, "a", "b");
    }

    @Test
    void attachSkippedWhenSessionClosed () throws Exception {
        SseHub hub = mock (SseHub.class);
        SseSession session = mock (SseSession.class);
        when (session.isClosed ()).thenReturn (true);
        ServerSideEventImpl sse = new ServerSideEventImpl ();
        sse.setHub (hub);
        sse.setSession (session);

        sse.attach ("a");

        verify (hub, never ()).attach (any (SseSession.class), any (String[].class));
    }

    @Test
    void attachWithoutHubIsSilentlyIgnored () {
        ServerSideEventImpl sse = new ServerSideEventImpl ();
        assertDoesNotThrow (() -> sse.attach ("a"));
    }
}

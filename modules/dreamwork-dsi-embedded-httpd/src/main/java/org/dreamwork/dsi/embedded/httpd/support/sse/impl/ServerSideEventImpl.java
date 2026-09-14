package org.dreamwork.dsi.embedded.httpd.support.sse.impl;

import org.dreamwork.dsi.embedded.httpd.support.sse.IServerSideEvent;
import org.dreamwork.dsi.embedded.httpd.support.sse.SseFrame;
import org.dreamwork.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerSideEventImpl implements IServerSideEvent {
    private final Logger logger = LoggerFactory.getLogger (ServerSideEventImpl.class);

    public final String uuid = StringUtil.uuid ();

    private SseSession session;
    private SseHub hub;

    public void setSession (SseSession session) {
        this.session = session;
    }

    public void setHub (SseHub hub) {
        this.hub = hub;
    }

    @Override
    public void send (SseFrame frame) {
        send (uuid, frame);
    }

    @Override
    public void send (String channel, SseFrame frame) {
        if (hub != null) {
            hub.publish (channel, frame);
        }
    }

    @Override
    public void close () {
        if (hub != null) {
            hub.unregister (session);
        }
    }

    @Override
    public void closeWithError (Throwable error) {
        if (error != null && session != null) {
            String message = error.getMessage ();
            logger.warn (message, error);

            SseFrame frame = new SseFrame.Builder ()
                    .id (StringUtil.uuid ())
                    .event ("exception")
                    .json (true)
                    .data (message).build ();
            session.enqueue (frame.toString ());
        }
        close ();
    }

    @Override
    public void attach (String... channels) {
        if (hub != null && session != null && !session.isClosed ()) {
            hub.attach (session, channels);

        }
    }
}

package org.dreamwork.dsi.embedded.httpd.support.sse.impl;

import jakarta.servlet.AsyncContext;
import org.dreamwork.dsi.embedded.httpd.support.sse.SseFrame;
import org.dreamwork.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class SseSession {
    private final Logger logger = LoggerFactory.getLogger (SseSession.class);
    private final AsyncContext ac;
    private final PrintWriter writer;
    private final Set<String> channels;
    private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<> ();
    private final AtomicBoolean closed = new AtomicBoolean ();

    SseSession (AsyncContext ac, Set<String> channels) throws IOException {
        this.ac = ac;
        this.writer = ac.getResponse ().getWriter ();
        this.channels = Set.copyOf (channels);
    }

    void enqueue (String frame) {
        queue.offer (frame);
    }

    /**
     * 由 EventLoop 调用
     */
    void flush () throws IOException {
        String frame;
        boolean written = false;
        while ((frame = queue.poll ()) != null) {
            if (logger.isDebugEnabled ()) {
                logger.debug ("writing {} to client", frame);
            }
            writer.write (frame);
            written = true;
        }
        if (written) writer.flush ();
    }

    boolean isClosed () {
        return closed.get ();
    }

    void close () {
        if (logger.isDebugEnabled ()) {
            logger.debug ("closing the session: {}", this);
        }

        if (closed.compareAndSet (false, true)) {
            try {
                if (logger.isDebugEnabled ()) {
                    logger.debug ("send the complete event to client ...");
                }

                var builder = new SseFrame.Builder ()
                        .id (StringUtil.uuid ())
                        .json (true)
                        .event (".complete");
                enqueue (builder.build ().toString ());
                if (logger.isDebugEnabled ()) {
                    logger.debug ("complete event push into the queue");
                }

                flush ();
                writer.close ();
            } catch (Exception ignored) {
                logger.warn (ignored.getMessage (), ignored);
            }
            ac.complete ();
        }
    }

    Set<String> channels () {
        return channels;
    }

    /**
     * 心跳
     */
    void ping () throws IOException {
        writer.write (": ping\n\n");
        writer.flush ();
    }
}

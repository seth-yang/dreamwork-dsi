package org.dreamwork.dsi.embedded.httpd.support.sse;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SseFrame} 的单元测试：验证 SSE 报文渲染格式。
 */
class SseFrameTest {

    @Test
    void toString_rendersAllAvailableLinesAndEndsWithBlankLine () {
        SseFrame frame = new SseFrame ("chat", "hello", "42");

        assertEquals ("id: 42\nevent: chat\ndata: hello\n\n", frame.toString ());
    }

    @Test
    void toString_skipsAbsentEvent () {
        SseFrame frame = new SseFrame (null, "hello", "42");

        assertEquals ("id: 42\ndata: hello\n\n", frame.toString ());
    }

    @Test
    void toString_skipsAbsentId () {
        SseFrame frame = new SseFrame ("chat", "hello", null);

        assertEquals ("event: chat\ndata: hello\n\n", frame.toString ());
    }

    @Test
    void toString_skipsEmptyData () {
        SseFrame frame = new SseFrame ("chat", "", "42");

        assertEquals ("id: 42\nevent: chat\n\n", frame.toString ());
    }

    @Test
    void toString_withOnlyEvent_isStillAValidFrame () {
        SseFrame frame = new SseFrame ("ping", null, null);

        assertEquals ("event: ping\n\n", frame.toString ());
    }

    @Test
    void builder_withoutJson_usesToString () {
        SseFrame frame = new SseFrame.Builder ()
                .id ("1")
                .event ("chat")
                .data (123)
                .build ();

        assertEquals ("id: 1\nevent: chat\ndata: 123\n\n", frame.toString ());
    }

    @Test
    void builder_withJson_serializesData () {
        Map<String, Object> data = new LinkedHashMap<> ();
        data.put ("user", "tom");
        data.put ("text", "hi");

        SseFrame frame = new SseFrame.Builder ()
                .id ("1")
                .event ("chat")
                .json (true)
                .data (data)
                .build ();

        // JSON 的字段顺序不作假设
        assertTrue (frame.data ().startsWith ("{"), frame.data ());
        assertTrue (frame.data ().contains ("\"user\":\"tom\""), frame.data ());
        assertTrue (frame.data ().contains ("\"text\":\"hi\""), frame.data ());
        assertTrue (frame.toString ().startsWith ("id: 1\nevent: chat\ndata: {"), frame.toString ());
        assertTrue (frame.toString ().endsWith ("\n\n"), frame.toString ());
    }

    @Test
    void builder_withoutData_producesHeaderOnlyFrame () {
        SseFrame frame = new SseFrame.Builder ().event ("empty").build ();

        assertNull (frame.data ());
        assertEquals ("event: empty\n\n", frame.toString ());
    }

    @Test
    void builder_jsonFlagWithoutData_isHarmless () {
        SseFrame frame = new SseFrame.Builder ().json (true).build ();

        assertNull (frame.data ());
        assertEquals ("\n", frame.toString ());
    }

    @Test
    void builderMethods_areChainable () {
        SseFrame.Builder builder = new SseFrame.Builder ();

        assertSame (builder, builder.id ("1"));
        assertSame (builder, builder.event ("e"));
        assertSame (builder, builder.data ("d"));
        assertSame (builder, builder.json (true));
    }
}

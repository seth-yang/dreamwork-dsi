package org.dreamwork.dsi.embedded.httpd.support.sse;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SseFrame} 的单元测试。
 */
class SseFrameTest {

    @Test
    void toStringRendersAllFields () {
        SseFrame frame = new SseFrame ("chat", "{\"user\":\"tom\"}", "42");
        assertEquals ("id: 42\nevent: chat\ndata: {\"user\":\"tom\"}\n\n", frame.toString ());
    }

    @Test
    void toStringOmitsEventWhenNull () {
        SseFrame frame = new SseFrame (null, "hello", "1");
        assertEquals ("id: 1\ndata: hello\n\n", frame.toString ());
    }

    @Test
    void toStringOmitsDataWhenNull () {
        SseFrame frame = new SseFrame ("chat", null, "1");
        assertEquals ("id: 1\nevent: chat\n\n", frame.toString ());
    }

    @Test
    void toStringOmitsDataWhenEmpty () {
        SseFrame frame = new SseFrame ("chat", "", "1");
        assertEquals ("id: 1\nevent: chat\n\n", frame.toString ());
    }

    @Test
    void toStringWithOnlyData () {
        SseFrame frame = new SseFrame (null, "hello", null);
        assertEquals ("data: hello\n\n", frame.toString ());
    }

    @Test
    void builderUsesToStringByDefault () {
        SseFrame frame = new SseFrame.Builder ().event ("chat").data (123).build ();
        assertEquals ("event: chat\ndata: 123\n\n", frame.toString ());
    }

    @Test
    void builderSerializesJsonWhenEnabled () {
        SseFrame frame = new SseFrame.Builder ().event ("chat").json (true)
                .data (Map.of ("user", "tom")).build ();
        assertNotNull (frame.data ());
        assertTrue (frame.data ().contains ("\"user\""));
        assertTrue (frame.data ().contains ("\"tom\""));
        assertTrue (frame.toString ().startsWith ("event: chat\ndata: {"));
    }

    @Test
    void builderWithoutDataProducesNoDataLine () {
        SseFrame frame = new SseFrame.Builder ().id ("9").event ("chat").build ();
        assertNull (frame.data ());
        assertEquals ("id: 9\nevent: chat\n\n", frame.toString ());
    }

    @Test
    void builderCarriesIdAndEvent () {
        SseFrame frame = new SseFrame.Builder ().id ("7").event ("notice").data ("hi").build ();
        assertEquals ("7", frame.id ());
        assertEquals ("notice", frame.event ());
        assertEquals ("hi", frame.data ());
    }
}

package org.dreamwork.dsi.embedded.httpd.support.sse;

import org.dreamwork.util.JsonHelper;
import org.dreamwork.util.StringUtil;

/**
 * 一帧 SSE 数据，对应 SSE 协议中的一次事件消息。
 *
 * <p>{@link #toString()} 会把它渲染成如下文本块（以空行结尾）：</p>
 * <pre>{@code
 * id: 3f9a1c7e-...
 * event: chat
 * data: {"user":"tom","text":"hi"}
 * }</pre>
 *
 * <p>各字段的取舍规则：</p>
 * <ul>
 *   <li>{@code event} 为 {@code null} 时不输出 {@code event} 行，客户端将按默认的 {@code message} 事件处理；</li>
 *   <li>{@code data} 为 {@code null} 或空串时不输出 {@code data} 行；</li>
 *   <li>{@code id} 用于客户端断线重连时携带 {@code Last-Event-ID}，实现续传。</li>
 * </ul>
 *
 * <p><b>注意：</b>{@code data} 是单行输出的，若内容里含换行符，会被原样写出并破坏 SSE 报文结构，
 * 请先自行转义（例如序列化为 JSON 后再传入）。</p>
 *
 * <p>推荐使用 {@link Builder} 构建实例；例如：</p>
 * <pre>{@code
 * SseFrame frame = new SseFrame.Builder ()
 *         .id (StringUtil.uuid ())
 *         .event ("chat")
 *         .json (true)
 *         .data (Map.of ("user", "tom", "text", "hi"))
 *         .build ();
 * }</pre>
 *
 * @param event 事件名，可为 {@code null}
 * @param data  已经渲染好的数据正文（字符串），可为 {@code null}
 * @param id    事件 ID，可为 {@code null}
 * @see Builder
 */
public record SseFrame (String event, String data, String id) {
    /**
     * 把本帧渲染为符合 SSE 协议的文本块，帧与帧之间以空行分隔。
     *
     * @return 可直接写入响应输出流的文本
     */
    @Override
    public String toString () {
        StringBuilder sb = new StringBuilder(128);
        if (id != null) sb.append("id: ").append(id).append('\n');
        if (event != null) sb.append("event: ").append(event).append('\n');
        if (StringUtil.isNotEmpty (data)) {
            sb.append ("data: ").append (data).append ('\n');
        }
        sb.append('\n');
        return sb.toString();
    }

    /**
     * {@link SseFrame} 的构建器，用于链式组装一帧数据。
     *
     * <p>其中 {@link #data(Object)} 接收的是<b>原始对象</b>，真正的序列化推迟到 {@link #build()} 时进行，
     * 序列化方式由 {@link #json(boolean)} 决定。</p>
     */
    public static class Builder {
        private String id, event;
        /** 原始数据对象，在 {@link #build()} 时被转换为字符串。 */
        private Object data;
        /** 是否以 JSON 方式序列化 {@link #data}。 */
        private boolean asJson;

        /**
         * 构建数据帧。
         *
         * <p>若 {@link #json(boolean)} 为 {@code true}，使用 JSON 序列化 {@code data}，
         * 否则直接调用其 {@code toString()}。</p>
         *
         * @return 构建好的数据帧
         */
        public SseFrame build () {
            String content = null;
            if (data != null) {
                if (asJson) {
                    content = JsonHelper.toJson (data);
                } else {
                    content = data.toString ();
                }
            }
            return new SseFrame (event, content, id);
        }

        /**
         * 设置是否以 JSON 方式序列化数据。
         *
         * @param on {@code true} 表示把数据对象序列化为 JSON
         * @return 当前构建器，便于链式调用
         */
        public Builder json (boolean on) {
            this.asJson = on;
            return this;
        }

        /**
         * 设置事件 ID。
         *
         * @param id 事件 ID，通常使用 {@code StringUtil.uuid ()}
         * @return 当前构建器，便于链式调用
         */
        public Builder id (String id) {
            this.id = id;
            return this;
        }

        /**
         * 设置事件名。
         *
         * @param event 事件名；为 {@code null} 时客户端按默认的 {@code message} 事件处理
         * @return 当前构建器，便于链式调用
         */
        public Builder event (String event) {
            this.event = event;
            return this;
        }

        /**
         * 设置数据内容（原始对象，序列化在 {@link #build()} 中进行）。
         *
         * @param data 任意可被 JSON 序列化的对象，或其 {@code toString()} 有意义的对象
         * @return 当前构建器，便于链式调用
         */
        public Builder data (Object data) {
            this.data = data;
            return this;
        }
    }
}

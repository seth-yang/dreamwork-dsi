package org.dreamwork.dsi.embedded.httpd.support.sse;

import org.dreamwork.util.JsonHelper;
import org.dreamwork.util.StringUtil;

public record SseFrame (String channel, String event, String data, String id) {
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

    public static class Builder {
        private String id, event, channel;
        private Object data;
        private boolean asJson;

        public SseFrame build () {
            String content = null;
            if (data != null) {
                if (asJson) {
                    content = JsonHelper.toJson (data);
                } else {
                    content = data.toString ();
                }
            }
            return new SseFrame (channel, event, content, id);
        }

        public Builder json (boolean on) {
            this.asJson = on;
            return this;
        }

        public Builder id (String id) {
            this.id = id;
            return this;
        }

        public Builder channel (String channel) {
            this.channel = channel;
            return this;
        }

        public Builder event (String event) {
            this.event = event;
            return this;
        }

        public Builder data (Object data) {
            this.data = data;
            return this;
        }
    }
}
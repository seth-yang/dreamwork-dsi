package org.dreamwork.dsi.embedded.httpd.support;

import org.dreamwork.dsi.embedded.httpd.support.sse.SseRole;public class WebHandler {
    public String beanName;
    public WebMappedMethod method;
    public HandlerType type;
    /** @since  1.1.0 */
    public boolean wrapped;
    /** @since 3.0.0 */
    public boolean sse;
    public String[] sseChannels;
    public SseRole sseRole;
    public boolean sseDynamic;
    public long sseTimeout = -1;

    public WebHandler (String beanName, WebMappedMethod method) {
        this.beanName = beanName;
        this.method   = method;
        this.type     = method.type;
        this.wrapped  = method.wrapped;
        this.sse      = method.sseSupported;
        this.sseRole  = method.sseRole;
        this.sseDynamic = method.sseDynamic;
        this.sseChannels = method.sseChannels;
        this.sseTimeout  = method.sseTimeout;
    }
}

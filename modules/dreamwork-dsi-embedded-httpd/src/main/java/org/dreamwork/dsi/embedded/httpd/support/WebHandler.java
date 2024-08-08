package org.dreamwork.dsi.embedded.httpd.support;

public class WebHandler {
    public String beanName;
    public WebMappedMethod method;
    public HandlerType type;
    /** @since  1.1.0 */
    public boolean wrapped;

    public WebHandler (String beanName, WebMappedMethod method) {
        this.beanName = beanName;
        this.method   = method;
        this.type     = method.type;
        this.wrapped  = method.wrapped;
    }
}

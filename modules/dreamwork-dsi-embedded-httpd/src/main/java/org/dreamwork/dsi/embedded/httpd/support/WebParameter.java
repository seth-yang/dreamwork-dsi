package org.dreamwork.dsi.embedded.httpd.support;

public class WebParameter {
    public String name, defaultValue, contentType;
    public ParameterLocation location = ParameterLocation.Internal;
    public ParameterType type;
    public boolean internal;
    /**
     * @since 1.1.0
     */
    public boolean nullable;
}
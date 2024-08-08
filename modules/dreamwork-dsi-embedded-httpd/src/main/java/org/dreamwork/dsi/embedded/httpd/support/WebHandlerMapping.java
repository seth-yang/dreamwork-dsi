package org.dreamwork.dsi.embedded.httpd.support;

import java.util.HashMap;
import java.util.Map;

public class WebHandlerMapping {
    public final String category, beanName;
    public final Map<String, WebMappedMethod> get = new HashMap<> ();
    public final Map<String, WebMappedMethod> post = new HashMap<> ();
    public final Map<String, WebMappedMethod> delete = new HashMap<> ();
    public final Map<String, WebMappedMethod> put = new HashMap<> ();

    public WebHandlerMapping (String category, String beanName) {
        this.category = category;
        this.beanName = beanName;
    }
}

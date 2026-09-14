module org.dreamwork.dsi.embedded.httpd {
    requires transitive org.dreamwork.dsi.runtime;
    requires transitive jakarta.annotation;
    requires transitive org.slf4j;
    requires transitive dreamwork.base;
    requires transitive org.apache.tomcat.embed.core;
    requires org.apache.tomcat.embed.websocket;

    exports org.dreamwork.dsi.embedded.httpd.annotation;
    exports org.dreamwork.dsi.embedded.httpd.support;
    exports org.dreamwork.dsi.embedded.httpd.starter;
    exports org.dreamwork.dsi.embedded.httpd.support.sse;
    exports org.dreamwork.dsi.embedded.httpd.support.upload;
    exports org.dreamwork.dsi.embedded.httpd.support.websocket;

    opens org.dreamwork.dsi.embedded.httpd.starter to
            dreamwork.base, org.dreamwork.dsi.runtime;
    opens org.dreamwork.dsi.embedded.httpd.support.upload to
            dreamwork.base, org.dreamwork.dsi.runtime;
    opens org.dreamwork.dsi.embedded.httpd.support.sse.impl to
            dreamwork.base, org.dreamwork.dsi.runtime;
}
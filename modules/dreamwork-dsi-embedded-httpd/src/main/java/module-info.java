module org.dreamwork.dsi.embedded.httpd {
    requires org.dreamwork.dsi.runtime;
    requires jakarta.annotation;
    requires org.slf4j;
    requires dreamwork.base;
    requires org.apache.tomcat.embed.websocket;
    exports org.dreamwork.dsi.embedded.httpd.annotation;
    exports org.dreamwork.dsi.embedded.httpd.support;
    exports org.dreamwork.dsi.embedded.httpd.starter;
    exports org.dreamwork.dsi.embedded.httpd.support.upload;
    exports org.dreamwork.dsi.embedded.httpd.support.websocket;

    opens org.dreamwork.dsi.embedded.httpd.starter to org.dreamwork.dsi.runtime;
}
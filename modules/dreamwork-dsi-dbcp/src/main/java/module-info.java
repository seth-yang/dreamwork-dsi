module org.dreamwork.dsi.dbcp {
    requires org.dreamwork.dsi.runtime;
    requires org.slf4j;
    requires jakarta.annotation;
    requires dreamwork.base;
    requires org.apache.tomcat.dbcp;

    opens org.dreamwork.dsi.dbcp.starter to org.dreamwork.dsi.runtime;
}
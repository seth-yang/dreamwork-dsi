module org.dreamwork.dsi.dbcp {
    requires java.sql;
    requires transitive org.dreamwork.dsi.runtime;
    requires org.slf4j;
    requires jakarta.annotation;
    requires transitive dreamwork.base;
    requires transitive org.apache.commons.dbcp2;

    opens org.dreamwork.dsi.dbcp.starter to
            dreamwork.base, org.dreamwork.dsi.runtime;
}
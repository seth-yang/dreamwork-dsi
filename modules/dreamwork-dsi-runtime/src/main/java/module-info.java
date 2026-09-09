module org.dreamwork.dsi.runtime {
    exports org.dreamwork.injection;
//    exports org.dreamwork.injection.impl;

    requires dreamwork.base;
    requires org.slf4j;
    requires java.management;
    requires jakarta.annotation;
    requires com.fasterxml.jackson.databind;
}
package org.dreamwork.dsi.embedded.httpd.annotation;

import org.dreamwork.dsi.embedded.httpd.support.sse.SseRole;

import java.lang.annotation.*;

@Target ({ElementType.METHOD})
@Retention (RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface AServerSideEvent {
    String[] value () default {};    // alias for channels
    String[] channels () default {};
    SseRole role () default SseRole.Producer;
    boolean allowDynamicAttachment () default false;
    long timeout () default -1;
}
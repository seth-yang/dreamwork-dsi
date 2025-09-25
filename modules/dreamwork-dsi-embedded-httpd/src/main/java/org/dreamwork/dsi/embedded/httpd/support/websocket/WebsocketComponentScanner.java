package org.dreamwork.dsi.embedded.httpd.support.websocket;

import org.dreamwork.dsi.embedded.httpd.annotation.AWebSocket;
import org.dreamwork.injection.impl.ClassScanner;

import javax.websocket.Endpoint;
import java.util.Set;

public class WebsocketComponentScanner extends ClassScanner {
    private final Set<Class<? extends Endpoint>> set;

    public WebsocketComponentScanner (Set<Class<? extends Endpoint>> set) {
        this.set = set;
    }

    @Override
    protected boolean accept (Class<?> type) {
        return type.isAnnotationPresent (AWebSocket.class) && Endpoint.class.isAssignableFrom (type);
    }

    @Override
    @SuppressWarnings ("unchecked")
    protected void onFound (String name, Class<?> type, Set<Wrapper> wrappers) {
        set.add ((Class<? extends Endpoint>) type);
    }

    @Override
    protected void onCompleted (Set<Wrapper> wrappers) {
    }
}
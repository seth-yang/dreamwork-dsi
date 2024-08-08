package org.dreamwork.dsi.embedded.httpd.support;

import org.dreamwork.util.StringUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ManagedSession {
    private final Map<String, Object> map = new ConcurrentHashMap<> ();

    @SuppressWarnings ("unchecked")
    public<T> T get (String key) {
        timestamp = System.currentTimeMillis ();
        return (T) map.get (key);
    }

    public void set (String key, Object value) {
        try {
            if (value != null)
                map.put (key, value);
            else
                map.remove (key);
        } finally {
            timestamp = System.currentTimeMillis ();
        }
    }

    public void remove (String key) {
        map.remove (key);
        timestamp = System.currentTimeMillis ();
    }

    public boolean has (String key) {
        timestamp = System.currentTimeMillis ();
        return map.containsKey (key);
    }

    public void clear () {
        map.clear ();
        timestamp = System.currentTimeMillis ();
    }

    public long timestamp;
    public final String id;

    public ManagedSession () {
        this.id     = StringUtil.uuid ();
        timestamp   = System.currentTimeMillis ();
    }

    public ManagedSession (String id) {
        this.id     = id;
        timestamp   = System.currentTimeMillis ();
    }
}
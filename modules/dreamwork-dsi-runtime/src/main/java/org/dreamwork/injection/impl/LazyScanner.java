package org.dreamwork.injection.impl;

import org.dreamwork.injection.IObjectContext;

import javax.management.InstanceAlreadyExistsException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class LazyScanner {
    private final Map<String, ClassScanner> cache = new ConcurrentHashMap<> ();

    void merge (Map<String, ClassScanner> scanners) throws InstanceAlreadyExistsException {
        for (String key : scanners.keySet ()) {
            if (cache.containsKey (key)) {
                ClassScanner cached = cache.get (key);
                ClassScanner scanner = scanners.get (key);
                if (cached != scanner) {
                    throw new InstanceAlreadyExistsException ("key");
                }
            } else {
                cache.put (key, scanners.get (key));
            }
        }
    }

    Map<String, ClassScanner> getCachedScanners () {
        return new HashMap<> (cache);
    }
}
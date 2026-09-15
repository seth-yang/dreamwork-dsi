package org.dreamwork.injection.impl;

import org.dreamwork.injection.ClassScanner;
import org.junit.jupiter.api.Test;

import javax.management.InstanceAlreadyExistsException;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link LazyScanner} 的单元测试。
 */
class LazyScannerTest {

    @Test
    void merge_putsNewScannersIntoCache () throws Exception {
        LazyScanner lazy = new LazyScanner ();
        ClassScanner scanner = new NoopScanner ();

        assertTrue (lazy.getCachedScanners ().isEmpty ());

        lazy.merge (Map.of ("demo", scanner));

        Map<String, ClassScanner> cached = lazy.getCachedScanners ();
        assertEquals (1, cached.size ());
        assertSame (scanner, cached.get ("demo"));
    }

    @Test
    void merge_sameInstanceTwice_isIdempotent () throws Exception {
        LazyScanner lazy = new LazyScanner ();
        ClassScanner scanner = new NoopScanner ();

        lazy.merge (Map.of ("demo", scanner));
        lazy.merge (Map.of ("demo", scanner));

        assertEquals (1, lazy.getCachedScanners ().size ());
    }

    @Test
    void merge_sameKeyWithDifferentScanner_throws () {
        LazyScanner lazy = new LazyScanner ();

        assertThrows (
                InstanceAlreadyExistsException.class,
                () -> {
                    lazy.merge (Map.of ("demo", new NoopScanner ()));
                    lazy.merge (Map.of ("demo", new NoopScanner ()));
                }
        );
    }

    @Test
    void getCachedScanners_returnsACopy () throws Exception {
        LazyScanner lazy = new LazyScanner ();
        lazy.merge (Map.of ("demo", new NoopScanner ()));

        lazy.getCachedScanners ().clear ();

        assertEquals (1, lazy.getCachedScanners ().size ());
    }

    private static class NoopScanner extends ClassScanner {
        @Override
        protected boolean accept (Class<?> type) {
            return false;
        }

        @Override
        protected void onFound (String name, Class<?> type, Set<Wrapper> wrappers) {
            // nothing to do
        }

        @Override
        protected void onCompleted (Set<Wrapper> wrappers) {
            // nothing to do
        }
    }
}

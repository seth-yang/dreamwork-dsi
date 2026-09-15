package org.dreamwork.injection;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ClassScanner} 的单元测试，使用一个只做记录的扫描器来观察事件时序。
 */
class ClassScannerTest {
    private static final String FIXTURE_PACKAGE = "org.dreamwork.injection.fixture";

    @Test
    void scan_triggersOnFoundForAcceptedTypesThenOnCompleted () throws Exception {
        RecordingScanner scanner = new RecordingScanner ();

        scanner.scan (FIXTURE_PACKAGE);

        assertEquals (List.of ("SimpleComponent"), scanner.found);
        assertFalse (scanner.accepted.isEmpty ());
        assertEquals (1, scanner.completedCount);
        assertEquals (1, scanner.wrappers.size ());
    }

    @Test
    void scan_withoutPackageNames_stillCompletes () throws Exception {
        RecordingScanner scanner = new RecordingScanner ();

        scanner.scan ();

        assertTrue (scanner.found.isEmpty ());
        assertEquals (1, scanner.completedCount);
        assertNotNull (scanner.wrappers);
        assertTrue (scanner.wrappers.isEmpty ());
    }

    @Test
    void scan_whenOnFoundThrows_propagatesAndStillTriggersOnCompleted () {
        RecordingScanner scanner = new RecordingScanner ();
        scanner.failOnFound = true;

        assertThrows (IllegalStateException.class, () -> scanner.scan (FIXTURE_PACKAGE));

        assertEquals (1, scanner.completedCount);
    }

    private static class RecordingScanner extends ClassScanner {
        final List<Class<?>> accepted = new ArrayList<> ();
        final List<String> found = new ArrayList<> ();
        Set<Wrapper> wrappers;
        int completedCount;
        boolean failOnFound;

        @Override
        protected boolean accept (Class<?> type) {
            accepted.add (type);
            return type.getSimpleName ().startsWith ("Simple");
        }

        @Override
        protected void onFound (String name, Class<?> type, Set<Wrapper> wrappers) {
            found.add (name);

            Wrapper wrapper = new Wrapper ();
            wrapper.type = type;
            wrappers.add (wrapper);

            if (failOnFound) {
                throw new IllegalStateException ("boom");
            }
        }

        @Override
        protected void onCompleted (Set<Wrapper> wrappers) {
            completedCount ++;
            this.wrappers = wrappers;
        }
    }
}

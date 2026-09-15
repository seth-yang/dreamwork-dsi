package org.dreamwork.injection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ShutdownHook} 的单元测试。
 *
 * <p>注意：{@code bind(...)} 会真正监听端口，而 {@code cancel()} 内部会调用
 * {@link System#exit(int)}，因此这里只测试不涉及副作用的静态方法。</p>
 */
class ShutdownHookTest {

    @Test
    void isShutdownRequest_recognizesSupportedWords () {
        assertTrue (ShutdownHook.isShutdownRequest ("stop"));
        assertTrue (ShutdownHook.isShutdownRequest ("--stop"));
        assertTrue (ShutdownHook.isShutdownRequest ("shutdown"));
        assertTrue (ShutdownHook.isShutdownRequest ("--shutdown"));

        assertTrue (ShutdownHook.isShutdownRequest ("-c", "app.conf", "--stop"));
    }

    @Test
    void isShutdownRequest_ignoresOtherArguments () {
        assertFalse (ShutdownHook.isShutdownRequest ());
        assertFalse (ShutdownHook.isShutdownRequest ("start"));
        assertFalse (ShutdownHook.isShutdownRequest ("-c", "app.conf"));
        assertFalse (ShutdownHook.isShutdownRequest ("--root"));
    }

    @Test
    void shutdown_withoutPortFile_doesNothing (@TempDir Path tempDir) throws Exception {
        String previous = System.getProperty ("java.io.tmpdir");
        System.setProperty ("java.io.tmpdir", tempDir.toString ());
        try {
            assertDoesNotThrow (ShutdownHook::shutdown);
        } finally {
            System.setProperty ("java.io.tmpdir", previous);
        }
    }
}

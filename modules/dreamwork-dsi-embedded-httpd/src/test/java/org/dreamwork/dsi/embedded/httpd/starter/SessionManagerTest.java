package org.dreamwork.dsi.embedded.httpd.starter;

import org.dreamwork.dsi.embedded.httpd.support.ManagedSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SessionManager} 的单元测试。
 */
class SessionManagerTest {
    private SessionManager manager;

    @AfterEach
    void tearDown () {
        if (manager != null) {
            manager.stopMonitor ();
        }
    }

    @Test
    void createWithKeyKeepsKeyAsId () {
        manager = new SessionManager ();
        ManagedSession session = manager.create ("my-session");
        assertEquals ("my-session", session.id);
        assertSame (session, manager.get ("my-session"));
    }

    @Test
    void createWithoutKeyGeneratesUuid () {
        manager = new SessionManager ();
        ManagedSession a = manager.create (null);
        ManagedSession b = manager.create ();
        assertNotNull (a.id);
        assertNotEquals (a.id, b.id);
        assertSame (a, manager.get (a.id));
        assertSame (b, manager.get (b.id));
    }

    @Test
    void removeDeletesSession () {
        manager = new SessionManager ();
        ManagedSession session = manager.create ("to-remove");
        manager.remove ("to-remove");
        assertNull (manager.get ("to-remove"));
    }

    @Test
    void getReturnsNullForUnknownKey () {
        manager = new SessionManager ();
        assertNull (manager.get ("unknown"));
    }

    @Test
    @Timeout (30)
    void expiredSessionIsRemovedByMonitor () throws Exception {
        manager = new SessionManager ();
        // 通过反射把超时时间改小（私有字段，由 @AConfigured 正常注入）
        Field timeoutField = SessionManager.class.getDeclaredField ("timeout");
        timeoutField.setAccessible (true);
        timeoutField.setLong (manager, 50L);

        // 先创建会话再启动监控，避免监控线程因空表进入长等待
        ManagedSession session = manager.create ("expired-key");
        manager.startMonitor ();
        assertNotNull (manager.get ("expired-key"));

        // 监控循环每 2 秒检查一次，轮询等待其被清理
        long deadline = System.currentTimeMillis () + 15_000;
        while (manager.get ("expired-key") != null && System.currentTimeMillis () < deadline) {
            Thread.sleep (200);
        }
        assertNull (manager.get ("expired-key"));

        manager.stopMonitor ();
    }
}

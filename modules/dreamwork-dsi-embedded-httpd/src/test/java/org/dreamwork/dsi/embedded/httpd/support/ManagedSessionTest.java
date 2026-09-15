package org.dreamwork.dsi.embedded.httpd.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ManagedSession} 的单元测试。
 */
class ManagedSessionTest {

    @Test
    void newSession_hasGeneratedIdAndTimestamp () {
        ManagedSession session = new ManagedSession ();

        assertNotNull (session.id);
        assertFalse (session.id.isEmpty ());
        assertTrue (session.timestamp > 0);
    }

    @Test
    void newSession_withExplicitId_keepsIt () {
        ManagedSession session = new ManagedSession ("custom-id");

        assertEquals ("custom-id", session.id);
    }

    @Test
    void setAndGet_roundTrip () {
        ManagedSession session = new ManagedSession ();

        session.set ("user", "tom");

        assertEquals ("tom", session.get ("user"));
        assertTrue (session.has ("user"));
        assertEquals ("tom", session.<String>get ("user"));
    }

    @Test
    void setWithNull_removesTheKey () {
        ManagedSession session = new ManagedSession ();
        session.set ("user", "tom");

        session.set ("user", null);

        assertFalse (session.has ("user"));
        assertNull (session.get ("user"));
    }

    @Test
    void removeDeletesTheKey () {
        ManagedSession session = new ManagedSession ();
        session.set ("user", "tom");

        session.remove ("user");

        assertFalse (session.has ("user"));
    }

    @Test
    void clearRemovesEverything () {
        ManagedSession session = new ManagedSession ();
        session.set ("a", 1);
        session.set ("b", 2);

        session.clear ();

        assertFalse (session.has ("a"));
        assertFalse (session.has ("b"));
    }

    @Test
    void get_unknownKeyReturnsNull () {
        ManagedSession session = new ManagedSession ();

        assertNull (session.get ("nothing"));
        assertFalse (session.has ("nothing"));
    }

    @Test
    void operations_refreshTimestamp () throws Exception {
        ManagedSession session = new ManagedSession ("id");
        session.timestamp = 1L;

        session.set ("key", "value");

        assertTrue (session.timestamp > 1L);
    }
}

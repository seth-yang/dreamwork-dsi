package org.dreamwork.dsi.embedded.httpd.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ManagedSession} 的单元测试。
 */
class ManagedSessionTest {

    @Test
    void constructorWithKeyKeepsId () {
        ManagedSession session = new ManagedSession ("fixed-id");
        assertEquals ("fixed-id", session.id);
    }

    @Test
    void defaultConstructorGeneratesUuid () {
        ManagedSession a = new ManagedSession ();
        ManagedSession b = new ManagedSession ();
        assertNotNull (a.id);
        assertNotEquals (a.id, b.id);
    }

    @Test
    void setAndGetAttribute () {
        ManagedSession session = new ManagedSession ("s1");
        session.set ("user", "tom");
        assertEquals ("tom", session.get ("user"));

        Integer count = session.get ("count");
        assertNull (count);
    }

    @Test
    void setNullRemovesAttribute () {
        ManagedSession session = new ManagedSession ("s1");
        session.set ("user", "tom");
        session.set ("user", null);
        assertFalse (session.has ("user"));
    }

    @Test
    void hasAndRemove () {
        ManagedSession session = new ManagedSession ("s1");
        assertFalse (session.has ("user"));
        session.set ("user", "tom");
        assertTrue (session.has ("user"));
        session.remove ("user");
        assertFalse (session.has ("user"));
    }

    @Test
    void clearRemovesAllAttributes () {
        ManagedSession session = new ManagedSession ("s1");
        session.set ("a", 1);
        session.set ("b", 2);
        session.clear ();
        assertFalse (session.has ("a"));
        assertFalse (session.has ("b"));
    }

    @Test
    void accessRefreshesTimestamp () throws Exception {
        ManagedSession session = new ManagedSession ("s1");
        long before = session.timestamp;
        Thread.sleep (20);
        session.set ("k", "v");
        assertTrue (session.timestamp >= before + 20);

        long middle = session.timestamp;
        Thread.sleep (20);
        session.get ("k");
        assertTrue (session.timestamp >= middle + 20);
    }
}

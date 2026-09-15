package org.dreamwork.dsi.embedded.httpd.starter;

import jakarta.servlet.ServletException;
import org.dreamwork.dsi.embedded.httpd.StubObjectContext;
import org.dreamwork.dsi.embedded.httpd.annotation.APathVariable;
import org.dreamwork.dsi.embedded.httpd.annotation.AWebHandler;
import org.dreamwork.dsi.embedded.httpd.annotation.AWebMapping;
import org.dreamwork.dsi.embedded.httpd.annotation.AWebParameter;
import org.dreamwork.dsi.embedded.httpd.support.HandlerType;
import org.dreamwork.dsi.embedded.httpd.support.WebHandler;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link WebHandlerScanner} 的单元测试。
 */
class WebHandlerScannerTest {
    // ------------------------------------------------------
    // 测试夹具
    // ------------------------------------------------------

    @AWebHandler ("user")
    public static class UserHandler {
        @AWebMapping (value = "/login", method = "POST")
        public String login (@AWebParameter (name = "name") String name) {
            return "hello " + name;
        }

        @AWebMapping ("/detail/${id}")
        public String detail (@APathVariable ("id") int id) {
            return "id = " + id;
        }

        @AWebMapping (value = "/echo", wrapped = true)
        public String echo () {
            return "echo";
        }
    }

    /** 与 {@link UserHandler} 的 /login 冲突，用于验证重复映射的检查。 */
    @AWebHandler ("user")
    public static class DuplicateHandler {
        @AWebMapping (value = "/login", method = "POST")
        public String login () {
            return "x";
        }
    }

    private static StubObjectContext contextOf (Object... handlers) {
        StubObjectContext context = new StubObjectContext ();
        for (int i = 0; i < handlers.length; i++) {
            context.add ("bean" + i, handlers[i]);
        }
        return context;
    }

    // ------------------------------------------------------
    // perform / match
    // ------------------------------------------------------

    @Test
    void performRegistersMappingsAndMatchReturnsHandler () throws ServletException {
        WebHandlerScanner scanner = new WebHandlerScanner ();
        scanner.perform (contextOf (new UserHandler ()));

        WebHandler handler = scanner.match ("/user/login", "post", new HashMap<> ());
        assertNotNull (handler);
        assertEquals ("bean0", handler.beanName);
        assertEquals (HandlerType.API_HANDLER, handler.type);
        assertFalse (handler.wrapped);
    }

    @Test
    void matchResolvesPathVariable () throws ServletException {
        WebHandlerScanner scanner = new WebHandlerScanner ();
        scanner.perform (contextOf (new UserHandler ()));

        Map<String, String> args = new HashMap<> ();
        WebHandler handler = scanner.match ("/user/detail/42", "get", args);
        assertNotNull (handler);
        assertEquals ("42", args.get ("id"));
    }

    @Test
    void matchCarriesWrappedFlag () throws ServletException {
        WebHandlerScanner scanner = new WebHandlerScanner ();
        scanner.perform (contextOf (new UserHandler ()));

        WebHandler handler = scanner.match ("/user/echo", "get", new HashMap<> ());
        assertNotNull (handler);
        assertTrue (handler.wrapped);
    }

    @Test
    void matchReturnsNullForUnknownPath () throws ServletException {
        WebHandlerScanner scanner = new WebHandlerScanner ();
        scanner.perform (contextOf (new UserHandler ()));

        assertNull (scanner.match ("/user/unknown", "get", new HashMap<> ()));
    }

    @Test
    void matchThrowsForUnsupportedHttpMethod () {
        WebHandlerScanner scanner = new WebHandlerScanner ();
        scanner.perform (contextOf (new UserHandler ()));

        assertThrows (ServletException.class, () -> scanner.match ("/user/login", "put", new HashMap<> ()));
    }

    @Test
    void matchThrowsForEmptyScanner () {
        WebHandlerScanner scanner = new WebHandlerScanner ();
        scanner.perform (new StubObjectContext ());

        assertThrows (ServletException.class, () -> scanner.match ("/any", "get", new HashMap<> ()));
    }

    @Test
    void performThrowsOnDuplicatePattern () {
        WebHandlerScanner scanner = new WebHandlerScanner ();
        assertThrows (IllegalArgumentException.class,
                () -> scanner.perform (contextOf (new UserHandler (), new DuplicateHandler ())));
    }

    @Test
    void performIgnoresNonHandlerBeans () {
        WebHandlerScanner scanner = new WebHandlerScanner ();
        StubObjectContext context = contextOf ("a plain string", 42);
        assertDoesNotThrow (() -> scanner.perform (context));
    }
}

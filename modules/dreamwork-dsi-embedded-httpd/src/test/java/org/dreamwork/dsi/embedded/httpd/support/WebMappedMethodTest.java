package org.dreamwork.dsi.embedded.httpd.support;

import org.dreamwork.dsi.embedded.httpd.annotation.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link WebMappedMethod} 的单元测试。
 * <p>覆盖 URL 模式匹配（含路径变量与通配符）、参数注解解析、SSE 元数据解析与反射调用。</p>
 */
class WebMappedMethodTest {
    // ------------------------------------------------------
    // 测试夹具
    // ------------------------------------------------------

    public static class Sample {
        public String plain () {
            return "plain";
        }

        public String missingName (@AWebParameter String noName) {
            return noName;
        }

        public String namedParam (@AWebParameter (name = "id") String id) {
            return id;
        }

        public String valueParam (@AWebParameter ("uid") String id) {
            return id;
        }

        public String defaultParam (@AWebParameter (name = "page", defaultValue = "1") int page) {
            return "page = " + page;
        }

        public String internalParam (@AWebParameter (internal = true) Object ctx) {
            return String.valueOf (ctx);
        }

        public String bodyParam (@ARequestBody String body) {
            return body;
        }

        public String internalOnly (@AInternal Object o) {
            return String.valueOf (o);
        }

        public String requestAttr (@ARequestAttribute ("user") Object user) {
            return String.valueOf (user);
        }

        public String sessionAttr (@ASessionAttribute (name = "token", nullable = false) String token) {
            return token;
        }

        public String formItem (@AFormItem (name = "age", type = ParameterType.integer, defaultValue = "18") int age) {
            return "age = " + age;
        }

        public String pathVar (@APathVariable ("id") String id) {
            return id;
        }

        public String headerItem (@AHeaderItem ("X-Token") String token) {
            return token;
        }

        public String managedAttr (@AManagedSessionAttribute ("ms-key") String value) {
            return value;
        }

        public String uploaded (@AUploadedFile ("file") Object part) {
            return String.valueOf (part);
        }

        public String unannotated (Object whatever) {
            return String.valueOf (whatever);
        }

        public String error () {
            throw new IllegalStateException ("boom");
        }

        @AServerSideEvent (value = "chat", role = SseRole.Subscriber, allowDynamicAttachment = true, timeout = 5000)
        public String sse (@APathVariable ("id") String id) {
            return id;
        }
    }

    private static Method method (String name, Class<?>... types) {
        assertDoesNotThrow (() -> Sample.class.getMethod (name, types));
        try {
            return Sample.class.getMethod (name, types);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException (e);
        }
    }

    private static WebMappedMethod wmm (String methodName, String pattern, Class<?>... types) {
        return new WebMappedMethod (method (methodName, types), pattern, HandlerType.API_HANDLER);
    }

    // ------------------------------------------------------
    // 构造函数
    // ------------------------------------------------------

    @Test
    void constructorRejectsNullMethod () {
        assertThrows (NullPointerException.class, () -> new WebMappedMethod (null, "/x", HandlerType.API_HANDLER));
    }

    @Test
    void simplePatternHasNoParts () {
        WebMappedMethod m = wmm ("plain", "/login");
        assertNull (m.parts);
        assertEquals ("/login", m.pattern);
        assertNull (m.parameters);
    }

    @Test
    void aWebParameterNameMissingThrows () {
        assertThrows (IllegalArgumentException.class, () -> wmm ("missingName", "/x", String.class));
    }

    // ------------------------------------------------------
    // 参数解析
    // ------------------------------------------------------

    @Test
    void parsesAWebParameterWithName () {
        WebMappedMethod m = wmm ("namedParam", "/a", String.class);
        assertEquals (1, m.parameters.size ());
        WebParameter wp = m.parameters.get (0);
        assertEquals ("id", wp.name);
        assertFalse (wp.internal);
        assertEquals (ParameterType.raw, wp.type);
        assertEquals (ParameterLocation.Path, wp.location);
        assertNull (wp.defaultValue);
        assertTrue (wp.nullable);
    }

    @Test
    void parsesAWebParameterWithValueAlias () {
        WebMappedMethod m = wmm ("valueParam", "/a", String.class);
        assertEquals ("uid", m.parameters.get (0).name);
    }

    @Test
    void parsesAWebParameterWithDefaultValue () {
        WebMappedMethod m = wmm ("defaultParam", "/a", int.class);
        WebParameter wp = m.parameters.get (0);
        assertEquals ("1", wp.defaultValue);
    }

    @Test
    void parsesInternalAWebParameter () {
        WebMappedMethod m = wmm ("internalParam", "/a", Object.class);
        WebParameter wp = m.parameters.get (0);
        assertTrue (wp.internal);
    }

    @Test
    void parsesARequestBody () {
        WebMappedMethod m = wmm ("bodyParam", "/a", String.class);
        WebParameter wp = m.parameters.get (0);
        assertEquals (ParameterType.raw, wp.type);
        assertEquals (ParameterLocation.Body, wp.location);
        assertEquals ("application/json;charset=utf-8", wp.contentType);
    }

    @Test
    void parsesAInternal () {
        WebMappedMethod m = wmm ("internalOnly", "/a", Object.class);
        WebParameter wp = m.parameters.get (0);
        assertTrue (wp.internal);
        assertEquals (ParameterLocation.Internal, wp.location);
    }

    @Test
    void parsesARequestAttribute () {
        WebMappedMethod m = wmm ("requestAttr", "/a", Object.class);
        WebParameter wp = m.parameters.get (0);
        assertEquals (ParameterType.request_attribute, wp.type);
        assertEquals ("user", wp.name);
        assertEquals (ParameterLocation.Internal, wp.location);
    }

    @Test
    void parsesASessionAttribute () {
        WebMappedMethod m = wmm ("sessionAttr", "/a", String.class);
        WebParameter wp = m.parameters.get (0);
        assertEquals (ParameterType.session_attribute, wp.type);
        assertEquals ("token", wp.name);
        assertFalse (wp.nullable);
    }

    @Test
    void parsesAFormItem () {
        WebMappedMethod m = wmm ("formItem", "/a", int.class);
        WebParameter wp = m.parameters.get (0);
        assertEquals ("age", wp.name);
        assertEquals (ParameterType.integer, wp.type);
        assertEquals (ParameterLocation.QueryString, wp.location);
        assertEquals ("18", wp.defaultValue);
    }

    @Test
    void parsesAPathVariable () {
        WebMappedMethod m = wmm ("pathVar", "/a", String.class);
        WebParameter wp = m.parameters.get (0);
        assertEquals ("id", wp.name);
        assertEquals (ParameterLocation.Path, wp.location);
        assertEquals (ParameterType.raw, wp.type);
    }

    @Test
    void parsesAHeaderItem () {
        WebMappedMethod m = wmm ("headerItem", "/a", String.class);
        WebParameter wp = m.parameters.get (0);
        assertEquals ("X-Token", wp.name);
        assertEquals (ParameterLocation.Header, wp.location);
    }

    @Test
    void parsesAManagedSessionAttribute () {
        WebMappedMethod m = wmm ("managedAttr", "/a", String.class);
        WebParameter wp = m.parameters.get (0);
        assertEquals (ParameterType.managed_session_attribute, wp.type);
        assertEquals ("ms-key", wp.name);
    }

    @Test
    void parsesAUploadedFile () {
        WebMappedMethod m = wmm ("uploaded", "/a", Object.class);
        WebParameter wp = m.parameters.get (0);
        assertEquals (ParameterType.uploaded_file, wp.type);
        assertEquals ("file", wp.name);
        assertEquals (ParameterLocation.Internal, wp.location);
        assertTrue (wp.nullable);
    }

    @Test
    void unannotatedParameterDefaultsToInternal () {
        WebMappedMethod m = wmm ("unannotated", "/a", Object.class);
        assertEquals (1, m.parameters.size ());
        WebParameter wp = m.parameters.get (0);
        assertTrue (wp.internal);
        assertEquals (ParameterLocation.Internal, wp.location);
        assertNull (wp.name);
    }

    // ------------------------------------------------------
    // SSE 元数据
    // ------------------------------------------------------

    @Test
    void parsesAServerSideEventMetadata () {
        WebMappedMethod m = wmm ("sse", "/chat/${id}", String.class);
        assertTrue (m.sseSupported);
        assertEquals (SseRole.Subscriber, m.sseRole);
        assertTrue (m.sseDynamic);
        assertEquals (5000, m.sseTimeout);
        assertArrayEquals (new String[]{"chat"}, m.sseChannels);
    }

    @Test
    void nonSseMethodIsNotMarked () {
        WebMappedMethod m = wmm ("plain", "/login");
        assertFalse (m.sseSupported);
        assertNull (m.sseRole);
        assertEquals (-1, m.sseTimeout);
    }

    // ------------------------------------------------------
    // URL 匹配
    // ------------------------------------------------------

    @Test
    void matchesSimplePattern () {
        WebMappedMethod m = wmm ("plain", "/login");
        Map<String, String> args = new HashMap<> ();
        assertTrue (m.matches ("/login", args));
        assertFalse (m.matches ("/logout", args));
        assertFalse (m.matches ("/login/extra", args));
        assertTrue (args.isEmpty ());
    }

    @Test
    void matchesPathVariables () {
        WebMappedMethod m = wmm ("plain", "/user/${uid}/orders");
        Map<String, String> args = new HashMap<> ();
        assertTrue (m.matches ("user/42/orders", args));
        assertEquals ("42", args.get ("uid"));

        args.clear ();
        assertFalse (m.matches ("user/42", args));
        assertFalse (m.matches ("user/42/orders/extra", args));
        assertFalse (m.matches ("other/42/orders", args));
    }

    @Test
    void matchesWildcardAtTail () {
        WebMappedMethod m = wmm ("plain", "/static/*");
        Map<String, String> args = new HashMap<> ();
        assertTrue (m.matches ("static/css", args));
        assertFalse (m.matches ("static", args));
        assertFalse (m.matches ("static/a/b", args));
    }

    // ------------------------------------------------------
    // 调用
    // ------------------------------------------------------

    @Test
    void invokeDelegatesToTargetMethod () throws Exception {
        WebMappedMethod m = wmm ("namedParam", "/a", String.class);
        Object result = m.invoke (new Sample (), "hello");
        assertEquals ("hello", result);
    }

    @Test
    void invokePropagatesTargetException () {
        WebMappedMethod m = wmm ("error", "/a");
        assertThrows (InvocationTargetException.class, () -> m.invoke (new Sample ()));
    }
}

package org.dreamwork.dsi.embedded.httpd.support;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import org.apache.catalina.core.StandardContext;
import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.annotation.WebFilter;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WebComponentScanner} 的单元测试，聚焦于 {@code accept} 的类型判定逻辑。
 */
class WebComponentScannerTest {
    // ------------------------------------------------------
    // 测试夹具
    // ------------------------------------------------------

    @WebFilter ("/*")
    public static class GoodFilter implements Filter {
        @Override
        public void doFilter (ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            chain.doFilter (request, response);
        }
    }

    /** 带 @WebFilter 但没有实现 {@link Filter} 接口，应被拒绝。 */
    @WebFilter ("/*")
    public static class NotAFilter {
    }

    @WebServlet ("/good")
    public static class GoodServlet extends HttpServlet {
    }

    /** 带 @WebServlet 但没有继承 {@link HttpServlet}，应被拒绝。 */
    @WebServlet ("/bad")
    public static class NotAServlet {
    }

    /** 没有任何 Web 注解的普通类。 */
    public static class PlainClass {
    }

    /** 暴露 protected accept 的测试替身。 */
    static class TestableScanner extends WebComponentScanner {
        TestableScanner () {
            super (new StandardContext (), new MultipartConfigElement (""));
        }

        boolean acceptType (Class<?> type) {
            return accept (type);
        }
    }

    // ------------------------------------------------------
    // 测试
    // ------------------------------------------------------

    @Test
    void acceptsWebFilterImplementingFilter () {
        assertTrue (new TestableScanner ().acceptType (GoodFilter.class));
    }

    @Test
    void rejectsWebFilterNotImplementingFilter () {
        assertFalse (new TestableScanner ().acceptType (NotAFilter.class));
    }

    @Test
    void acceptsWebServletExtendingHttpServlet () {
        assertTrue (new TestableScanner ().acceptType (GoodServlet.class));
    }

    @Test
    void rejectsWebServletNotExtendingHttpServlet () {
        assertFalse (new TestableScanner ().acceptType (NotAServlet.class));
    }

    @Test
    void rejectsPlainClass () {
        assertFalse (new TestableScanner ().acceptType (PlainClass.class));
        assertFalse (new TestableScanner ().acceptType (String.class));
    }
}

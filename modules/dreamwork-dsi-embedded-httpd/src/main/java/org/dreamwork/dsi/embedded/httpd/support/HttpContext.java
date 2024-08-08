package org.dreamwork.dsi.embedded.httpd.support;

import org.dreamwork.util.IDisposable;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class HttpContext implements IDisposable {
    private static final ThreadLocal<HttpContext> ref = new ThreadLocal<> ();

    public final ServletContext      context;
    public final HttpServletResponse response;
    public final HttpServletRequest  request;
    private HttpSession session;

    public static HttpContext current () {
        return ref.get ();
    }

    HttpContext (HttpServletRequest request, HttpServletResponse response) {
        ref.set (this);

        this.request  = request;
        this.response = response;
        this.context  = request.getServletContext ();
        this.session  = request.getSession (false);
    }

    public HttpSession getSession (boolean create) {
        if (!create) {
            return session;
        }

        return session == null ? session = request.getSession (true) : session;
    }

    public HttpSession getSession () {
        return session;
    }

    @Override
    public void dispose () {
        ref.remove ();
    }
}

package org.dreamwork.dsi.embedded.httpd.support;

import org.dreamwork.injection.IObjectContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

import static org.dreamwork.dsi.embedded.httpd.support.WebComponentHelper.*;

/**
 * 可注入的 Http Servlet
 * @since 2.1.2
 */
public abstract class InjectableServlet extends HttpServlet {
    private final Logger logger = LoggerFactory.getLogger (InjectableServlet.class);

    protected IObjectContext context;
    private Cache cache;

    @Override
    public void init () throws ServletException {
        super.init ();

        ServletContext webapp = getServletContext ();
        context = (IObjectContext) webapp.getAttribute (IObjectContext.class.getCanonicalName ());

        try {
            cache = parseType (getClass ());
            inject (cache, this);
        } catch (Exception ex) {
            logger.warn (ex.getMessage (), ex);
            throw new ServletException (ex);
        }
    }

    @Override
    public void destroy () {
        if (cache != null && cache.destroyer != null) {
            try {
                cache.destroyer.invoke (this);
            } catch (Exception ex) {
                logger.warn (ex.getMessage (), ex);
                throw new RuntimeException (ex);
            } finally {
                super.destroy ();
            }
        } else {
            super.destroy ();
        }
    }
}
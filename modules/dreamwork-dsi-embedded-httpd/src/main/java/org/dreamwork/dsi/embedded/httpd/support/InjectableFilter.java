package org.dreamwork.dsi.embedded.httpd.support;

import org.dreamwork.injection.IObjectContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;

import static org.dreamwork.dsi.embedded.httpd.support.WebComponentHelper.inject;
import static org.dreamwork.dsi.embedded.httpd.support.WebComponentHelper.parseType;

/**
 * 可注入的 Web Filter
 * @since 2.1.2
 */
@SuppressWarnings ("unused")
public abstract class InjectableFilter implements Filter {
    private final Logger logger = LoggerFactory.getLogger (InjectableFilter.class);

    protected IObjectContext context;

    private Cache cache;

    @Override
    public void init (FilterConfig config) throws ServletException {
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
            }
        }
    }
}
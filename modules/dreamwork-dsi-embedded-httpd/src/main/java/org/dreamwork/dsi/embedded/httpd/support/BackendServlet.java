package org.dreamwork.dsi.embedded.httpd.support;

import com.google.gson.Gson;
import org.apache.catalina.core.ApplicationServletRegistration;
import org.apache.catalina.core.StandardWrapper;
import org.dreamwork.dsi.embedded.httpd.starter.SessionManager;
import org.dreamwork.dsi.embedded.httpd.starter.WebHandlerScanner;
import org.dreamwork.gson.GsonHelper;
import org.dreamwork.injection.IObjectContext;
import org.dreamwork.util.CollectionCreator;
import org.dreamwork.util.IOUtil;
import org.dreamwork.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URLDecoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@WebServlet (loadOnStartup = 1)
public class BackendServlet extends HttpServlet {
    private static final String UTF_8 = "utf-8";
    private static final String KEY_MSA = "X-Managed-Session";
    private final Logger logger = LoggerFactory.getLogger (BackendServlet.class);

    private WebHandlerScanner scanner;

    private IObjectContext context;
    private Servlet defaultServlet, jspServlet;
    private boolean delegateEnabled;
    private String ext;

    private SessionManager manager;

    private static final Pattern STATIC_RESOURCES = Pattern.compile (
            "^(.*?)\\.(htm|html|xml|png|jpg|jpeg|gif|js|css|mp3|mp4)$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern JAVA_PAGE = Pattern.compile ("^(.*?)\\.(jsp|jspx)", Pattern.CASE_INSENSITIVE);

    @Override
    public void init () throws ServletException {
        super.init ();

        ServletContext app = getServletContext ();
        context = (IObjectContext) app.getAttribute (IObjectContext.class.getCanonicalName ());
        scanner = context.getBean (WebHandlerScanner.class);
        manager = context.getBean (SessionManager.class);

        ext = (String) app.getAttribute ("embedded.httpd.views.extension");
        if (StringUtil.isEmpty (ext)) {
            ext = ".jsp";
        } else {
            ext = ext.trim ();
            if (ext.charAt (0) != '.') {
                ext = '.' + ext;
            }
        }
        if (logger.isTraceEnabled ()) {
            logger.trace ("the view's extension = {}", ext);
            logger.trace ("checking for whether delegate the default servlet....");
            logger.trace ("    checking system property, key = dreamwork.dsi.embedded.httpd.delegate");
        }
        String prop = System.getProperty ("dreamwork.dsi.embedded.httpd.delegate");
        if (logger.isTraceEnabled ()) {
            logger.trace ("                            value = {}", prop);
        }
        delegateEnabled = "true".equals (prop);
        if (!delegateEnabled) {
            if (logger.isTraceEnabled ()) {
                logger.trace ("    delegate not enable by system property");
                logger.trace ("    checking servlet context, key = embedded.httpd.delegate.enabled");
            }
            Boolean enabled = (Boolean) app.getAttribute ("embedded.httpd.delegate.enabled");
            delegateEnabled = enabled != null && enabled;
            if (logger.isTraceEnabled ()) {
                logger.trace ("                            value = {}", enabled);
                logger.trace ("                 delegate enabled = {}", delegateEnabled);
            }
        }

        if (delegateEnabled) {
            try {
                Class<ApplicationServletRegistration> type = ApplicationServletRegistration.class;
                Field field = type.getDeclaredField ("wrapper");
                if (!field.isAccessible ()) {
                    field.setAccessible (true);
                }
                Map<String, ? extends ServletRegistration> mappings = getServletContext ().getServletRegistrations ();
                ServletRegistration base = mappings.get ("default");
                StandardWrapper wrapper = (StandardWrapper) field.get (base);
                defaultServlet = wrapper.getServlet ();

                ServletRegistration jsp = mappings.get ("jsp");
                wrapper = (StandardWrapper) field.get (jsp);
                jspServlet = wrapper.getServlet ();
            } catch (Exception ex) {
                throw new ServletException (ex);
            }
        }

        logger.info ("backend servlet initialed.");
    }

    @Override
    public void destroy () {
        if (manager != null) {
            manager.stopMonitor ();
        }
        super.destroy ();
    }

    @Override
    protected void service (HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String method   = request.getMethod ();
        String pathInfo = request.getPathInfo ();
        if (logger.isTraceEnabled ()) {
            logger.trace ("pathInfo = {}", pathInfo);
        }
        if (StringUtil.isEmpty (pathInfo)) {
            response.setStatus (HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        Matcher m = STATIC_RESOURCES.matcher (pathInfo);
        if (m.matches ()) {
            if (logger.isTraceEnabled ()) {
                logger.trace ("request to a static resource: {}", pathInfo);
            }
            if (defaultServlet != null) {
                defaultServlet.service (request, response);
                return;
            }
        }

        m = JAVA_PAGE.matcher (pathInfo);
        if (m.matches ()) {
            if (logger.isTraceEnabled ()) {
                logger.trace ("request to a java server page: {}", pathInfo);
            }
            if (jspServlet != null) {
                jspServlet.service (request, response);
                return;
            }
        }

        Map<String, String> values = new HashMap<> ();
        WebHandler handler;
        try {
            handler = scanner.match (pathInfo, method.toLowerCase (), values);
        } catch (ServletException ex) {
            logger.warn (ex.getMessage (), ex);
            response.setStatus (HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }

        if (handler == null) {
            if (delegateEnabled) {
                if (pathInfo.endsWith (".jsp")) {
                    if (jspServlet != null) {
                        jspServlet.service (request, response);
                    }
                } else if (defaultServlet != null) {
                    defaultServlet.service (request, response);
                } else {
                    response.setStatus (HttpServletResponse.SC_NOT_FOUND);
                }
            } else {
                response.setStatus (HttpServletResponse.SC_NOT_FOUND);
            }
            return;
        }

        Object bean = context.getBean (handler.beanName);
        Object value;
        Gson g = new Gson ();
        HttpContext ctx = null;
        try {
            request.getSession ().getId ();
            ctx = new HttpContext (request, response);
            response.setContentType (handler.method.contentType);

            if (handler.method.parameters == null) {
                value = handler.method.invoke (bean);
            } else {
                Object[] args = parseParameters (request, response, handler, values);
                value = handler.method.invoke (bean, args);
            }

            if (handler.type == HandlerType.DISPATCHER) {
                if (value != null && CharSequence.class.isAssignableFrom (value.getClass ())) {
                    String path = value.toString ();
                    if (path.charAt (0) != '/') {
                        path = '/' + path;
                    }
                    if (!path.contains (".") && !path.endsWith (ext)) {
                        path += ext;
                    }
                    RequestDispatcher dispatcher = request.getRequestDispatcher (path);
                    dispatcher.forward (request, response);
                    return;
                }
            }

            String contentType = response.getContentType ();
            if (contentType.contains ("json")) {
                if (handler.wrapped) {
                    // since 1.1.0 自动包裹固定结构的json
                    WebJsonResult wjr = new WebJsonResult (0, "success", value);
                    response.getWriter ().write (g.toJson (wjr));
                } else if (value != null) {
                    // since 1.1.0 输出原始数据的 json 格式
                    response.getWriter ().write (g.toJson (value));
                }
            } else if (value != null) {
                response.getWriter ().write (value.toString ());
            }
        } catch (InvocationTargetException ite) {
            Throwable t = ite.getCause ();
            response.reset ();
            response.setContentType (handler.method.contentType);
            if (handler.method.contentType.contains ("json")) {
                if (t instanceof WebHandlerException) {
                    WebHandlerException whe = (WebHandlerException) t;
                    if (whe.httpStatus >= 200 && whe.httpStatus < 600) {
                        response.setStatus (whe.httpStatus);
                    }
                    WebJsonResult wjr = new WebJsonResult (whe.code, whe.getMessage (), null);
                    response.getWriter ().write (g.toJson (wjr));
                } else {
                    throw new ServletException (t);
                }
            } else {
                if (t instanceof WebHandlerException) {
                    WebHandlerException whe = (WebHandlerException) t;
                    if (whe.httpStatus >= 200 && whe.httpStatus < 600) {
                        response.setStatus (whe.httpStatus);
                    }
                    response.getWriter ().write (whe.getMessage ());
                } else {
                    throw new ServletException (t);
                }
            }
        } catch (RuntimeException re) {
            logger.warn (re.getMessage (), re);
            response.reset ();
            response.setContentType (handler.method.contentType);
            if (handler.method.contentType.contains ("json")) {
                response.setStatus (HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter ().write (g.toJson (
                        CollectionCreator.asMap (
                                "code", HttpServletResponse.SC_BAD_REQUEST,
                                "error", re.getMessage ()
                        )
                ));
            }
        } finally {
            if (ctx != null) {
                ctx.dispose ();
            }
        }
    }

    private Object[] parseParameters (HttpServletRequest request, HttpServletResponse response,
                                      WebHandler handler, Map<String, String> values) throws IOException {
        int n = handler.method.parameters.size ();
        Object[] args = new Object[n];
        Class<?>[] types = handler.method.method.getParameterTypes ();
        // @since 1.1.1
        String key = request.getHeader (KEY_MSA);
        ManagedSession session = null;
        if (!StringUtil.isEmpty (key)) {
            session = manager.get (key);
            response.setHeader (KEY_MSA, key);
        }

        Map<String, String> paramsMap = new HashMap<> ();
        boolean patched = false;

        for (int i = 0; i < n; i ++) {
            WebParameter wp = handler.method.parameters.get (i);
            Class<?> type = types [i];
            if (wp == null || wp.internal) {
                if (type == HttpContext.class) {
                    args[i] = HttpContext.current ();
                } else if (type == ServletContext.class) {
                    args[i] = getServletContext ();
                } else if (type == HttpServletRequest.class) {
                    args[i] = request;
                } else if (type == HttpServletResponse.class) {
                    args[i] = response;
                } else if (type == HttpSession.class) {
                    args[i] = request.getSession ();
                } else if (type == ManagedSession.class) {
                    // @since 1.1.1
                    if (session == null) {
                        // session 还未创建，创建一个
                        session = manager.create (key);
                        response.setHeader (KEY_MSA, session.id);
                    }
                    args[i] = session;
                } else {
                    throw new IllegalArgumentException ("unsupported internal type: " + type);
                }
/*
            } else if (wp.internal) {
                if (type == HttpContext.class) {
                    args[i] = HttpContext.current ();
                } else if (type == ServletContext.class) {
                    args[i] = getServletContext ();
                } else if (type == HttpServletRequest.class) {
                    args[i] = request;
                } else if (type == HttpServletResponse.class) {
                    args[i] = response;
                } else if (type == HttpSession.class) {
                    args[i] = request.getSession ();
                } else {
                    throw new IllegalArgumentException ("unsupported internal type: " + type);
                }
*/
            } else {
                String temp;
                switch (wp.location) {
                    case QueryString:
                        if (!patched) {
                            patchPUTParameters (request, paramsMap);
                            patched = true;
                        }
//                        temp = request.getParameter (wp.name);
                        temp = paramsMap.get (wp.name);
                        if (StringUtil.isEmpty (temp)) {
                            if (!StringUtil.isEmpty (wp.defaultValue)) {
                                temp = wp.defaultValue;
                            }
                        }
                        break;
                    case Body:
                        String contentType = request.getContentType ();
                        if (contentType.contains ("json") || contentType.contains ("text/plain")) {
                            temp = new String (IOUtil.read (request.getInputStream ()));
                        } else {
//                            temp = request.getParameter (wp.name);
                            temp = paramsMap.get (wp.name);
                        }
                        break;
                    case Path:
                        temp = values.get (wp.name);
                        break;
                    case Header:
                        temp = request.getHeader (wp.name);
                        break;
                    // @since 1.1.0
                    case Internal:
                        temp = "0";
                        break;

                    default:
                        throw new IllegalArgumentException ("unknown location: " + wp.location);
                }

                Object o;
                switch (wp.type) {
                    case string:
                        args[i] = temp;
                        break;

                    case integer:
                        args[i] = Integer.parseInt (temp);
                        break;

                    case long_integer:
                        args[i] = Long.parseLong (temp);
                        break;

                    case bool:
                        args[i] = Boolean.parseBoolean (temp);
                        break;

                    case datetime:
                        if (!StringUtil.isEmpty (temp)) {
                            try {
                                args[i] = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss").parse (temp);
                            } catch (ParseException pe) {
                                try {
                                    args[i] = new SimpleDateFormat ("yyyy-MM-dd").parse (temp);
                                } catch (ParseException ex) {
                                    throw new RuntimeException (ex);
                                }
                            }
                        }
                        break;

                    case raw:
                        Class<?> parameterType = handler.method.method.getParameterTypes ()[i];
                        args[i] = translate (temp, parameterType);
                        break;
                    // @since 1.1.0
                    case request_attribute:
                        o = request.getAttribute (wp.name);
                        if (o == null && !wp.nullable) {
                            throw new RuntimeException ("parameter [request." + wp.name + "] needs value, but meet null!");
                        }
                        args[i] = o;
                        break;
                    // @since 1.1.0
                    case session_attribute:
                        o = request.getSession ().getAttribute (wp.name);
                        if (o == null && !wp.nullable) {
                            throw new RuntimeException ("parameter [session." + wp.name + "] needs value, but meet null!");
                        }
                        args[i] = o;
                        break;

                    // @since 1.1.1
                    case managed_session_attribute:
                        if (session == null) {
                            // 已经获取过了，但是没有，说明还未创建，创建一个
                            session = manager.create (key);
                            response.setHeader (KEY_MSA, session.id);
                        }
                        o = session.get (wp.name);
                        if (o == null && !wp.nullable) {
                            throw new RuntimeException ("parameter [MSA." + wp.name + "] needs value, but meet null!");
                        }
                        args[i] = o;
                        break;
                }
            }
        }
        return args;
    }

    private static final Pattern P_TRUE = Pattern.compile ("^t|y|yes|on$", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_FALSE = Pattern.compile ("^f|n|no|off$", Pattern.CASE_INSENSITIVE);

    private Object translate (String expression, Class<?> type) {

        if (type == int.class || (type == Integer.class && !StringUtil.isEmpty (expression))) {
            return Integer.parseInt (expression);
        }
        if (type == byte.class || type == Byte.class) {
            return Byte.parseByte (expression);
        }
        if (type == char.class || (type == Character.class && !StringUtil.isEmpty (expression))) {
            return expression.isEmpty () ? '\u0000' : expression.charAt (0);
        }
        if (type == short.class || (type == Short.class && !StringUtil.isEmpty (expression))) {
            return Short.parseShort (expression);
        }
        if (type == long.class || (type == Long.class && !StringUtil.isEmpty (expression))) {
            return Long.parseLong (expression);
        }
        if (type == boolean.class || type == Boolean.class) {
            try {
                return Boolean.parseBoolean (expression);
            } catch (Exception ex) {
                if (!StringUtil.isEmpty (expression)) {
                    Matcher m = P_TRUE.matcher (expression);
                    if (m.matches ()) {
                        return true;
                    }
                    m = P_FALSE.matcher (expression);
                    if (m.matches ()) {
                        return false;
                    }
                }
                throw new NumberFormatException ("cannot convert " + expression + " to boolean");
            }
        }
        if (type == float.class || (type == Float.class && !StringUtil.isEmpty (expression))) {
            return Float.parseFloat (expression);
        }
        if (type == double.class || (type == Double.class && !StringUtil.isEmpty (expression))) {
            return Double.parseDouble (expression);
        }
        if (type.isAssignableFrom (String.class)) {
            return expression;
        }
        if (type == BigDecimal.class && !StringUtil.isEmpty (expression)) {
            return new BigDecimal (expression);
        }
        if (type == BigInteger.class && !StringUtil.isEmpty (expression)) {
            return new BigInteger (expression);
        }
        if (type == Date.class && !StringUtil.isEmpty (expression)) {
            return toDate (expression);
        }
        if (type == java.sql.Date.class && !StringUtil.isEmpty (expression)) {
            return new java.sql.Date (toDate (expression).getTime ());
        }
        if (type == java.sql.Timestamp.class && !StringUtil.isEmpty (expression)) {
            return new java.sql.Timestamp (toDate (expression).getTime ());
        }
        return StringUtil.isEmpty (expression) ? null : GsonHelper.getGson ().fromJson (expression, type);
    }

    private Date toDate (String expression) {
        try {
            return new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss").parse (expression);
        } catch (ParseException ex) {
            try {
                return new SimpleDateFormat ("yyyy-MM-dd").parse (expression);
            } catch (ParseException e) {
                throw new RuntimeException (e);
            }
        }
    }

    private void urlDecode (String body, Map<String, String> map) throws IOException {
        String[] array = body.split ("&");
        for (String pair : array) {
            if (pair.contains ("=")) {
                String[] parts = pair.trim ().split ("=");
                String name = URLDecoder.decode (parts[0].trim (), UTF_8);
                if (!StringUtil.isEmpty (parts[1])) {
                    String value = URLDecoder.decode (parts[1].trim (), UTF_8);
                    map.put (name, value);
                } else {
                    map.put (name, null);
                }
            } else {
                map.put (URLDecoder.decode (pair.trim (), UTF_8), null);
            }
        }
    }

    private void patchPUTParameters (HttpServletRequest request, Map<String, String> map) throws IOException {
        String contentType = request.getContentType ().toLowerCase ();
        String method = request.getMethod ().toLowerCase ();
        if ("get".equals (method) || "post".equals (method)) {
            Enumeration<String> en = request.getParameterNames ();
            while (en.hasMoreElements ()) {
                String name = en.nextElement ();
                map.put (name, request.getParameter (name));
            }
        } else if ("put".equals (method) && !StringUtil.isEmpty (contentType) && contentType.contains ("application/x-www-form-urlencoded")) {
            String query = request.getQueryString ();
            if (!StringUtil.isEmpty (query)) {
                urlDecode (query, map);
            }
            String body = new String (IOUtil.read (request.getInputStream ()));
            if (!StringUtil.isEmpty (body)) {
                urlDecode (body, map);
            }
        }
    }
}

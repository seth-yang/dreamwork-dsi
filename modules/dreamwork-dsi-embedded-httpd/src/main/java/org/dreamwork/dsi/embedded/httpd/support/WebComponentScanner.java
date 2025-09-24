package org.dreamwork.dsi.embedded.httpd.support;

import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.dreamwork.injection.impl.ClassScanner;
import org.dreamwork.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Filter;
import javax.servlet.annotation.WebFilter;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;

import static org.dreamwork.util.CollectionHelper.isNotEmpty;

public class WebComponentScanner extends ClassScanner {
    private final Logger logger = LoggerFactory.getLogger (WebComponentScanner.class);

    private final StandardContext web;
    private final ClassLoader loader;

    public WebComponentScanner (StandardContext web) {
        this.web = web;
        loader = getClass ().getClassLoader ();
    }
    /**
     * 当扫描器扫描到一个类时调用这个方法来验证是否是所需的，若是，则触发 {@link #onFound(String, Class, Set)} 事件
     *
     * @param type java 类
     * @return 如果是所需的返回 {@code true}，否在返回 {@code false}
     */
    @Override
    protected boolean accept (Class<?> type) {
        if (type.isAnnotationPresent (WebFilter.class)) {
            if (Filter.class.isAssignableFrom (type)) {
                return true;
            }
        }
        if (type.isAnnotationPresent (WebServlet.class)) {
            return HttpServlet.class.isAssignableFrom (type);
        }
        return false;
    }

    /**
     * 当扫描器找到所需的类时触发该事件。
     *
     * <p>该方法的实现应该处理这个事件。
     * 若当即无法处理的逻辑，比如 <i><strong>{@code 依赖注入}</strong></i> 等，需要所有扫描结果出来后再处理的，
     * 可以为处理结果创建一个包裹类 {@link Wrapper} 放在 {@code wrappers} 出参里，
     * 等到 {@link #onCompleted(Set)} 事件来统一处理
     * </p>
     *
     * @param name     类的简短名称
     * @param type     java 类型
     * @param wrappers 包裹类
     * @see #onCompleted(Set)
     */
    @Override
    protected void onFound (String name, Class<?> type, Set<Wrapper> wrappers) {
        if (type.isAnnotationPresent (WebServlet.class)) {
            mapServlet (type);
        } else if (type.isAnnotationPresent (WebFilter.class)) {
            mapFilter (type);
        }
    }

    @Override
    protected void onCompleted (Set<Wrapper> wrappers) {}

    private void mapServlet (Class<?> type) {
        if (logger.isTraceEnabled ()) {
            logger.trace ("mapping servlet :: {}", type);
        }
        WebServlet servlet = type.getAnnotation (WebServlet.class);
        String servletName = getServletName (servlet, type);
        org.apache.catalina.Wrapper w = Tomcat.addServlet (web, servletName, type.getCanonicalName ());
        w.setParentClassLoader (loader);
        String[] mappings = servlet.urlPatterns ();
        if (mappings == null || mappings.length == 0) {
            mappings = servlet.value ();
        }
        Arrays.stream (mappings).forEach (w::addMapping);
        WebInitParam[] params = servlet.initParams ();
        if (isNotEmpty (params)) {
            for (WebInitParam p : params) {
                w.addInitParameter (p.name (), p.value ());
            }
        }
        w.setLoadOnStartup (servlet.loadOnStartup ());
    }

    private void mapFilter (Class<?> type) {
        if (logger.isTraceEnabled ()) {
            logger.trace ("mapping filter :: {}", type);
        }
        WebFilter filter = type.getAnnotation (WebFilter.class);
        FilterDef def = new FilterDef ();
        def.setFilterClass (type.getCanonicalName ());
        String[] mappings = filter.urlPatterns ();
        if (mappings == null || mappings.length == 0) {
            mappings = filter.value ();
        }
        WebInitParam[] params = filter.initParams ();
        if (params != null && params.length > 0) {
            Arrays.stream (params).forEach (p -> def.addInitParameter (p.name (), p.value ()));
        }
        String name = getFilterName (filter, type);
        def.setFilterName (name);
        web.addFilterDef (def);

        FilterMap mapper = new FilterMap ();
        mapper.setFilterName (name);
        mapper.setCharset (StandardCharsets.UTF_8);
        for (String mapping : mappings) {
            mapper.addURLPattern (mapping);
        }
        web.addFilterMap (mapper);
    }

    private String getServletName (WebServlet servlet, Class<?> type) {
        return findName (servlet.name (), servlet.displayName (), type, "Servlet");
    }

    private String getFilterName (WebFilter filter, Class<?> type) {
        return findName (filter.filterName (), filter.displayName (), type, "Filter");
    }

    private String findName (String first, String second, Class<?> type, String suffix) {
        String name = first;
        if (StringUtil.isEmpty (name)) {
            name = second;
        }
        if (StringUtil.isEmpty (name)) {
            name = type.getSimpleName ();
            name = Character.toLowerCase (name.charAt (0)) + name.substring (1);
            if (!name.toLowerCase ().endsWith (suffix.toLowerCase ())) {
                name += suffix;
            }
        }

        return name;
    }
}
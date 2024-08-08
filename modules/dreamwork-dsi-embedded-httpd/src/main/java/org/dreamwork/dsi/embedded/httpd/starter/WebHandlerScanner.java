package org.dreamwork.dsi.embedded.httpd.starter;

import org.dreamwork.dsi.embedded.httpd.annotation.AWebHandler;
import org.dreamwork.dsi.embedded.httpd.annotation.AWebMapping;
import org.dreamwork.dsi.embedded.httpd.support.WebHandler;
import org.dreamwork.dsi.embedded.httpd.support.WebMappedMethod;
import org.dreamwork.injection.IInjectResolvedProcessor;
import org.dreamwork.injection.IObjectContext;
import org.dreamwork.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import javax.servlet.ServletException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

@Resource
public class WebHandlerScanner implements IInjectResolvedProcessor {
    private final Map<String, Map<String, WebMappedMethod>> mappings = new HashMap<> ();
//    private final Pattern PATTERN = Pattern.compile ("^/?(.*?)(/(.*?))?$");
    private final Logger logger   = LoggerFactory.getLogger (WebHandlerScanner.class);

    @Override
    public void perform (IObjectContext context) /*throws Exception */{
        for (String beanName : context.getAllBeanNames ()) {
            Object bean = context.getBean (beanName);
            Class<?> type = bean.getClass ();
            if (type.isAnnotationPresent (AWebHandler.class)) {
                if (logger.isTraceEnabled ()) {
                    logger.trace ("found a web handler: {}", type.getCanonicalName ());
                }
                AWebHandler awh = type.getAnnotation (AWebHandler.class);
                String[] categories = awh.pattern ();
                if (categories == null || categories.length == 0) {
                    categories = awh.value ();
                }
                if (categories == null || categories.length == 0) {
                    throw new IllegalArgumentException ("Invalid category");
                }

                Method[] methods = type.getMethods ();
                for (Method method : methods) {
                    if (method.isAnnotationPresent (AWebMapping.class)) {
                        AWebMapping awm = method.getAnnotation (AWebMapping.class);
                        String httpMethod = awm.method ();
                        if (StringUtil.isEmpty (httpMethod)) {
                            httpMethod = "get";
                        }

                        String[] patterns = awm.pattern ();
                        if (patterns.length == 0) {
                            patterns = awm.value ();
                        }
                        if (patterns == null || patterns.length == 0) {
                            continue;
                        }

                        for (String category : categories) {
                            for (String pattern : patterns) {
                                if (StringUtil.isEmpty (pattern)) {
                                    pattern = "/";
                                }

                                String pathInfo = '/' + category + '/' + pattern;
                                while (pathInfo.contains ("//")) {
                                    pathInfo = pathInfo.replace ("//", "/");
                                }
                                WebMappedMethod wmm = new WebMappedMethod (method, pathInfo, awh.type ());
                                wmm.beanName = beanName;
                                wmm.contentType = awm.contentType ();
                                // @since 1.1.0
                                // 标记是否自动包裹结果为固定结构的json
                                if (awm.wrapped ()) {
                                    wmm.wrapped = true;
                                } else {
                                    wmm.wrapped = awh.wrapped ();
                                }
                                String key = httpMethod.toLowerCase ();

                                Map<String, WebMappedMethod> map = mappings.computeIfAbsent (key, name -> new HashMap<> ());
                                if (map.containsKey (pathInfo)) {
                                    throw new IllegalArgumentException ("pattern " + pathInfo + " already mapped.");
                                }
                                map.put (pathInfo, wmm);
                                if (logger.isTraceEnabled ()) {
                                    logger.trace ("a web mapped method is mapped: {} <=> {}", pathInfo, wmm.method);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public WebHandler match (String pathInfo, String method, Map<String, String> parsedArgs) throws ServletException {
        Map<String, WebMappedMethod> map = mappings.get (method);
        if (map == null) {
            throw new ServletException ("Method " + method + " not supported.");
        }
        if (map.containsKey (pathInfo)) {
            WebMappedMethod wmm = map.get (pathInfo);
            return new WebHandler (wmm.beanName, wmm);
        }

        for (WebMappedMethod wmm : map.values ()) {
            if (wmm.matches (pathInfo, parsedArgs)) {        // 更复杂的情况
                return new WebHandler (wmm.beanName, wmm);
            }
        }
        return null;
    }
}

package org.dreamwork.dsi.embedded.httpd.support;

import org.dreamwork.dsi.embedded.httpd.starter.EmbeddedTomcatStarter;
import org.dreamwork.injection.IObjectContext;
import org.dreamwork.injection.impl.ClassScanner;
import org.dreamwork.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import java.util.Arrays;
import java.util.Set;

/**
 * @deprecated replaced by {@link WebComponentScanner}
 */
@Deprecated
public class WebResourceScanner extends ClassScanner {
    private final Logger logger = LoggerFactory.getLogger (WebResourceScanner.class);

    private final IObjectContext context;

    public WebResourceScanner (IObjectContext context) {
        this.context = context;
    }
    /**
     * 当扫描器扫描到一个类时调用这个方法来验证是否是所需的，若是，则触发 {@link #onFound(String, Class, Set)} 事件
     *
     * @param type java 类
     * @return 如果是所需的返回 {@code true}，否在返回 {@code false}
     */
    @Override
    protected boolean accept (Class<?> type) {
        return HttpServlet.class.isAssignableFrom (type) && type.isAnnotationPresent (WebServlet.class);
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
        Wrapper w = new Wrapper ();
        w.type = type;
        wrappers.add (w);
    }

    /**
     * 当所有包都扫描完成，扫描器将触发这个事件.
     *
     * <p>如果在 {@link #onFound(String, Class, Set)} 事件中有未处理完的逻辑可以在这个事件中完成。</p>
     *
     * @param wrappers 包裹类集合
     * @see #onFound(String, Class, Set)
     */
    @Override
    protected void onCompleted (Set<Wrapper> wrappers) {
        if (!wrappers.isEmpty ()) {
            EmbeddedTomcatStarter tomcat = context.getBean (EmbeddedTomcatStarter.class);

            wrappers.stream ()
                    .map (w -> w.type)
                    .forEach (type -> map (type, tomcat));
        }
    }

    @SuppressWarnings ("unchecked")
    private void map (Class<?> type, EmbeddedTomcatStarter tomcat) {
        WebServlet servlet = type.getAnnotation (WebServlet.class);
        String name = servlet.name ();
        String[] patterns = servlet.urlPatterns ();
        if (patterns != null && patterns.length > 0) {
            if (StringUtil.isEmpty (name)) {
                name = type.getSimpleName () + "Servlet";
            }
            if (logger.isTraceEnabled ()) {
                logger.trace ("adding servlet: {} as {}, mapping to {}", type.getCanonicalName (), name, Arrays.toString (patterns));
            }
            tomcat.mapServlet (name, (Class<? extends HttpServlet>) type, patterns);
        }
    }
}
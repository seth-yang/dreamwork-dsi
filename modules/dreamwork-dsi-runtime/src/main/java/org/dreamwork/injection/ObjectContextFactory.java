package org.dreamwork.injection;

import org.dreamwork.injection.impl.SimpleObjectContextFactory;

/**
 * 对象托管容器的工厂
 *
 * <p>这个工厂类负责初始化容器，解析配置，启动扫描</p>
 * <pre>
 * package org.example.app;
 *
 * &#64;AInjectionContext
 * public class MyAppMain {
 *     public static void main (String[] args) throws Exception {
 *         ObjectContextFactory.start (MyAppMain.class, args);
 *     }
 * }
 * </pre>
 * 扫描器将自动扫描 {@code org.example.app} 包下的所有类，所有带有 <strong>托管标注</strong> 的类
 * 都将自动被配置到托管容器内。
 * <p>如果希望扫描其他包，使用 {@link AInjectionContext#scanPackages() scanPackages} 属性来添加额外的包扫描</p>
 * <p>在默认情况下，扫描器 <strong>不会</strong> 递归扫描，若希望递归扫描，设置 {@link AInjectionContext#recursive() recursive}
 * 属性的值为 {@code true} 来激活递归扫描
 * </p>
 *
 * <h2>受托管对象</h2>
 * {@link javax.annotation.Resource} 标注用来表示一个对象/资源是 <strong><i>受托管</i></strong> 的。 托管容器将自动
 * 配置和管理这些对象实例。当 {@link javax.annotation.Resource} 用来标注: <ul>
 * <li>一个类时，意味着这个类将被作为受托管资源配置到托管容器内</li>
 * <li>一个 {@code java getter} 时，意味着这个 getter 的返回值将被自动配置到容器内</li>
 * <li>一个 {@code java setter} 时，意味着从容器内获取对应的资源并作为 setter 的参数注入到对象内</li>
 * <li>一个 {@code 字段} 时，意味着从容器内获取对应的资源输入到这个字段</li>
 * </ul>
 * 每个被 {@link javax.annotation.Resource} 标注的类，<strong>最多有一个</strong>方法被<ul>
 * <li>{@link javax.annotation.PostConstruct} 标注，意味着这个类在被配置到容器后将自动调用。这个方法的
 * 签名必须是<pre>public void &lt;methodName&gt; ()</pre></li>
 * <li>{@link javax.annotation.PreDestroy} 标注，意味着这个类从容器内删除前将自动调用。这个方法的签名必须是
 * <pre>public void &lt;methodName&gt; ()</pre></li>
 * </ul>
 *
 * <h2>简单的容器生命周期</h2>
 * 当容器解决了所有的依赖注入后，会调用所有实现了 {@link IInjectResolvedProcessor} 接口
 * 的实例的 {@link IInjectResolvedProcessor#perform(IObjectContext) perform} 方法。如果希望
 * 多个实现的运行顺序，应当实现 {@link IInjectResolvedProcessor#getOrder() getOrder} 方法，容器将
 * 按照这个方法的返回值排序，并按顺序调用，例如：<pre>
 * package org.example.app;
 * &#64;Resource
 * public class MyProcessor implements IInjectResolvedProcessor {
 *     &#64;Override
 *     public void perform (IObjectContext ctx) throws Exception {
 *         for (String name : ctx.getAllBeanNames ()) {
 *             System.out.println (name);
 *         }
 *     }
 * }
 * </pre>
 *
 * <h2>自动装配</h2>
 * 对象托管容器的工厂还允许用户代码自动装配额外的托管对象。
 * 工厂会扫描类路劲下的 /META-INF/dsi-*.properties 文件来获取自动装配的信息
 *
 * @author seth.yang
 * @since 3.1.1
 */
@SuppressWarnings ("unused")
public class ObjectContextFactory {
    /**
     * 对象托管容器的工厂方法
     *
     * @param type 被 {@link AInjectionContext} 标注的类，用于提供托管容器的配置信息
     * @param args 命令行参数
     * @return 托管容器的实例
     * @throws Exception 任何异常
     */
    public static IObjectContext start (Class<?> type, String... args) throws Exception {
        if (!type.isAnnotationPresent (AInjectionContext.class)) {
            throw new TypeNotPresentException (AInjectionContext.class.getName (), null);
        }

        return SimpleObjectContextFactory.start (type, args);
    }
}
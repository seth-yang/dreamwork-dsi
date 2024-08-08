package org.dreamwork.injection.impl;

import org.dreamwork.config.IConfiguration;
import org.dreamwork.injection.AConfigured;
import org.dreamwork.injection.IInjectResolvedProcessor;
import org.dreamwork.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.management.IntrospectionException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * 受托管对象的扫描器
 */
public class ObjectContextScanner extends ClassScanner {
    private final Logger logger = LoggerFactory.getLogger (ObjectContextScanner.class);

    private final SimpleObjectContext context;

    public ObjectContextScanner (SimpleObjectContext context) {
        this.context = context;
    }

    @Override
    protected boolean accept (Class<?> type) {
        return type.isAnnotationPresent (Resource.class);
    }

    @Override
    protected void onFound (String name, Class<?> type, Set<Wrapper> wrappers) throws Exception {
        String beanName = Character.toLowerCase (name.charAt (0)) + name.substring (1);
        Object bean = type.newInstance ();
        context.register (beanName, bean);

        Wrapper w = new Wrapper ();
        w.type = type;
        w.bean = bean;

        findInjectField (type, w);
        findMethods (type, w);

        if (!w.configuredFields.isEmpty ()) {
            IConfiguration conf = context.getBean (IConfiguration.class);
            if (conf != null) {
                SimpleObjectContext.configureFields (conf, bean, w.configuredFields);
            }
        }

        if (!w.exposeMethods.isEmpty ()) {
            for (MethodWrapper mw : w.exposeMethods) {
                String exposeName = mw.name;
                if (StringUtil.isEmpty (exposeName)) {
                    Class<?> rt = mw.method.getReturnType ();
                    exposeName = rt.getSimpleName ();
                    exposeName = Character.toLowerCase (exposeName.charAt (0)) + exposeName.substring (1);
                }
                if (!mw.method.isAccessible ()) {
                    mw.method.setAccessible (true);
                }
                Object o = mw.method.invoke (bean);
                if (o == null) {
                    throw new IntrospectionException ("method " + mw.method + " returns a null object!");
                }
                context.register (exposeName, o);
            }

            w.exposeMethods.clear ();
        }

        // 判断这个类是否实现了容器注入完成处理器
        w.isProcessor = IInjectResolvedProcessor.class.isAssignableFrom (type);

        if (w.postConstruct != null || !w.injectMethods.isEmpty () || !w.injectFields.isEmpty () || w.isProcessor) {
            wrappers.add (w);
        }
    }

    @Override
    protected void onCompleted (Set<Wrapper> wrappers) throws Exception {
        context.resolve (wrappers);
    }

    /**
     * 查找所有需要注入的字段
     * @param type    java 类型
     * @param wrapper 包裹类
     */
    private void findInjectField (Class<?> type, Wrapper wrapper) {
        while (type != null && type != Object.class) {
            Field[] fields = type.getDeclaredFields ();
            for (Field field : fields) {
                Class<?> ft = field.getType ();
                if (field.isAnnotationPresent (Resource.class) && !ft.isPrimitive ()) {
                    wrapper.injectFields.add (field);
                } else if (field.isAnnotationPresent (AConfigured.class)) {
                    wrapper.configuredFields.add (field);
                }
            }

            type = type.getSuperclass ();
        }
    }

    /**
     * 查找所有需要 注入 / 注册 / 预处理 的方法
     * @param type    java 类型
     * @param wrapper 包裹类
     * @throws IntrospectionException 内省异常.
     * <ul>
     * <li>仅标准的 {@code java setter} 允许被标注为 <strong>自动注入</strong></li>
     * <li>仅标准的 {@code java getter} 允许被标注为 <strong>自动注册</strong></li>
     * <li>仅签名为 {@code void &lt;method-name&gt; ()} 的 <strong>实例</strong> 方法允许被标注为预处理方法</li>
     * </ul>
     * 若违反了以上规则，将抛出 {@link IntrospectionException} 异常
     */
    private void findMethods (Class<?> type, Wrapper wrapper) throws IntrospectionException {
        Method[] methods = type.getMethods ();
        for (Method method : methods) {
            if (method.isAnnotationPresent (Resource.class)) {
                if (method.isSynthetic ()) {
                    // 合成方法会被跳过，不管是否有 @Resource 注解
                    continue;
                }
                String name = method.getName ();
                Resource res = method.getAnnotation (Resource.class);

                if (name.startsWith ("set")) {  // setter
                    Class<?>[] pts = method.getParameterTypes ();
                    if (pts.length != 1) {
                        throw new IntrospectionException ("a method annotated as Resource can ONLY have ONE parameter");
                    }
                    MethodWrapper mw = new MethodWrapper ();
                    mw.method = method;

                    if (!StringUtil.isEmpty (res.name ()))
                        mw.name = res.name ().trim ();
                    wrapper.injectMethods.add (mw);
                } else if (name.startsWith ("get")) {   // getter
                    if (method.getReturnType () == void.class || method.getReturnType () == Void.class) {
                        throw new IntrospectionException ("a method annotated as exposed resource MUST return something");
                    }
                    if (method.getParameterCount () != 0) {
                        throw new IntrospectionException ("a method annotated as exposed resource cannot contains any parameters");
                    }
                    MethodWrapper mw = new MethodWrapper ();
                    mw.method = method;
                    if (!StringUtil.isEmpty (res.name ())) {
                        mw.name = res.name ();
                    } else if (!StringUtil.isEmpty (res.mappedName ())) {
                        mw.name = res.mappedName ();
                    }
                    wrapper.exposeMethods.add (mw);
                }
            } else if (method.isAnnotationPresent (PostConstruct.class)) {  // 预加载的方法
                if (wrapper.postConstruct != null) {
                    throw new IntrospectionException ("a class can ONLY have ONE method annotated PostConstruct!");
                }
                wrapper.postConstruct = method;
            }
        }
    }
}

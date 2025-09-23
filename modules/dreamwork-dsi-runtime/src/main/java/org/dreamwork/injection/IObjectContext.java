package org.dreamwork.injection;

import org.dreamwork.util.IDisposable;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Set;

/**
 * 简单的对象容器
 */
public interface IObjectContext extends IDisposable {
    /**
     * 根据给定的名称索引容器内的对象
     * @param name 对象名称
     * @return 指定名称的对象
     * @see #register(Object)
     * @see #register(String, Object)
     * @see #getBean(Class)
     * @see #getBeanMap(Class)
     */
    Object getBean (String name);

    /**
     * 根据给定的类型索引容器内的对象.
     *
     * <p>如果根据类型在容器内有多个实例被注册，将抛出 {@link InstanceNotUniqueException} 异常。
     * 如果无法确认给定的类型是否注册了多个实例，应调用 {@link #getBeanMap(Class)} 方法。
     * </p>
     * <p>如果给定的类型在容器内无任何实例被注册，返回 {@code null}</p>
     * @param type 对象类型
     * @param <T> 对象类型
     * @return 指定类型的对象
     * @throws InstanceNotUniqueException 若给定的类型有多个实例被注册，抛出这个异常
     * @see #getBeanMap(Class)
     */
    <T> T getBean (Class<T> type);

    /**
     * 根据给定的类型索引容器内的对象所有映射
     *
     * <p>如果给定的类型在容器内有多个实例被注册，返回的映射中包含了被 <code>{@link #register(String, Object)}</code>
     * 方法映射时的名称。若是由 <code>{@link #register(Object) register (Object bean)}</code> 方法注册的实例，名称通常是
     * {@code bean} 简短类名的 {@code java property} 命名格式
     * </p>
     * <ul>
     * <li>若给定类型在容器内未注册任何实例，返回的 {@code Map} 对象中没有元素</li>
     * <li>若给定类型在容器内仅有一个实例，返回仅有一个元素的映射</li>
     * </ul>
     *
     * @param type 对象类型
     * @param <T> 指定的类型
     * @return 匹配类型的所有对象实例
     * @see #register(Object)
     * @see #register(String, Object)
     * @see #getBean(String)
     */
    <T> Map<String, T> getBeanMap (Class<T> type);

    /**
     * 删除容器内的对象
     * @param bean 目标
     */
    void remove (Object bean);

    /**
     * 根据给定的名称删除容器内的对象
     * @param name 对象名称
     */
    void remove (String name);

    /**
     * 向容器注册命名的对象
     * <p>实现要求：必须遍历对象的继承树，自动生成类型索引</p>
     * @param name 对象名称
     * @param bean 对象实例
     * @throws InstanceNotFoundException 当自动注入发生时，无法从容器中找到对应的资源，将抛出这个异常
     * @throws IllegalAccessException    当自动注入发送时，无法将资源注入目标时抛出这个异常
     * @throws InvocationTargetException 当实例中有被标注为 {@link javax.annotation.PostConstruct} 的方法调用失败时抛出
     * @throws InstantiationException    当无法实例化某个类型时抛出
     * @throws IntrospectionException    当发送 java 内省错误时抛出
     * @throws InstanceAlreadyExistsException 当名称重复时抛出
     */
    void register (String name, Object bean) throws
            IllegalAccessException,
            InstanceNotFoundException,
            InvocationTargetException,
            InstantiationException,
            InstanceAlreadyExistsException, IntrospectionException;

    /**
     * 向容器内注册对象.
     *
     * <p>实现要求：必须遍历对象的继承树，自动生成类型索引</p>
     * @param bean 对象实例
     * @throws InstanceNotFoundException 当自动注入发生时，无法从容器中找到对应的资源，将抛出这个异常
     * @throws IllegalAccessException    当自动注入发送时，无法将资源注入目标时抛出这个异常
     * @throws InvocationTargetException 当实例中有被标注为 {@link javax.annotation.PostConstruct} 的方法调用失败时抛出
     * @throws InstantiationException    当无法实例化某个类型时抛出
     * @throws IntrospectionException    当发送 java 内省错误时抛出
     */
    void register (Object bean) throws InstanceNotFoundException, IllegalAccessException, InvocationTargetException, InstantiationException, IntrospectionException;

    /**
     * 根据给定的接口类型，和 {@link IObjectCreateFactory}, 自动生产实例，并注册到容器内
     * @param factory    对象生产工厂
     * @param interfaces 生产目标对象的接口类型
     */
    void create (IObjectCreateFactory factory, Class<?>... interfaces);

    /**
     * 解决依赖注入
     */
    void resolve ();

    /**
     * 获取所有配置实例对象的名称集合
     * @return 所有的名称集合
     */
    Set<String> getAllBeanNames ();

    /**
     * 获取所有已经注册的实例
     * @return 所有已经注册的实例
     */
    Set<Object> getAllRegisteredBeans ();

    /**
     * 容器注解的键名
     * @since 1.1.0
     */
    String CONTEXT_ANNOTATION_KEY = "org.dreamwork.dsi.CONTEXT_ANNOTATION_KEY";

    String CONTEXT_DESCRIBER = "org.dreamwork.dsi.CONTEXT_DESCRIBER";

    /**
     * 获取所有针对容器的注解
     * @return 所有针对容器的注解
     * @since 1.1.0
     */
    Annotation[] getContextAnnotation ();
}

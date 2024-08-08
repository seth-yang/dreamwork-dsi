package org.dreamwork.injection;

/**
 * 对象创建工厂类
 *
 * <p>
 * 当向 {@link IObjectContext 托管容器} 内注册实例时，可以提供这个接口的实例来负责创建新的对象实例
 * </p>
 */
public interface IObjectCreateFactory {
    /**
     * 根据给定的接口类型，创建新的实例。
     *
     * <p>通常，这个工厂方法用来创建动态代理实例</p>
     * @param interfaces 接口类型
     * @return 新的对象实例
     */
    Object create (Class<?>... interfaces);
}

package org.dreamwork.injection;

import org.dreamwork.injection.impl.ClassScanner;

import java.util.Collections;
import java.util.Map;

/**
 * 简单容器的自动装配钩子
 *
 * @author seth.yang
 * @since 1.1.1
 */
public interface IObjectContextHook {
    default String[] getScanPackages () { return new String[0]; }
    default boolean isRecursive () { return false; }

    /**
     * 获取扩展的扫描器字典，该名字将和 {@link AInjectionContext#extras()} 属性中的
     * {@link AExtraScan#name()} 属性匹配
     * @return 扩展扫描器字典
     */
    default Map<String, ClassScanner> getExtraScanners () {
        return Collections.emptyMap ();
    }
}
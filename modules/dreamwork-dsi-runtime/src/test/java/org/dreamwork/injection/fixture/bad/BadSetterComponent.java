package org.dreamwork.injection.fixture.bad;

import jakarta.annotation.Resource;

/**
 * 一个非法的受托管对象：标注为 {@link Resource} 的 setter 拥有两个参数。
 *
 * <p>单独放在一个包里，避免污染其它扫描测试的可扫描范围。</p>
 */
@Resource
public class BadSetterComponent {
    @Resource
    public void setTooManyArguments (String first, String second) {
        // 容器在扫描阶段就应该拒绝这样的方法
    }
}

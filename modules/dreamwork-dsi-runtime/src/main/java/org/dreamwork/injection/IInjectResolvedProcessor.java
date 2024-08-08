package org.dreamwork.injection;

/**
 * 托管容器所有对象注入完成后的处理类
 */
public interface IInjectResolvedProcessor extends Comparable<IInjectResolvedProcessor> {
    /**
     * 托管容器所有对象注入完成后触发
     * @param context 托管容器
     * @throws Exception 任何异常
     */
    void perform (IObjectContext context) throws Exception;

    /**
     * 处理器处理顺序
     * @return 处理器处理顺序
     */
    default int getOrder () {
        return 0;
    }

    @Override
    default int compareTo (IInjectResolvedProcessor o) {
        return getOrder () - o.getOrder ();
    }
}
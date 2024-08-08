package org.dreamwork.injection;

/**
 * 附加扫描项
 */
public @interface AExtraScan {
    /**
     * 附加扫描项的名称
     * @return 附加扫描项名称
     */
    String name() default "";

    /**
     * 附加扫描项的扫描包名数组
     * @return 附加扫描项的扫描包名数组
     */
    String[] scanPackages() default {};

    /**
     * 是否递归扫描。默认 {@code false}
     * @return 是否递归扫描
     */
    boolean recursive () default false;
}

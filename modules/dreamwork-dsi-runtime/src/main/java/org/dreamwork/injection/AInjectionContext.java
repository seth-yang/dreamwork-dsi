package org.dreamwork.injection;

import java.lang.annotation.*;

/**
 * {@link IObjectContext} 的程序入口描述
 */
@Target ({ElementType.PACKAGE, ElementType.TYPE})
@Retention (RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface AInjectionContext {
    /**
     * {@link #scanPackages() scanPackages} 的快捷方式
     * @return 需要扫描的包名
     */
    String[] value () default {};

    /**
     * 指定需要扫描的所有包名。 无论是否提供了该属性，扫描器都会扫描被标注对象的包
     * @return 需扫描的所有包名
     */
    String[] scanPackages () default {};

    /**
     * 指定配置文件的路径.
     * <ul>
     * <li>无协议 或 协议 {@code classpath:} 代表在类路径中查找</li>
     * <li>协议 {@code file:} 代表在文件系统中查找</li>
     * </ul>
     *
     * 默认值 "" 代表：<br>
     * 配置文件名为<strong>{@code object-context.conf}</strong>
     * <ol>
     * <li>在<strong>类路径</strong>中查找 <strong>{@code /object-context.conf}</strong>，如果没有找到</li>
     * <li>在当前目录下查找 <strong>{@code ../conf/object-context.conf}</strong>，若未找到</li>
     * <li>在当前目录下查找 <strong>{@code object-context.conf}</strong></li>
     * </ol>
     *
     * <p>
     * 如果扫描器找到配置文件，将在 {@link IObjectContext} 中注册一个名为 <strong>{@code global-config}</strong>，类型为
     * {@link org.dreamwork.config.IConfiguration} 的对象，所有配置都可从这个对象中获取。
     * </p>
     *
     * <p>扫描器还会将命令行参数和这个配置进行合并，<i>如果提供了命令行参数</i></p>
     * @return 配置文件路径
     */
    String config () default "";

    /**
     * 是否递归扫描。默认 {@code false}
     * @return 是否递归扫描
     */
    boolean recursive () default false;

    /**
     * 命令行参数定义的 {@code json} 结构.
     * <p>扫描器的查找顺序：</p>
     * <ol>
     * <li>在 <strong>类路径</strong> 中搜索这个属性提供的值，若未找到</li>
     * <li>在 <strong>类路径</strong> 中搜索 <strong>{@code cli-arguments.json}</strong>, 若未找到</li>
     * <li>在 <strong>当前路径</strong> 下搜索 <strong>{@code ../conf/cli-arguments.json}</strong>，若未找到</li>
     * <li>在 <strong>当前路径</strong> 中搜索 <strong>{@code cli-arguments.json}</strong>, 若未找到</li>
     * <li>在 <strong>当前路径</strong> 中搜索 <strong>{@code ../cli-arguments.json}</strong>, 若未找到</li>
     * </ol>
     *
     * 关于 {@code json} 文件的结构请参见 {@link org.dreamwork.cli.Argument} 类
     *
     * @return 定义命令行参数的 json 结构
     * @see org.dreamwork.cli.Argument
     * @see org.dreamwork.cli.ArgumentParser
     */
    String argumentDefinition () default "";

    /**
     * 指定附加扫描的内容
     * @return 附加扫描项
     * @since 1.0.3
     */
    AExtraScan[] extras () default {};

    /**
     * 应用程序关闭端口. 任意一个从本地 {@code 127.0.0.1} 发出的，到达这个端口的请求都被视为请求应用程序关闭.
     * <ul>
     *     <li>默认值{@code -1}代表着由系统来随机生成一个端口，并且记录在 {@code ${java.io.tmpdir}/.shutdown-port}文件中</li>
     *     <li>值 {@code 0} ~ 值 {@code 1024} 为无效值，意味着不监听应用关闭请求</li>
     *     <li>任意 &gt; 1024 的值，系统将监听这个端口，并记录在 {@code ${java.io.tmpdir}/.shutdown-port} 文件中</li>
     * </ul>
     * @return 应用程序关闭的端口
     * @since 2.0.0
     */
    int shutdownPort () default -1;
}

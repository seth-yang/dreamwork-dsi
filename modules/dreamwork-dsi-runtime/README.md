# dreamwork-simple-injection

从 [https://gitee.com/seth_yang/dreamwork-cai-runtime](dreamwork-cai-runtime) 中分离出来，独立成项。

项目引入:
```xml
<dependency>
    <groupId>io.github.seth-yang</groupId>
    <artifactId>dreamwork-dsi-runtime</artifactId>
    <version>2.1.2</version>
</dependency>
```

## 介绍

dreamwork-simple-injection 提供一个受托管容器的简单实现。可用于低资源环境的应用程序开发 (如：系统内存 <= 256MB )。

简单示例
```java
package org.example.app;

@AInjectionContext
public class MyAppMain {
    public static void main (String[] args) throws Exception {
        ObjectContextFactory.start (MyAppMain.class, args);
    }
}
```
扫描器将自动扫描 `org.example.app` 包下的所有类，所有带有 **托管标注** 的类都将自动被配置到托管容器内。如果希望扫描其他包，使用 `scanPackages` 属性来添加额外的包扫描。
在默认情况下，扫描器 **不会** 递归扫描，若希望递归扫描，设置 `recursive`属性的值为 `true` 来激活递归扫描。


## 受托管对象
`javax.annotation.Resource` 标注用来表示一个对象/资源是 **受托管** 的。 托管容器将自动配置和管理这些对象实例。当 `javax.annotation.Resource` 用来标注: 

- 一个类时，意味着这个类将被作为受托管资源配置到托管容器内
- 一个 `java getter` 时，意味着这个 getter 的返回值将被自动配置到容器内
- 一个 `java setter` 时，意味着从容器内获取对应的资源并作为 setter 的参数注入到对象内
- 一个 `字段` 时，意味着从容器内获取对应的资源输入到这个字段

每个被 `javax.annotation.Resource` 标注的类，**最多有一个**方法被
- `javax.annotation.PostConstruct` 标注，意味着这个类在被配置到容器后将自动调用。这个方法的签名必须是
```java
public void <methodName> ();
```
- `javax.annotation.PreDestroy` 标注，意味着这个类从容器内删除前将自动调用。这个方法的签名必须是
```java
public void <methodName> ();
```

## 简单的容器生命周期
当容器解决了所有的依赖注入后，会调用所有实现了 `IInjectResolvedProcessor` 接口的实例的`perform` 方法。如果希望多个实现的运行顺序，应当实现 `getOrder` 方法，容器将按照这个方法的返回值排序，并按顺序调用，例如：
```java
package org.example.app;
@Resource
public class MyProcessor implements IInjectResolvedProcessor {
    @Override
    public void perform (IObjectContext ctx) throws Exception {
        for (String name : ctx.getAllBeanNames ()) {
            System.out.println (name);
        }
    }
}
```

## 几个相关注解
### org.dreamwork.injection.AInjectionContext 注解
`org.dreamwork.injection.IObjectContext`的程序入口描述

- `scanPackages` 属性
	指定需要扫描的所有包名。 无论是否提供了该属性，扫描器都会扫描被标注对象的包
	
- `config` 属性
	指定配置文件的路径
	
	- 无协议 或 协议 `classpath:` 代表在类路径中查找
	- 协议 `file:` 代表在文件系统中查找
	
	默认值 "" 代表配置文件名为 **object-context.conf**
  1. 在**类路径**中查找 **`/object-context.conf`**，如果没有找到 
  2. 在**当前目录**下查找 **`../conf/object-context.conf`**，若未找到
  3. 在当前目录下查找 **`object-context.conf`**
	
	如果扫描器找到配置文件，将在 `IObjectContext` 中注册一个名为**global-config**，类型为`org.dreamwork.config.IConfiguration`的对象，所有配置都可从这个对象中获取。
	
	扫描器还会将命令行参数和这个配置进行合并，*如果提供了命令行参数*
	
- `recursive` 属性
	是否递归扫描。默认`false`
	
- `argumentDefinition` 属性
	命令行参数定义的 `json` 结构。扫描器的查找顺序：
	1. 在 **类路径** 中搜索这个属性提供的值，若未找到
	2. 在 **类路径** 中搜索 **`cli-arguments.json`**, 若未找到
	3. 在 **当前路径** 下搜索 **`../conf/cli-arguments.json`**，若未找到
	4. 在 **当前路径** 中搜索 **`cli-arguments.json`**

### org.dreamwork.injection.AConfigured 注解
- `value` 属性
	key 属性的快捷方式
	
- `key` 属性
	表示被标注的对象可以由配置来获取值.
	1. 当表达式为 `${a.b.c.d}` 时，代表着从全局配置文件中获取 `a.b.c.d` 的值。当在配置文件中未匹配到键值时不会注入
	2. 当表达式为常量时，直接将常量赋值给被标注的对象
	3. 默认值 `""` 表示直接使用 `package.class.field` 的形式作为键值在配置文件中进行匹配
	
- `required` 属性
	该注入的配置项是否是必须的。
	若该属性为 `true` 时，**且** 在配置文件中 **未找到** 该键值时，扫描器将抛出 `ConfigurationNotFoundException` 异常。默认`false`

## 参与贡献

1.  Fork 本仓库
2.  新建 Feat_xxx 分支
3.  提交代码
4.  新建 Pull Request
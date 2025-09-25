# dreamwork-dsi-embedded-httpd

## 介绍
Dreamwork Simple Injection (DSI) 的钩子，用于启动一个内嵌的 httpd 服务.

the dsi hook of dreamwork simple injection for start an embedded-httpd

项目引入:
```xml
<dependency>
    <groupId>io.github.seth-yang</groupId>
    <artifactId>dreamwork-dsi-embedded-httpd</artifactId>
    <version>2.1.2</version>
</dependency>
```
### 内置配置项

| 键名 | 类型 | 默认值 | 版本      | 备注 |
| --- | --- | --- |---------| -- |
| `embedded.httpd.context-path` | string | `/` | 1.0.0   | webapp 的根目录 |
| `embedded.httpd.base` | string | `../webapp` | 1.0.0   | http 服务的根目录 |
| `embedded.httpd.port` | int | `9090` | 1.0.0 | http 服务的端口 |
| `embedded.httpd.host` | string | `127.0.0.1` | 1.0.0   | http 服务绑定的服务器地址 |
| `embedded.httpd.views.extension` | string | `.jsp` | 1.0.0   | 默认视图的扩展名 |
| `embedded.httpd.api-mapping` | string | `/apis` | 1.0.0   | WebHandler 映射的根目录 |
| `embedded.httpd.delegate.enabled` | boolean | `false` | 1.0.0   | |
| `dsi.embedded.httpd.managed.session.enabled` | boolean | `true` | 1.0.0   | 是否启用托管的session |
| `dsi.embedded.httpd.session.timeout` | long | `1800000` | 1.0.0   | 托管 session 的超时时间，毫秒 |
| `embedded.httpd.websocket.enabled` | boolean | `true` | 2.1.0   | 是否启用 Websocket 支持 |

### 注解列表
| 名称                                                            | 备注 | 支持的版本 |
|---------------------------------------------------------------| -- |-------|
| `@org.dreamwork.dsi.embedded.httpd.annotation.AWebHandler`    | 批注一个类用于 WebHandler 映射 | 1.0.0 |
| `@org.dreamwork.dsi.embedded.httpd.annotation.AWebMapping`    | 批注一个方法用于 WebHandler 映射 | 1.0.0 |
| `@org.dreamwork.dsi.embedded.httpd.annotation.AWebParameter` | 批注一个参数用于 WebHdndler 映射 | 1.0.0 |
| `@org.dreamwork.dsi.embedded.httpd.annotation.AWebSocket` | 批注一个类作于 WebSocket 映射 | 2.1.0 |
| `@org.dreamwork.dsi.embedded.httpd.annotation.AFormItem` | 批注一个参数来源于 `Query String` 或者 `Web Form` | 1.0.0 |
| `@org.dreamwork.dsi.embedded.httpd.annotation.AHeaderItem` | 批注一个参数来源于 `Http Header` | 1.0.0 |
| `@org.dreamwork.dsi.embedded.httpd.annotation.AInternal` | 批注一个参数来源于 `内部类型`，比如常见的 `HttpServletRequest` 之类的内置对象 | 1.0.0 |
| `@org.dreamwork.dsi.embedded.httpd.annotation.AManagedSessionAttribute` | 批注一个参数来源于托管session的缓存 | 1.0.0 |
| `@org.dreamwork.dsi.embedded.httpd.annotation.APathVariable` | 批注一个参数来源于Path的一部分 | 1.0.0 |
| `@org.dreamwork.dsi.embedded.httpd.annotation.ARequestAttribute` | 批注一个参数来源于 HttpRequest 属性 | 1.0.0 |
| `@org.dreamwork.dsi.embedded.httpd.annotation.ARequestBody` | 批注一个参数来源于整个 http payload | 1.0.0 |
| `!org.dreamwork.dsi.embedded.httpd.annotation.ASessionAttribute` | 批注一个参数来源于 HttpSession 的属性 | 1.0.0 |
| `@org.dreamwork.dsi.embedded.httpd.annotation.AWebPackages` | 批注在主类上，用于扫描传统 Web 组件，如: `HttpServlet`、`WebFilter` 之类 | 1.1.0 |
| `@org.dreamwork.dsi.embedded.httpd.annotation.AWebsocketPackages` | 批注在主类上，用于扫描 Websocket 组件 | 2.1.2 |

## 托管的 Web 请求处理程序

### WebHandler
任意一个同时被 `@javax.annotation.Resource` 和 `@org.dreamwork.dsi.embedded.httpd.annotation.AWebHandler` 标注的类，且该类位于扫描器扫描路径下，
都将自动被识别为 Web 请求处理程序，以下称为 `WebHandler`. 一个典型的例子：

```java
package org.dreamwork.example.dsi.web.handlers;

import org.dreamwork.dsi.embedded.httpd.annotation.*;
import org.dreamwork.injection.IObjectContext;
import org.dreamwork.util.CollectionCreator;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;

@Resource
@AWebHandler ("/first")
public class MyFirstWebHandler {
    @Resource
    private IObjectContext context;

    @PostConstruct
    public void init () {
        // all injected resources and configurations are now available.
    }

    @PreDestroy
    public void destroy () {
        System.out.println ("i'm destroyed");
    }

    @AWebMapping ("/resource")
    public Map<?, ?> getResource (@AFormItem ("id") String id) {
        return CollectionCreator.asMap ("resource", id);
    }

    @AWebMapping (value = "/resource", method = "POST")
    public void createResource (@ARequestBody Map<?, ?> payload) {
        // do something
    }

    @AWebMapping (value = "/resource/${id}", method = "DELETE")
    public void removeResource (@APathVariable ("id") String id) {
        // do something remove
    }
}
```
现在，您已经有以下http请求服务了：
- 路径为 `/first/resource` 的 `GET` 请求，它接受一个从`QueryString`来的名为`id`的参数
- 路径为 `/first/resource` 的 `POST` 请求，它将整个 HTTP Payload 作为参数，并组织为一个 `Map<?, ?>`
- 路径为 `/first/resource/${id}` 的 `DELETE` 请求，它将路径中的 `${id}` 部分作为参数

### Websocket
#### 关于 `org.dreamwork.dsi.embedded.httpd.support.websocket.IWebsocketCommand`
一个 Websocket 需要通过 IWebsocketCommand 实现类来和客户端进行数据交换
```java
package org.dreamwork.example.dsi.web.websockets;

import org.dreamwork.dsi.embedded.httpd.support.websocket.IWebsocketCommand;

public class MyWSCommand implements IWebsocketCommand {
    public int action;      // 操作代码
    public String payload;  // 数据
    
    public static final int ACT_HANDSHAKE = 0x01;
}
```

#### 关于 `org.dreamwork.dsi.embedded.httpd.support.websocket.AbstractWebSocket`
在 `AbstractWebSocket` 中，您需要关注以下方法
```java
public abstract class AbstractWebSocket<T extends IWebsocketCommand>
        extends Endpoint
        implements IWebSocketExecutor<T>, MessageHandler.Whole<String> {
    
    protected abstract String cast (T message);
    
    protected abstract T parse (String text);

    public abstract boolean matches (String id, T message);
    
    public abstract void handleMessage (T message);

    public final void send (T message) { /* ... */ }
```
由于框架采取的用 String 来进行 Websocket 数据传输，您必须在实现类中处理 IWebsocketCommand 和 String 之间的相互转换，方法:
- `protected abstract String cast (T message)` 用来将 IWebsocketCommand 序列化成 String
- `protected abstract T parse (String text)` 用来将 String 反序列化成 IWebsocketCommand

当框架收到一个客户端发来的消息时，将触发实现类的 `public abstract void handleMessage (T message)` 方法，您可以在该方法内对客户端请求做出响应，
并将计算结果，通过 `public final void send (T message)` 方法发送给客户端.

某些情况下，客户端请求了一个耗时的计算任务，比如统计大量数据，或导出大量数据，通常的做法是将任务转到后台，期间通过websocket将任务的实时进度反馈到 Web UI上；
当后台任务需要更新任务的实时进度时，通过 `WebSocketManager.update (Class<? extends AbstractWebSocket<T>> type, String id, T message)` 发出更新请求， 
实现类通过 `public abstract boolean matches (String id, T message)` 的返回值来决定这条更新消息是否匹配，只有匹配的消息才会被反馈到客户端.

任意一个在扫描器类路径下，被 `@org.dreamwork.dsi.embedded.httpd.annotation.AWebSocket` 标注的 `org.dreamwork.dsi.embedded.httpd.support.websocket.AbstractWebSocket` 实现类， 
将被识别为 Websocket 服务。下面是一个简单的例子

```java
package org.dreamwork.example.dsi.web.websockets;

import com.google.gson.Gson;
import org.dreamwork.dsi.embedded.httpd.annotation.AWebSocket;
import org.dreamwork.dsi.embedded.httpd.support.websocket.AbstractWebSocket;
import org.dreamwork.dsi.embedded.httpd.support.websocket.IWebsocketCommand;
import org.dreamwork.injection.AConfigured;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;

@AWebSocket ("/ws/example")
public class MyWebSocket extends AbstractWebSocket<MyWSCommand> {
    @Resource
    private AnyDelegatedService service;    // 任何一个托管的服务

    @AConfigured ("${my.app.config.key}")
    private String anyConfigItem;           // 任何一个可注入的配置项
    
    private String id;

    @PostConstruct
    public void init () {
        // all injected resources and configurations are now available.
    }

    @PreDestroy
    public void destroy () {
        // do something when this instance will be destroyed.
    }

    @Override
    protected String cast (MyWSCommand command) {
        return command == null ? "" : new Gson ().toJson (command);
    }

    @Override
    protected MyWSCommand parse (String content) {
        return content == null || content.isEmpty () ? 
                null : 
                new Gson ().fromJson (content, MyWSCommand.class);
    }

    @Override
    public void onConnected (Session session) {
        MyWSCommand cmd = MyWSCommand ();
        cmd.action = 0;
        cmd.payload = "Welcome to my first websocket example.";
        send (cmd);
    }

    @Override
    public boolean matches (String id, MyWSCommand command) {
        // I only accept the message that send to "me".
        return id != null && id.equals (this.id);
    }

    @Override
    public void handleMessage (MyWSCommand command) {
        if (command.action == MyWSCommand.ACT_HANDSHAKE) {
            this.id = command.payload;
        } else {
            // just push back
            send (command);
        }
    }
}
```

## 传统的 Web 组件
dsi-httpd 也提供了对传统的 Web 组件的支持，可以使用资源注入注解注入资源
### HttpServlet
dsi-httpd 提供 `org.dreamwork.dsi.embedded.httpd.support.InjectableServlet` 基类来支持资源注入，
您的 Servlet 实现类只要继承这个基类便可使用注解来注入资源。

```java
package org.dreamwork.example.dsi.web.servlets;

import org.dreamwork.dsi.embedded.httpd.support.InjectableServlet;
import org.dreamwork.injection.AConfigured;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet ("/my-endpoint/*")
public class MyServlet extends InjectableServlet {
    @Resource
    private AnyDelegatedService service;    // any delegated service

    @AConfigured ("${my.app.config.key}")
    private int someIntValue;               // any pre-config value

    @PostConstruct
    public void postConstruct () {
        // all injected resources and configurations are now available.
    }

    @Override
    protected void doGet (HttpServletRequest request, HttpServletResponse response) {
        // do your job.
    }
}
```
### 内置支持的文件上传
dsi-httpd 还提供了 `org.dreamwork.dsi.embedded.httpd.support.upload.ResourceServlet` Servlet，来处理用户上传的文件，以及获取它们：
- 将用户上传的所有文件保存在服务器的临时目录下
- 组成以用户上传的字段名为键，临时文件的文件名为值的一个 Map
- 触发 `Object onFilesSaved (Map<String, String> savedFiles)` 事件，其参数是上一步产生的 Map，期待返回一个最终返回给客户端的结果
- 返回的 Http Content-Type为 `application/json;charset=utf-8`, payload 是 onFileSaved 事件的返回值.

```java
package org.dreamwork.example.dsi.web.servlets;

import org.dreamwork.dsi.embedded.httpd.support.upload.ResourceServlet;

import javax.servlet.annotation.WebServlet;

@WebServlet ({"/uploader", "/resources/*"})
public class SimpleResourceUploadServlet extends ResourceServlet { }
```
这样就可以获得一个最简单但可工作的文件上传/浏览器了。

### WebFilter
dsi-httpd 也支持传统的 WebFilter 的依赖注入，只要用户代码继承 `org.dreamwork.dsi.embedded.httpd.support.InjectableFilter` 即可。

```java
package org.dreamwork.example.dsi.web.filters;

import org.dreamwork.dsi.embedded.httpd.support.InjectableFilter;
import org.dreamwork.injection.AConfigured;
import org.dreamwork.util.CollectionHelper;
import org.dreamwork.util.PathFilter;
import org.dreamwork.util.StringUtil;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashSet;

@WebFilter ("/my-resource-filter")
public class MyWebFilter extends InjectableFilter {
    @Resource
    private AnyDeletegatedService service;

    @AConfigured ("${my.app.resource.filters.excluded}")
    private String excluded;

    private final Set<PathFilter> filters = new HashSet<> ();

    @PostConstruct
    public void init () {
        if (resourceFilters != null && !resourceFilters.isEmpty ()) {
            java.util.Arrays.stream (resourceFilters.split ("[,;\\s]"))
                    .filter (f -> StringUtil::isNotEmpty)
                    .map (PathFilter::new)
                    .forEach (filters::add);
        }
    }

    @Override
    public void doFilter (ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException, ServletException {
        if (CollectionHelper.isNotEmpty (filters)) {
            HttpServletRequest request = (HttpServletRequest) req;
            String pathInfo = request.getPathInfo ();

            for (PathFilter excluded : filters) {
                if (excluded.hit (pathInfo)) {
                    chain.doFilter (req, resp);
                    break;
                }
            }
        } else {
            // do some check for current resource.
            if (checkForPathInfo (pathInfo)) {
                chain.doFilter (req, resp);
            } else {
                // throw some error or respond to client.
                ((HttpServletResponse) resp).sendError (HttpServletResponse.SC_NOT_ACCEPTABLE);
            }
        }
    }
}
```

## Change Logs
2022-11-01
- 新增对普通 Servlet 和 Filter 的支持
- 新增 AInternal, ARequestAttribute, ASessionAttribute 注解。呃。。。对，就是你想象的那样。。。
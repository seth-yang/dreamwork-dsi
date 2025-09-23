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

| 键名 | 类型 | 默认值 | 版本 | 备注 |
| --- | --- | --- | --- | -- |
| `embedded.httpd.context-path` | string | `/` | 1.0.0 | webapp 的根目录 |
| `embedded.httpd.base` | string | `../webapp` | 1.0.0 | http 服务的根目录 |
| `embedded.httpd.port` | int | `9090` | `1.0.0` | http 服务的端口 |
| `embedded.httpd.host` | string | `127.0.0.1` | 1.0.0 | http 服务绑定的服务器地址 |
| `embedded.httpd.views.extension` | string | `.jsp` | 1.0.0 | 默认视图的扩展名 |
| `embedded.httpd.api-mapping` | string | `/apis` | 1.0.0 | WebHandler 映射的根目录 |
| `embedded.httpd.delegate.enabled` | boolean | `false` | 1.0.0 | |
| `dsi.embedded.httpd.managed.session.enabled` | boolean | `true` | 1.0.0 | 是否启用托管的session |
| `dsi.embedded.httpd.session.timeout` | long | `1800000` | 1.0.0 | 托管 session 的超时时间，毫秒 |
| `embedded.httpd.websocket.enabled` | boolean | `true` | 1.0.0 | 是否启用 Websocket 支持 |

### 注解列表
| 名称                                                            | 备注 | 支持的版本 |
|---------------------------------------------------------------| -- |-------|
| `@org.dreamwork.dsi.embedded.httpd.annotation.AWebHandler`    | 批注一个类用于 WebHandler 映射 | 1.0.0 |
| `@org.dreamwork.dsi.embedded.httpd.annotation.AWebMapping`    | 批注一个方法用于 WebHandler 映射 | 1.0.0 |
| `@org.dreamwork.dsi.embedded.httpd.annotation.AWebParameter` | 批注一个参数用于 WebHdndler 映射 | 1.0.0 |
| `@` |

## Change Logs
2022-11-01
- 新增对普通 Servlet 和 Filter 的支持
- 新增 AInternal, ARequestAttribute, ASessionAttribute 注解。呃。。。对，就是你想象的那样。。。
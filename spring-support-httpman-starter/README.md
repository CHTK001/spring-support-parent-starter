# spring-support-httpman-starter

接口调试控制台后端：集合、请求、运行历史、环境变量与代理执行，自带数据库表与前端页面约定。

> 版本 `4.0.0.51`；父 POM 继承 `spring-boot-starter-parent:4.1.0`，Java 25。

## 一、能力概览

| 能力 | 入口 |
| --- | --- |
| 自动装配 | `HttpmanAutoConfiguration`（`AutoConfiguration.imports`） |
| 控制器 | 1 个（`HttpmanController`） |
| Service | 1 个 |
| 配置前缀 | `httpman.*` |
| 建表脚本 | 2 个 SQL 文件 |

## 二、引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-httpman-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

内部依赖：`common`、`mybatis`、`cache`

## 三、装配与开关

`HttpmanAutoConfiguration` 组件扫描 `com.chua.starter.httpman` 并注册 Mapper；属性前缀是 `httpman`（不是 `plugin.*`）。

## 四、公开 Bean

本模块没有显式 `@Bean`，控制器/服务由组件扫描或 `@Import` 注册。

## 五、Service（组件扫描，可直接注入）

| Service | 说明 |
| --- | --- |
| `HttpmanServiceImpl` | 接口调试控制台 - 服务实现 |

## 六、HTTP 接口

### `HttpmanController`（`/ms/httpman`）

接口调试控制台 - REST 入口 与前端约定前缀 /ms/httpman，通过 HttpmanService 管理集合/请求/历史/变量， 以及由 execution 转入代理执行。

| 方法 | 路径 | Java 方法 | 说明 |
| --- | --- | --- | --- |
| GET | `/ms/httpman/collections` | `collections` |  |
| POST | `/ms/httpman/collections` | `addCollection` |  |
| PUT | `/ms/httpman/collections/{id}` | `updateCollection` |  |
| DELETE | `/ms/httpman/collections/{id}` | `deleteCollection` |  |
| GET | `/ms/httpman/requests` | `requests` |  |
| POST | `/ms/httpman/requests` | `addRequest` |  |
| PUT | `/ms/httpman/requests/{id}` | `updateRequest` |  |
| GET | `/ms/httpman/requests/{id}` | `requestDetail` |  |
| DELETE | `/ms/httpman/requests/{id}` | `deleteRequest` |  |
| POST | `/ms/httpman/requests/{id}/execute` | `execute` |  |
| POST | `/ms/httpman/requests/{id}/test` | `test` |  |
| GET | `/ms/httpman/history` | `history` |  |
| GET | `/ms/httpman/history/{id}` | `historyDetail` |  |
| DELETE | `/ms/httpman/history/{id}` | `deleteHistory` |  |
| POST | `/ms/httpman/history/clean` | `cleanHistory` |  |
| GET | `/ms/httpman/variables` | `variables` |  |
| POST | `/ms/httpman/variables` | `addVariable` |  |
| PUT | `/ms/httpman/variables/{id}` | `updateVariable` |  |
| DELETE | `/ms/httpman/variables/{id}` | `deleteVariable` |  |

## 七、配置

### `httpman.*`（`HttpmanProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enabled` | `boolean` | `true` | 总开关，false 时不暴露任何 HttpMan 能力 |
| `page` | `String` | `"/httpman"` | 前端页面路径（不是非得用，CMS 留档） |
| `path-prefix` | `String` | `"/ms/httpman"` | 后端 REST 路径前缀（前端约定 /ms/httpman/**） |
| `default-collection` | `String` | `"默认集合"` | 集合根文件夹名称（首次打开时自动创建） |
| `history-retention-days` | `int` | `30` |  |
| `connect-timeout` | `Duration` | `Duration.ofSeconds(15)` |  |
| `read-timeout` | `Duration` | `Duration.ofSeconds(60)` |  |
| `ssl-insecure` | `boolean` | `false` | 代理执行：是否需要跳过证书校验（地对错误证书/自签的场景开放） |
| `proxy-from-env` | `boolean` | `false` | 代理执行：是否为全局代理（从 http_proxy / https_proxy 环境变量读） |
| `proxy` | `String` | `—` | 代理执行：显式代理地址（覆盖环境变量），如 http://127.0.0.1:7890 |
| `proxy-bypass` | `List<String>` | `new ArrayList<>()` | 代理执行：排除列表，命中这些前缀的请求不走代理（如 127.0.0.1 / localhost） |
| `sensitive-header-mask` | `boolean` | `true` | 是否在运行记录中剥离敏感头（Authorization / Cookie），防止凭据落库 |

## 八、数据表与脚本

| 脚本 | 建表数 | 表名（前 12 个） |
| --- | --- | --- |
| `db/h2/V4.0.0.51__httpman.sql` | 4 | `ms_httpman_collection`, `ms_httpman_request`, `ms_httpman_history`, `ms_httpman_variable` |
| `db/init/V4.0.0.51__httpman.sql` | 4 | `ms_httpman_collection`, `ms_httpman_request`, `ms_httpman_history`, `ms_httpman_variable` |

## 九、通用场景

团队内替代 Postman 的轻量方案：前端按 `/ms/httpman/**` 调用，运行记录落库，按 `history-retention-days` 清理。

## 十、注意事项

- `httpman.enabled` 字段目前没有在服务/控制器里读取，关掉它不会禁用接口；要禁用只能不引依赖或排除自动配置。
- 运行记录会保存请求头/参数，`sensitive-header-mask=true` 会剥离 Authorization/Cookie，别关。
- 生产环境建议加鉴权或直接不部署；`ssl-insecure` 只对自签/测试环境打开。

## 十一、验证

```bash
mvn -pl spring-support-httpman-starter -am -DskipTests package
```

接口路径以本文档为准，启动后用 `GET /service-info` 查看模块开关与端点汇总。

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

内部依赖：`common`、`mybatis`、`cache`（可选）。`spring-boot-starter-web` 与 `spring-boot-starter-webflux` 在本 starter 中声明为 `optional`：宿主应用需自带 Web 容器（否则不会暴露 `/ms/httpman/**` 端点）与 reactor-netty（代理执行依赖 WebClient）。

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
| `enabled` | `boolean` | `true` | 总开关，false 时不暴露任何 HttpMan 能力（当前版本未在服务/控制器中强制读取，见注意事项） |
| `page` | `String` | `"/httpman"` | 前端页面路径（不是非得用，CMS 留档；预留字段） |
| `path-prefix` | `String` | `"/ms/httpman"` | 后端 REST 路径前缀（前端约定 /ms/httpman/**；控制器当前固定映射 `/ms/httpman`，改配置不会挪动端点） |
| `default-collection` | `String` | `"默认集合"` | 集合根文件夹名称（首次打开时自动创建；预留字段） |
| `history-retention-days` | `int` | `30` | 历史记录保留天数，≤0 表示不限制/不清理；当前清理入口是 `POST /history/clean`（接口参数默认 30，未自动读本字段） |
| `connect-timeout` | `Duration` | `15s` | 代理执行：连接超时（当前执行器未单独应用，由 reactor-netty 内部兜底） |
| `read-timeout` | `Duration` | `60s` | 代理执行：读取超时（reactor-netty responseTimeout + Mono 超时；服务端整体等待 read-timeout+15s） |
| `ssl-insecure` | `boolean` | `false` | 代理执行：是否需要跳过证书校验（地对错误证书/自签的场景开放） |
| `proxy-from-env` | `boolean` | `false` | 代理执行：是否为全局代理（从 http_proxy / https_proxy 环境变量读；预留，当前执行器仅消费显式 `proxy`） |
| `proxy` | `String` | `—`（空/空白 = 不走代理） | 代理执行：显式代理地址（覆盖环境变量），如 http://127.0.0.1:7890；格式错误会在执行时抛出异常 |
| `proxy-bypass` | `List<String>` | `[]` | 代理执行：排除列表，命中这些前缀的请求不走代理（如 127.0.0.1 / localhost；仅在 `proxy` 非空时生效） |
| `sensitive-header-mask` | `boolean` | `true` | 是否在运行记录中剥离敏感头（Authorization / Cookie / x-oauth-token 置为 ******），防止凭据落库 |
| `allowed-methods` | `List<String>` | `GET/POST/PUT/DELETE/PATCH/OPTIONS/HEAD` | 代理执行方法白名单，不在列表内的方法执行时直接拒绝（防开放代理） |

### 完整配置示例（application.yml）

```yaml
httpman:
  enabled: true                    # 总开关
  page: /httpman                   # 前端页面路径（CMS 留档）
  path-prefix: /ms/httpman         # 后端 REST 路径前缀（与前端约定）
  default-collection: 默认集合      # 根集合名称
  history-retention-days: 30       # 历史保留天数，<=0 表示不清理
  connect-timeout: 15s             # 连接超时
  read-timeout: 60s                # 读取超时
  ssl-insecure: false              # 跳过证书校验（仅自签/测试环境）
  proxy-from-env: false            # 预留：从环境变量读代理
  proxy: ""                        # 显式代理地址，空 = 不走代理
  proxy-bypass:                    # 不走代理的主机前缀
    - 127.0.0.1
    - localhost
  sensitive-header-mask: true      # 历史记录脱敏 Authorization/Cookie
  allowed-methods: [GET, POST, PUT, DELETE, PATCH, OPTIONS, HEAD]
```

### 逐项配置示例

#### 基础开关与路径

- **`httpman.enabled`**（`boolean`，默认 `true`）：HttpMan 总开关。当前版本服务/控制器未读取该字段，置为 `false` 不会禁用已装配的接口；要彻底关闭请不引依赖或排除自动配置。

  ```yaml
  httpman:
    enabled: true
  ```

- **`httpman.page`**（`String`，默认 `/httpman`）：前端页面路径，CMS 留档用途，后端运行时不消费。

  ```yaml
  httpman:
    page: /httpman
  ```

- **`httpman.path-prefix`**（`String`，默认 `/ms/httpman`）：后端 REST 路径前缀。控制器当前以 `@RequestMapping("/ms/httpman")` 固定映射，本字段为前后端约定留档。

  ```yaml
  httpman:
    path-prefix: /ms/httpman
  ```

- **`httpman.default-collection`**（`String`，默认 `默认集合`）：集合根文件夹名称（首次打开时自动创建），预留字段。

  ```yaml
  httpman:
    default-collection: 默认集合
  ```

#### 历史与超时

- **`httpman.history-retention-days`**（`int`，默认 `30`）：历史记录保留天数；≤0 表示不限制（清理入口对 ≤0 直接跳过）。当前实际清理入口是 `POST /ms/httpman/history/clean?retentionDays=`（接口默认 30），未自动读取本字段。

  ```yaml
  httpman:
    history-retention-days: 30
  ```

- **`httpman.connect-timeout`**（`Duration`，默认 `15s`）：代理执行连接超时。当前执行器未单独设置连接超时（由 reactor-netty 内部兜底），字段保留。

  ```yaml
  httpman:
    connect-timeout: 15s
  ```

- **`httpman.read-timeout`**（`Duration`，默认 `60s`）：代理执行读取超时。同时作用于 reactor-netty `responseTimeout`、`Mono.timeout`，服务端同步等待上限为 `read-timeout + 15s`。

  ```yaml
  httpman:
    read-timeout: 60s
  ```

#### 代理执行

- **`httpman.proxy`**（`String`，默认空 = 不走代理）：显式代理地址，配置后覆盖环境变量；地址无法解析时执行会抛出 `代理地址格式错误` 异常。

  ```yaml
  httpman:
    proxy: http://127.0.0.1:7890
  ```

- **`httpman.proxy-from-env`**（`boolean`，默认 `false`）：是否从 `http_proxy` / `https_proxy` 环境变量读代理。当前执行器仅消费显式 `httpman.proxy`，本字段为预留。

  ```yaml
  httpman:
    proxy-from-env: false
  ```

- **`httpman.proxy-bypass`**（`List<String>`，默认 `[]`）：不走代理的主机前缀列表，会以逗号拼接进 reactor-netty 的 `nonProxyHosts`；仅在 `httpman.proxy` 非空时生效。

  ```yaml
  httpman:
    proxy-bypass:
      - 127.0.0.1
      - localhost
  ```

- **`httpman.ssl-insecure`**（`boolean`，默认 `false`）：`true` 时跳过证书校验（reactor-netty `noSSL()`），仅建议自签/测试环境开启。

  ```yaml
  httpman:
    ssl-insecure: false
  ```

#### 安全与白名单

- **`httpman.sensitive-header-mask`**（`boolean`，默认 `true`）：请求转发前与历史落库前，把 `Authorization` / `Cookie` / `x-oauth-token` 的值替换为 `******`。生产环境请勿关闭。

  ```yaml
  httpman:
    sensitive-header-mask: true
  ```

- **`httpman.allowed-methods`**（`List<String>`，默认 `GET/POST/PUT/DELETE/PATCH/OPTIONS/HEAD`）：代理执行方法白名单，防止把控制台当开放代理使用；置为空列表会拒绝所有执行请求。

  ```yaml
  httpman:
    allowed-methods: [GET, POST, PUT, DELETE, PATCH, OPTIONS, HEAD]
  ```

## 八、数据表与脚本

| 脚本 | 建表数 | 表名（前 12 个） |
| --- | --- | --- |
| `db/h2/V4.0.0.51__httpman.sql` | 4 | `ms_httpman_collection`, `ms_httpman_request`, `ms_httpman_history`, `ms_httpman_variable` |
| `db/init/V1.0__init_all_httpman.sql` | 4 | `ms_httpman_collection`, `ms_httpman_request`, `ms_httpman_history`, `ms_httpman_variable` |
| `db/init/V1.0__initdata_all_httpman.sql` | — | 种子占位（本模块无预置数据，保留以对齐「一个 init + 一个 initdata」规范） |

## 九、通用场景

团队内替代 Postman 的轻量方案：前端按 `/ms/httpman/**` 调用，运行记录落库，历史按保留天数经 `POST /ms/httpman/history/clean` 清理（`httpman.history-retention-days` 为约定保留期）。

## 十、注意事项

- `httpman.enabled` 字段目前没有在服务/控制器里读取，关掉它不会禁用接口；要禁用只能不引依赖或排除自动配置。
- 运行记录会保存请求头/参数，`sensitive-header-mask=true` 会剥离 Authorization/Cookie，别关。
- 生产环境建议加鉴权或直接不部署；`ssl-insecure` 只对自签/测试环境打开。
- 历史清理没有内置定时任务，需自行调用 `POST /ms/httpman/history/clean?retentionDays=30`（可挂到运维定时器）；`httpman.history-retention-days` 当前不会被自动消费。
- `page`、`path-prefix`、`default-collection`、`connect-timeout`、`proxy-from-env` 为预留/约定字段，运行时行为以源码为准；`path-prefix` 改配置不会挪动端点。
- `allowed-methods` 是防「开放代理」的白名单，建议按业务实际需要收窄（例如只留 `GET/POST`）；`proxy` 一旦配置，所有未命中 `proxy-bypass` 的执行请求都会经该代理转发，注意代理出口的审计与限流。

## 十一、验证

```bash
mvn -pl spring-support-httpman-starter -am -DskipTests package
```

接口路径以本文档为准，启动后用 `GET /service-info` 查看模块开关与端点汇总。

# spring-support-logger-starter

日志采集与检索一体化模块：客户端运行时注入 slf4j Appender（自动探测 Logback / Log4j2），经 utils 同步通道（TCP）把日志事件推送到远端；服务端接收、存储并提供 `/v2/logger` REST / SSE 检索与实时推送。通过 `plugin.logger.type` 选择角色：`client`（默认）/ `server` / `both`。

模块坐标：`com.chua:spring-support-logger-starter:4.0.0.51`，自动装配入口见 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`：

- `com.chua.starter.logger.LoggerAutoConfiguration`（采集客户端 + 接收服务端）
- `com.chua.starter.common.support.log.TracePortalConfiguration`（链路日志汇聚，默认关闭）
- `com.chua.starter.common.support.log.MdcReactorPropagationConfiguration`（Reactor 响应式链路 MDC 自动传播）

> 本模块同时承载了一批「随包迁移」的历史能力：MDC 链路追踪、HTTP 参数日志过滤器、服务信息端点 / 横幅（`plugin.service-info`）等，详见下文「配置说明」与「注意事项」。

## 能力概览

- **采集客户端**（client / both）
  - slf4j 绑定自动探测：按 classpath 注入 Logback 或 Log4j2 Appender（`frameworks=auto|logback|log4j2|none`）。
  - 有界内存队列（`queue-capacity`）：队列满时丢弃新事件并计数，**绝不阻塞业务线程**。
  - 批量推送线程（`batch-size` / `flush-interval-ms`）：攒满条数或超时即整批发送；停机前排空队列尽量不丢尾批。
  - 防递归保护：本模块自身（`com.chua.starter.logger.`）与同步通道（`com.chua.common.support.network.sync`）的日志不进采集队列。
- **传输通道**：utils `TcpSyncServer` / `TcpSyncClient`（topic `logger-events`）；优先经服务发现（`client.service-name` + `client.scatter-id`）寻址服务端，`client.server-url` 显式地址兜底；`both` 角色且未配 `server-url` 时自动回环 `tcp://127.0.0.1:{server.port}`。
- **接收服务端**（server / both）
  - TCP 监听（`server.host` / `server.port`，默认 `0.0.0.0:19391`），发现中心可用时按 `server.service-name` + `server.scatter-id` 注册。
  - 存储引擎可插拔（`storage.engine`）：`datasource-bean`（复用容器 DataSource 落 JDBC KV）/ `memory`（有界内存）/ `redis-bean`（SPI 探测）；引擎不可用时回落 `memory` 并告警。
  - 日志事件速率（EPS）时序落 `logger_eps` 表，建表脚本 `db/init/V1.0__init_all_logger.sql`（`CREATE TABLE IF NOT EXISTS`，幂等）。
- **检索与实时 API**（`/v2/logger`，仅 Servlet Web 环境装配）
  - `GET /apps`：应用 / 实例清单。
  - `GET /events`：分页查询（应用、实例、级别、关键字、traceId、时间区间、游标翻页，`limit` 1~500，默认 100）。
  - `GET /traces`：按 traceId 聚合的链路摘要（条数 / 错误数 / 起止 / 涉及应用，`limit` 1~200，默认 50）。
  - `GET /stats`：统计信息（total / levels / eps / topLoggers / apps / instances）。
  - `DELETE /events`：按条件删除（应用 / 实例 / 级别 / `before` 时间戳）。
  - `POST /ingest`：浏览器 / JS 批量推送入口（单批上限 1000 条；可选 `x-logger-token` 共享密钥校验）。
  - `GET /sdk.js`：零依赖浏览器 SDK（UMD）。
  - `GET /stream`：SSE 实时日志流（事件名 `log` / `heartbeat` / `open`，订阅过滤条件与 `/events` 同口径）。
- **浏览器 SDK**（`/v2/logger/sdk.js`）：采集 console、window 异常、网络失败（HTTP ≥ 400 或连接失败）、资源加载错误；跟随 `x-trace-id`；支持采样率、忽略规则与 `beforeSend` 钩子；自动附带 `mdc.page` 与 `mdc.sessionId`。
- **MDC 链路追踪**（随包能力）：`MdcHandlerFilter` 注册于过滤链最前（Header `x-trace-id`），`MdcTaskDecorator` / `MdcWrap` 覆盖异步线程池与 WebSocket，Reactor 场景经 `Hooks.enableAutomaticContextPropagation()` 自动传播；另提供 RestTemplate / HttpClient traceId 拦截器。
- **服务信息**（随包能力，`plugin.service-info`）：`GET /service-info` 端点 + 启动横幅，可选基础认证（用户名 / 密码 / IP 白名单）。
- **SPI 扩展**：`META-INF/extensions` 注册 `DefaultConsole`（watchlog 控制台）。

## 引入方式

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-logger-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

引入即生效（无需额外开关）：

- 默认角色 `client`：仅注入 Appender 采集并推送，不启动接收端。
- Logback / Log4j2 依赖为 `optional`：模块按宿主既有绑定自动探测，不强制翻转。
- `/v2/logger` 检索 API 仅在 Servlet Web 环境注册；纯非 Web 应用只提供 TCP 接收与存储能力。

## 配置说明

### 配置总表

#### `plugin.logger.*`（采集与接收主配置）

| 配置键 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `plugin.logger.type` | String | `client` | 角色：`client` / `server` / `both` |
| `plugin.logger.app-name` | String | `""` | 应用名；为空回落 `spring.application.name`，再回落 `unknown` |
| `plugin.logger.transport` | String | `tcp` | 传输协议，目前固定 `tcp` |
| `plugin.logger.frameworks` | String | `auto` | slf4j 绑定探测：`auto` / `logback` / `log4j2` / `none` |
| `plugin.logger.client.service-name` | String | `logger-server` | 服务端在发现中心的服务名 |
| `plugin.logger.client.scatter-id` | String | `""` | 业务分组；为空回落 `client.service-name` |
| `plugin.logger.client.server-url` | String | `""` | 显式服务端地址（如 `tcp://172.16.9.194:19391`），发现不可用时兜底 |
| `plugin.logger.client.batch-size` | int | `100` | 批量推送条数阈值；配置 ≤ 0 时按 `1` 生效 |
| `plugin.logger.client.flush-interval-ms` | long | `1000` | 刷新间隔（毫秒）；配置 < 50 时按 `50` 生效 |
| `plugin.logger.client.queue-capacity` | int | `8192` | 内存队列容量；配置 < 256 时按 `256` 生效；满则丢弃并计数 |
| `plugin.logger.server.host` | String | `0.0.0.0` | TCP 监听地址 |
| `plugin.logger.server.port` | int | `19391` | TCP 监听端口 |
| `plugin.logger.server.service-name` | String | `logger-server` | 注册到发现中心的服务名（与客户端对齐） |
| `plugin.logger.server.scatter-id` | String | `""` | 注册分组；为空回落 `server.service-name` |
| `plugin.logger.server.ingest-token` | String | `""` | `POST /v2/logger/ingest` 共享密钥；为空不校验，非空时要求请求头 `x-logger-token` 相等 |
| `plugin.logger.storage.engine` | String | `datasource-bean` | 存储引擎：`datasource-bean` / `memory` / `redis-bean`；无 DataSource 或 SPI 缺失时回落 `memory` |
| `plugin.logger.storage.retention-hours` | long | `72` | 原始日志保留时长（小时） |
| `plugin.logger.storage.memory-capacity` | int | `100000` | `memory` 引擎最大保留条数；配置 &lt; 1000 时按 `1000` 生效；超出丢最旧事件 |
| `plugin.logger.storage.trace-state-path` | String | `./data/logger/trace-state` | 无 JDBC/Redis 时，SDK trace 登记与频控状态的本地磁盘目录；有 JDBC/Redis 时优先复用持久化 KV |
| `plugin.logger.server.trace-requests-per-minute` | int | `60` | 单个用户+应用每分钟允许登记的请求生命周期数；超限封锁 30 分钟 |

#### `plugin.log.*`（参数日志 / 慢请求）

| 配置键 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `plugin.log.enable` | boolean | `true` | 是否启用参数日志过滤器（记录请求方法 / URL / 参数 / 自定义头 / 耗时） |
| `plugin.log.open-interface-log` | boolean | `false` | 是否开启接口日志事件 |
| `plugin.log.slow-request-threshold-ms` | long | `3000` | 慢请求阈值（毫秒），超过打印 WARN |

> 参数日志过滤器 Bean 的注册条件为 `plugin.log.enable=true`（未显式配置时不注册）。该配置类属随包迁移能力，需宿主装配 `LogConfiguration` 才生效（见「注意事项」）。

#### `plugin.log.portal.*`（链路日志汇聚，默认关闭）

| 配置键 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `plugin.log.portal.enabled` | boolean | `false` | 是否启用链路日志汇聚；`true` 才装配 Appender |
| `plugin.log.portal.file` | String | `./logs/trace-portal.log` | 汇聚块输出文件路径 |
| `plugin.log.portal.max-events-per-trace` | int | `200` | 单链路最大缓冲条数，达到即收口 |
| `plugin.log.portal.idle-millis` | long | `3000` | 链路空闲多久（毫秒）自动收口 |
| `plugin.log.portal.max-pending-traces` | int | `512` | 最大待收口链路数，超过淘汰最老的 |
| `plugin.log.portal.sweep-interval-millis` | long | `1000` | 空闲清扫周期（毫秒） |

#### `plugin.service-info.*`（服务信息端点与横幅）

| 配置键 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `plugin.service-info.enable` | boolean | `true` | 是否启用 `/service-info` 端点与横幅（仅 Web 环境） |
| `plugin.service-info.banner` | boolean | `true` | 启动时是否输出横幅 |
| `plugin.service-info.auth-enable` | boolean | `false` | 是否对 `/service-info` 启用认证（过滤器注册条件 `plugin.service-info.auth.enable=true`） |
| `plugin.service-info.auth-username` | String | `admin` | 认证用户名 |
| `plugin.service-info.auth-password` | String | `""` | 认证密码；生产环境务必显式配置 |
| `plugin.service-info.auth-ip-whitelist` | Set&lt;String&gt; | 空 | IP 白名单（精确 / 通配符 / CIDR），命中跳过认证 |
| `plugin.service-info.infrastructure` | boolean | `true` | 横幅是否展示基础设施连接信息（仅展示真实配置了的中间件） |

### 逐项配置示例

**角色与采集客户端：**

```yaml
plugin:
  logger:
    type: client                # client：只采集推送；server：只接收；both：单进程闭环
    app-name: order-service     # 缺省回落 spring.application.name
    frameworks: auto            # auto | logback | log4j2 | none
    client:
      service-name: logger-server
      scatter-id: prod-cluster  # 同一服务名下区分集群；留空回落 service-name
      server-url: ""            # 留空走服务发现；直连示例：tcp://172.16.9.194:19391
      batch-size: 200           # 攒满 200 条即推送（<=0 按 1）
      flush-interval-ms: 500    # 500ms 未攒满也推送（<50 按 50）
      queue-capacity: 16384     # 有界队列容量（<256 按 256）
```

**接收服务端：**

```yaml
plugin:
  logger:
    type: server
    server:
      host: 0.0.0.0
      port: 19391
      service-name: logger-server
      scatter-id: prod-cluster
      ingest-token: change-me   # 浏览器 /v2/logger/ingest 推送密钥；留空不校验
    storage:
      engine: datasource-bean   # datasource-bean | memory | redis-bean
      retention-hours: 168      # 保留 7 天
      memory-capacity: 200000   # memory 引擎上限
      trace-state-path: ./data/logger/trace-state # 无共享 KV 时的磁盘状态目录
```

**链路日志汇聚（默认关闭，按需开启）：**

```yaml
plugin:
  log:
    portal:
      enabled: true
      file: ./logs/trace-portal.log
      max-events-per-trace: 200
      idle-millis: 3000
      max-pending-traces: 512
      sweep-interval-millis: 1000
```

**参数日志与慢请求：**

```yaml
plugin:
  log:
    enable: true
    open-interface-log: false
    slow-request-threshold-ms: 3000
```

**服务信息端点（开启认证示例）：**

```yaml
plugin:
  service-info:
    enable: true
    banner: true
    auth-enable: true
    auth-username: admin
    auth-password: change-me
    auth-ip-whitelist:
      - 127.0.0.1
      - 192.168.0.0/16
    infrastructure: true
```

### 完整 YAML 使用示例

单机联调（both：同进程采集 + 接收 + 检索）：

```yaml
spring:
  application:
    name: order-service

plugin:
  logger:
    type: both                  # 客户端与服务端同进程，自动回环 127.0.0.1:19391
    app-name: order-service
    frameworks: auto
    client:
      service-name: logger-server
      batch-size: 100
      flush-interval-ms: 1000
      queue-capacity: 8192
    server:
      host: 0.0.0.0
      port: 19391
      ingest-token: change-me
    storage:
      engine: datasource-bean   # 复用容器 DataSource；无 DataSource 时自动回落 memory
      retention-hours: 72
      memory-capacity: 100000
  log:
    enable: true
    open-interface-log: false
    slow-request-threshold-ms: 3000
  service-info:
    enable: true
    banner: true
```

生产分离部署：业务应用用上面 `type: client` 的客户端片段；日志服务单独部署 `type: server` 的服务端片段，二者通过服务发现（或 `client.server-url`）对接。

## 使用示例

**1. 浏览器页面接入（零依赖 SDK）：**

```html
<script src="http://your-logger-server:8080/v2/logger/sdk.js"></script>
<script>
  LoggerJS.init({
    app: 'my-web-app',        // 应用名，缺省取 location.hostname
    level: 'info',            // console 采集下限：debug|info|warn|error
    console: true,            // 劫持 console
    errors: true,             // 捕获 window.onerror / unhandledrejection
    network: true,            // 采集 fetch / XHR 网络失败
    resources: true,          // 采集资源加载错误
    trace: true,              // 跟随 x-trace-id
    sampleRate: 1,            // 采样率 0~1
    beforeSend: null          // (event) => event | false，返回 false 丢弃
  });
  LoggerJS.info('页面已就绪');
  LoggerJS.error('下单失败', { orderId: 123 });
</script>
```

**2. 后端推送（与 TCP 通道同构）：**

```bash
curl -X POST "http://your-logger-server:8080/v2/logger/ingest" \
  -H "Content-Type: application/json" \
  -H "x-logger-token: change-me" \
  -H "x-logger-app: my-web-app" \
  -d '{"events":[{"level":"ERROR","logger":"pay","message":"refund failed","traceId":"abc123"}]}'
```

**3. 检索与实时流：**

```bash
# 分页查询（limit 1~500）
curl "http://your-logger-server:8080/v2/logger/events?app=order-service&level=ERROR&limit=100"

# 链路摘要
curl "http://your-logger-server:8080/v2/logger/traces?limit=50"

# 统计
curl "http://your-logger-server:8080/v2/logger/stats"

# SSE 实时日志流（事件名 log / heartbeat / open）
curl -N "http://your-logger-server:8080/v2/logger/stream?level=ERROR"
```

**4. 后端业务代码**：无需改造——slf4j Appender 已挂到日志绑定上，`log.info(...)` / `log.error(...)` 自动进采集队列；MDC 中的 `traceId` 随事件一同上报。

## 注意事项

- **角色选择**：默认 `client` 只采集不接收；生产建议业务应用用 `client`、独立部署 `server`；单机/联调用 `both`（自动回环本进程接收端，无需服务发现）。
- **寻址兜底顺序**：客户端优先走服务发现（`client.service-name` + `scatter-id`），发现不可用时用 `client.server-url`；`both` 且未配 `server-url` 时回环 `tcp://127.0.0.1:{server.port}`。
- **过载策略**：采集队列为有界队列，满时丢弃新事件并计数，不阻塞业务线程；`batch-size` / `flush-interval-ms` / `queue-capacity` 配置过小会被抬到下限（1 / 50ms / 256）。
- **存储回落**：`datasource-bean` 需容器存在 `DataSource`；`redis-bean` 需 SPI 探测到 Redis 版 KvEngine；二者不可用时均回落 `memory` 并输出 WARN。`memory` 引擎仅保留 `memory-capacity` 条，适合开发联调。
- **建表**：持久化存储会在启动时经 `db/init/V1.0__init_all_logger.sql` 幂等创建 `logger_eps`（日志事件速率时序表，`CREATE TABLE IF NOT EXISTS`），无需手工执行。
- **HTTP 推送鉴权**：生产环境建议设置 `server.ingest-token`，浏览器 SDK 与 `POST /v2/logger/ingest` 调用方需携带请求头 `x-logger-token`。
- **Web 依赖**：`/v2/logger` 检索 API 与 `/service-info` 端点仅在 Servlet Web 环境装配；非 Web 宿主只有采集 / 接收 / 存储能力。
- **随包迁移能力**：`LogConfiguration`（参数日志过滤器）与 `ServiceInfoAutoConfiguration`（服务信息端点 / 横幅）不在本模块 `AutoConfiguration.imports` 清单中，属随包迁移的历史能力，需宿主通过组件扫描或显式 `@Import` 装配后，对应配置项才会真正生效。
- **链路汇聚默认关闭**：`plugin.log.portal.enabled` 默认 `false`，关闭时不分配缓冲、不起线程、不改动任何现有 appender；开启后输出到 `plugin.log.portal.file`。
- **隐私与凭据**：本页所有口令 / 密钥均为占位符（如 `change-me`），接入时请替换为真实凭据并纳入配置中心 / 环境变量管理。

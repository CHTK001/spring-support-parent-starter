# spring-support-hotsport-starter

APM 热点观测模块：基于 `utils-support-runtime-apm` 的运行时数据源（`StorageManager` SPI），以 REST API 暴露传输记录 / 依赖图 / 资源泄漏 / 日志 / JVM / 告警等观测数据，自注入 Java Agent 对当前 JVM 字节码插桩，并托管 Vue 前端构建产物（classpath:/static/，单 jar 交付）。
采集相关能力默认开启（`plugin.hotsport.request-record.enabled`、`plugin.hotsport.log-capture.enabled`、`plugin.agent.enabled` 缺省均生效），但作为独立观测后台使用时以 `HotsportApplication` 启动并显式排除 `DataSourceAutoConfiguration`，不依赖任何外部数据库（存储默认内嵌 SQLite）。

## 一、模块能力

| 能力域 | 关键类 | 入口 |
|:-------|:-------|:-----|
| 配置绑定 | `HotsportProperties` | `plugin.hotsport.*` |
| Agent 自注入 | `ApmQueryService#attachAgent`（异步，虚拟线程） | 依赖 `spring-support-agent-starter` |
| Agent 自注入降级 | `AgentAttachFallbackConfiguration` | `plugin.agent.enabled=false` 时兜底 |
| 存储后端（SQLite/MySQL/内存） | `StorageManager` SPI + `JdbcStorage`（type=mysql） | `plugin.hotsport.apm.storage.*` |
| 请求采集过滤器 | `SlowCallFilter` / `SlowCallAutoConfiguration` | `plugin.hotsport.request-record.enabled` |
| 控制器耗时拦截器 | `ApmHandlerTimingInterceptor` / `ApmHandlerTimingConfiguration` | 同左，一套采集链 |
| 应用日志采集 | `ApmLogAppender` / `ApmLogCaptureAutoConfiguration` | `plugin.hotsport.log-capture.*` |
| JVM 指标采样 | `JvmMetricsService`（`@Scheduled`） | `plugin.hotsport.jvm.sample-interval-ms` |
| JVM 内存诊断 | `JvmMemoryService` | `/api/apm/jvm/memory/pools` 等 |
| 告警监控 | `AlertMonitorService` / `AlertStore`（100 条环形缓冲 + MySQL 持久化可选） | `/api/apm/alerts/**` |
| 查询门面 | `ApmQueryService` | REST 统一前缀 `/api/apm` |
| 观测接口 | `ApmController`（60+ 端点，含 SSE 实时流） | `/api/apm/**` |
| 前端托管 | `SpaWebConfig` | classpath:/static/ + SPA 路由回退 |

实际生效的能力（全部读取真实数据，不做任何模拟注入）：

- 真实 HTTP 请求采集：Servlet 过滤器记录每次请求（方法、路径、状态码、耗时、客户端 IP、响应字节），静态资源、favicon、actuator、健康检查与 `/api/apm/**` 自身不计入；
- 控制器调用层级还原：拦截器把真实命中的 `class.method` 与其自身耗时写入请求属性，随传输记录落库；
- 真实日志采集：logback appender 挂到 root logger，级别、logger、类名、方法名、消息、MDC traceId 原样入库（不采集 APM 自身与存储实现日志，防自我放大）；
- JVM 指标：堆内存 / GC / 线程 / CPU 快照 + 2000 点环形缓冲时序（默认 5s 采样，约 10 小时）；
- 告警巡检：默认 3 条规则（堆使用率 >90% CRITICAL、错误率 >10% CRITICAL、活跃泄漏 >3 WARNING），支持运行时增删改、事件状态机 OPEN → ACKNOWLEDGED / MUTED / IGNORED、Webhook 通知渠道；
- SSE 实时流：`/api/apm/stream`（stats，默认 3s 一帧）与 `/api/apm/stream/logs`（增量日志，游标续读）；
- 存储运行时可切换：`sqlite`（默认）/ `mysql` / `inmemory` / `noop`，`POST /storage/config` 热更新且仅在新存储成功启动后才替换；
- 全量 CSV 导出（传输/泄漏/日志）与一键诊断包导出。

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-hotsport-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

关键依赖：

| 依赖 | 作用 |
|:-----|:-----|
| `spring-boot-starter-web` | REST 接口、SSE、静态资源托管 |
| `spring-support-common-starter` | `ReturnResult` 响应体、统一异常处理 |
| `utils-support-runtime-apm` | `StorageManager` / `Query` / `ApmStorage` SPI 与 8 个采集 Handler |
| `utils-support-runtime-protocol` | 协议值对象（`TransmissionEvent` / `DependencyEdge` / `Endpoint`） |
| `spring-support-agent-starter` | Agent 自注入自动配置（`AgentAttachService`，显式依赖） |
| `com.mysql:mysql-connector-j` | **optional**：仅 `apm.storage.type=mysql` 时由使用方显式引入 |

MySQL 驱动刻意做成 optional：默认存储是内嵌 SQLite，宿主不该为观测组件多背 2.5MB 驱动；要接 MySQL 时由使用方显式引入。

## 三、配置项

配置前缀：**`plugin.hotsport`**（Agent 自注入开关在 **`plugin.agent`**，由 `spring-support-agent-starter` 的 `AgentAttachProperties` 消费）。

### 3.1 APM 存储（`plugin.hotsport.apm.storage.*`）

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.hotsport.apm.storage.type` | `sqlite` | 存储类型：`sqlite`（默认）/ `mysql` / `inmemory` / `noop` |
| `plugin.hotsport.apm.storage.retention-ms` | `604800000`（7 天） | 数据保留毫秒，超出按保留期清理 |
| `plugin.hotsport.apm.storage.capacity` | `100000` | 单表行数上限，超出按最旧淘汰 |
| `plugin.hotsport.apm.storage.path` | 空串 | 落盘目录（不是文件，库文件为 `<path>/apm.db`）；空时用临时目录 |
| `plugin.hotsport.apm.storage.url` | 空串 | JDBC URL（type=mysql 时必填，形如 `jdbc:mysql://127.0.0.1:3306/hotsport`） |
| `plugin.hotsport.apm.storage.username` | 空串 | 数据库用户名（type=mysql 时使用） |
| `plugin.hotsport.apm.storage.password` | 空串 | 数据库密码（type=mysql 时使用） |
| `plugin.hotsport.apm.storage.pool-size` | `4` | 连接池大小（type=mysql 时使用） |
| `plugin.hotsport.apm.storage.batch-size` | `200` | 单批写入条数（type=mysql 时使用） |
| `plugin.hotsport.apm.storage.async` | null（缺省走 APM 侧默认=异步） | 写入模式：`true` 异步批量写（业务线程不阻塞），`false` 逐条同步写 |
| `plugin.hotsport.apm.storage.maintenance-interval-ms` | `0`（0=沿用 APM 默认 60 秒） | 自维护周期：清理过期数据、按容量淘汰的检查间隔；经接口设置时下限 5000 |
| `plugin.hotsport.apm.storage.flush-timeout-ms` | `0`（0=沿用 APM 默认） | 关闭时等待异步队列落盘的最长时间 |

> `plugin.hotsport.apm.storage.*` 由 `HotsportProperties.Apm#toStorageConfig()` 展开为 `apm.storage.*` 键交给 `ApmBootstrap` 消费；其中 url/username/password 会从 provider 剔除并写入系统属性（`ApmBootstrap.resolveStorageConfig` 只合并以 `apm.storage.` 开头的系统属性键）。

### 3.2 请求采集（`plugin.hotsport.request-record.*`）

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.hotsport.request-record.enabled` | `true` | Servlet 过滤器 + 控制器耗时拦截器总开关（两者共用，一套采集链） |
| `plugin.hotsport.request-record.threshold-ms` | `0` | 记录阈值：0 = 记录全部真实请求，正数 = 只记录耗时达到该值的请求 |

### 3.3 日志采集（`plugin.hotsport.log-capture.*`）

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.hotsport.log-capture.enabled` | `true` | 应用日志采集开关；仅在 classpath 存在 logback 时生效 |
| `plugin.hotsport.log-capture.min-level` | `INFO` | 最低采集级别 |
| `plugin.hotsport.log-capture.caller-data` | `true` | 是否采集调用方类名/方法名（`getCallerData()` 有一定开销） |
| `plugin.hotsport.log-capture.max-message-length` | `4096` | 单条消息最大长度，超出截断 |

### 3.4 JVM 采样

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.hotsport.jvm.sample-interval-ms` | `5000` | JVM 指标采样间隔；经 `JvmMetricsService#recordSample` 的 `@Scheduled(fixedDelayString=...)` 读取 |

### 3.5 Agent（`plugin.agent.*`，由 spring-support-agent-starter 消费）

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.agent.enabled` | `true` | 是否自注入 Agent；`false` 时 `AgentAttachFallbackConfiguration` 提供禁用态实例兜底，本地 APM 引擎照常工作 |
| `plugin.agent.shell-port` | `4567` | Agent Shell 端口（`selfIdentity()` 中作为 `AGENT/Hotsport Shell` 端口回报） |
| `plugin.agent.options` | `""` | 额外 Agent 参数（逗号分隔 Key=值） |
| `plugin.agent.async` | `true` | 异步注入 |

### 3.6 本模块 Properties 中「仅声明」字段

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.hotsport.agent.shell-port` | `4567` | **仅声明，运行期未读取**：实际生效的是 `plugin.agent.shell-port` |
| `plugin.hotsport.agent.options` | `""` | **仅声明，运行期未读取**：实际生效的是 `plugin.agent.options` |
| `plugin.hotsport.agent.enabled` | `true` | **仅声明，运行期未读取**：实际生效的是 `plugin.agent.enabled` |

> `HotsportProperties.Agent` 三个字段在 `@ConfigurationProperties` 声明，但控制器与查询服务只消费 `getApm()` / `getStorage()`，Agent 类从未被 `getAgent()` 取用；前端「Agent 中心」回显的配置来自 `AgentAttachProperties` 与 `POST /api/apm/agent/config`。

## 四、快速开始

**第一步：引入依赖**（见上）。

**第二步：配置 yaml**

```yaml
server:
  port: 8086

spring:
  web:
    resources:
      static-locations: classpath:/static/   # 前端构建产物托管

plugin:
  hotsport:
    apm:
      storage:
        type: sqlite                         # 默认即 sqlite，可省略
        path: ${user.home}/.hotsport/apm     # 落盘目录，库文件 <path>/apm.db
        retention-ms: 604800000              # 7 天
  agent:
    enabled: true                            # 自注入 Agent（spring-support-agent-starter）
```

**第三步：验证**

1. 启动后访问 `http://127.0.0.1:8086/` 打开 APM 看板（前端已打包进 classpath:/static/）；
2. curl 冒烟：
   ```bash
   curl http://127.0.0.1:8086/api/apm/stats
   curl http://127.0.0.1:8086/api/apm/agent/status
   curl "http://127.0.0.1:8086/api/apm/transmissions?page=1&size=20"
   ```
3. 打开几个页面制造真实请求，再查 `/api/apm/transmissions`，应出现 `trace-http-*` 开头的真实传输记录；`/api/apm/logs` 应出现宿主真实日志（logback 环境）。

启动日志参考：`[Hotsport] 请求采集过滤器已注册，阈值 0ms（0=记录全部真实请求）`、`[Hotsport] 应用日志采集已启用，级别 >= INFO（真实日志写入 APM 存储）`、`[Hotsport] 本进程 APM 引擎已启动（存储: sqlite，与查询同源）`。

## 五、运行示例

### 5.1 调用示例

注入查询服务读取观测数据（无链式 API，全部经 service 层方法）：

```java
@Service
public class ApmReadDemo {

    private final ApmQueryService apmQueryService;

    public ApmReadDemo(ApmQueryService apmQueryService) {
        this.apmQueryService = apmQueryService;
    }

    public Map<String, Object> summary() {
        // 概览统计：agent 状态 + 各类总量
        Map<String, Object> stats = apmQueryService.stats();
        // 最近 10 分钟 JVM 堆/GC/线程时序
        List<Map<String, Object>> trend = apmQueryService.trend(10, 60);
        // 慢调用排行（按耗时降序）
        List<Map<String, Object>> slow = apmQueryService.slowCalls(20);
        return Map.of("stats", stats, "trendPoints", trend.size(), "slowCalls", slow);
    }
}
```

常用 REST 端点一览：

```bash
# 概览统计 / 健康 / 传输记录分页
curl http://127.0.0.1:8086/api/apm/stats
curl http://127.0.0.1:8086/api/apm/health
curl "http://127.0.0.1:8086/api/apm/transmissions?errorOnly=true"

# 链路 / 依赖 / 泄漏 / 日志
curl "http://127.0.0.1:8086/api/apm/traces/summary?limit=100"
curl "http://127.0.0.1:8086/api/apm/dependencies/aggregated"
curl "http://127.0.0.1:8086/api/apm/leaks?activeOnly=true"
curl "http://127.0.0.1:8086/api/apm/logs?level=ERROR"

# SSE 实时流
curl -N http://127.0.0.1:8086/api/apm/stream
curl -N "http://127.0.0.1:8086/api/apm/stream/logs?after=0"
```

### 5.2 SPI 扩展示例

**本模块不提供 `META-INF` 式 SPI 扩展点**；存储层扩展走 `utils-support-runtime-apm` 的 `ApmStorage` SPI（`StorageManager.register()` 注册自定义类型后把 `plugin.hotsport.apm.storage.type` 指过去即可，本模块的 `JdbcStorage`（type=mysql）就是按此接入的范例）：

```java
// 自定义存储：注册到 StorageManager 后，type 配成自定义名字即命中
public class MyStorage implements ApmStorage {
    @Override public String name() { return "my-storage"; }
    // ... append/query/cleanup 实现
}

// 启动时注册（ApmQueryService#startLocalApmEngine 会在引擎启动前调用 JdbcStorage.register()）
com.chua.runtime.apm.storage.StorageManager.register(MyStorage::new, "my-storage");
```

日志采集场景要扩展采集范围（如新增排除前缀、阈值），只能替换 `ApmLogAppender` Bean 或调整 `plugin.hotsport.log-capture.*` 配置；告警规则与通知渠道可在运行时经 `/api/apm/alerts/rules`、`/api/apm/alerts/channels` 增删改。

## 六、与其它 starter 的关系

| 模块 | 方向 | 说明 |
|:-----|:-----|:-----|
| `spring-support-agent-starter` | 本模块依赖 | 提供 `AgentAttachService` 与 Agent 自动注入；`plugin.agent.*` 由其消费 |
| `spring-support-common-starter` | 本模块依赖 | `ReturnResult`、统一异常处理（其引用 `DataAccessException` 使 spring-jdbc 留在 classpath，但应用入口显式排除 `DataSourceAutoConfiguration`） |
| `utils-support-runtime-apm` | 本模块依赖 | `StorageManager` SPI、8 个采集 Handler、APM 引擎 |
| `utils-support-runtime-protocol` | 本模块依赖 | 传输/依赖/端点值对象 |
| `spring-support-osgi-starter` | 独立模块，无依赖 | OSGi 插件框架，与本模块无关联 |
| `vue-support-hotspot-starter`（前端 app） | 构建产物托管于本模块 | `vue-support-parent-starter` 下构建，产物拷贝至 `src/main/resources/static/` 单 jar 交付 |

注意：`spring-support-hotsport-starter` 是**观测组件**，不应与「业务监控系统」或 `spring-support-device-starter`（设备接入）混淆；它观测的是承载它的那个 JVM/进程自身。

## 七、已知限制

- **独立形态必须排除 DataSourceAutoConfiguration**：`HotsportApplication` 显式排除 `DataSourceAutoConfiguration`；若自行组合应用，不要引入会强制要求可连数据源的配置，否则观测后台会因连不上外部库而启动失败。
- **`plugin.hotsport.agent.*` 三个字段不生效**：`agent.enabled / agent.shell-port / agent.options` 仅有声明，运行期全部读取 `plugin.agent.*`；不要在 `plugin.hotsport.agent` 下配置，写了不会生效。
- **mysql 驱动是 optional**：type=mysql 而未显式引入 `mysql-connector-j` 时，存储初始化会失败并降级为 `NoopStorage`（业务进程照常启动，但页面无数据；`JdbcStorage` 写入走有界队列 + 单写线程，队列满丢弃并计数，不阻塞业务线程）。
- **日志采集依赖 logback**：非 logback 日志实现下 `ApmLogCaptureAutoConfiguration` 整体不加载，日志页恒为空（日志采集也不采集 APM 自身与存储实现，按 logger 前缀排除，且按消息关键字过滤 SSE 断开等自噪声）。
- **分页是全量拉取后内存切片**：`transmissions` / `leaks` / `logs` 先 `Query.setLimit(100000)` 全量拉取再内存过滤分页；单表数据超过 10 万时大页翻查会明显变慢。
- **环形缓冲有固定容量**：告警事件环形缓冲 100 条（历史需走 MySQL 持久化 `AlertStore`）；JVM 时序环形缓冲 2000 点（约 10 小时 @5s）。`/jvm/metrics` 传更大 `minutes` 也只能看到缓冲内数据。
- **Agent 自注入失败不阻断**：`attachAgent` 在虚拟线程异步执行，失败只打日志；`agent-active` 为 false 时大部分页面数据来自本地 APM 引擎降级路径或直接为空。
- **SSE 超时 5 分钟**：`/stream` 与 `/stream/logs` 的 `SseEmitter` 超时 5 分钟（`5L * 60 * 1000`），闲置连接会被服务端完成关闭，前端需自行重连；推送线程池固定 4 线程（`hotsport-sse`），单个慢客户端最多占一个线程。
- **CSV 导出是全量直写**：`/export/*` 不受分页限制，数据量大时响应体与内存占用会跟着涨。
- **`cleanup` 与 `POST /storage/config` 的钳制**：`maintenance-interval-ms` 经接口设置时下限 5000ms；`/cleanup` 默认保留 24 小时（86400000ms）。
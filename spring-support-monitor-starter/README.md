# spring-support-monitor-starter

服务器与中间件监控的一站式模块：资产台账 + 采集任务 + 指标时序 + 小时聚合 + 告警 + 运维操作 + AI 诊断。既可作为 starter 被宿主引入，也可打成可执行包独立启动成监控台。

## 一、模块能力

| 能力域 | 关键类 | 入口 |
|---|---|---|
| 自动装配 | `MonitorAutoConfiguration`（`@ComponentScan com.chua.starter.monitor.support`、`@EnableScheduling`、`@EnableTransactionManagement`） | `META-INF/spring/...AutoConfiguration.imports` |
| 远程控制装配 | `ControlRestConfiguration`（WebRTC 远程桌面 / `/control` REST、`/webrtc` 信令） | 同上 |
| 资产主数据 | `MonitorService`（`create` / `update` / `delete` / `findById` / `listAll` / `listByServiceType` / `listByServerHost` / `page`） | `ms_monitor`，`/ms/monitor` |
| 采集任务 | `MonitorTaskService`、`MonitorScheduler` | `ms_monitor_task`，`/ms/monitor/task` |
| 采集 Provider 路由 | `MetricProvider` + `ProviderRegistry`（`ServiceType` × `ProviderType` 双键） | SPI `META-INF/extensions/...MetricProvider` |
| 指标时序 | `MetricService`（`writePoints` / `getLatestCpu` / `getCpuHistory` / `getMetricStatistics` / `exportMetricHistory`…） | 5 张明细表 + 通用时序 |
| 小时聚合 | `MetricService#aggregateHourly` | `@Scheduled(cron = "0 0 * * * ?")` |
| 过期清理 | `MetricService#cleanupOldData` | `monitor.cleanup.cron`（默认每小时） |
| Redis 运维 | `RedisManageService`、`SystemRedisKvService` | `/ms/monitor/redis-manage`、`/ms/monitor/system/redis/kv` |
| 告警 | `AlarmController` + `AlarmEngineServiceImpl` | `ms_monitor_alarm_*`，`/ms/monitor/alarm` |
| 主机运维 | `HostOperations`（`/ms/server/host/**`：文件、网络、进程、服务） | `HostOperationsProvider` SPI |
| 网络抓包 | `TsharkCaptureController`、`TsharkRuntime`、`CdpController` | `/ms/monitor/tshark`、`/ms/cdp` |
| 数据同步 | `SyncTaskService` / `SyncTaskServiceImpl` | `ms_sync_task`，`/ms/monitor/datasync` |
| Agent 部署 | `AgentDeployController`、`AgentTcpServerConfiguration` | `/ms/server/host/agent`、`/ms/monitor/agent` |
| AI 诊断 | `AiChatFacadeService`（来自 `spring-support-ai-starter`） | `/ms/monitor/ai/status`、`/ai/analyze`、`/ai/host/{id}/analyze` |
| 端口映射 | `spring-support-tunnel-starter` 统一入口 | `/ms/tunnel`（`ngrok` / `cloudflared` 只是 SPI 实现） |
| 服务发现查询 | `ServerDiscoveryController`（`spring-support-discovery-starter` 为 `optional`） | `/ms/monitor/discovery` |
| 缓存 | `monitor` / `monitorTask` / `monitorConfig` / `monitorMetricConfig` / `monitorGateway` / `monitor:metric:latest` / `monitor:alarm:*` | `@Cacheable` / `@CacheEvict` |

**核心数据表（14 张）**：

| # | 表名 | 用途 |
|---|---|---|
| 1 | `ms_monitor` | 资产主表 |
| 2 | `ms_monitor_config` | 全局配置（含 `monitor.collect.interval.ms`） |
| 3 | `ms_monitor_task` | 采集任务 |
| 4 | `ms_monitor_metric_config` | 解析规则 |
| 5 | `ms_monitor_gateway` | Guacamole 网关 |
| 6 | `ms_monitor_metric` | 通用时序 |
| 7 | `ms_monitor_metric_statistics` | 通用小时聚合 |
| 8 | `ms_monitor_metric_cpu` | CPU 明细 |
| 9 | `ms_monitor_metric_memory` | 内存明细 |
| 10 | `ms_monitor_metric_disk` | 磁盘明细 |
| 11 | `ms_monitor_metric_network` | 网络明细 |
| 12 | `ms_monitor_metric_jvm` | JVM 明细 |
| 13 | `ms_monitor_redis_metric` | Redis 时序 |
| 14 | `ms_monitor_redis_metric_statistics` | Redis 小时聚合 |

同一份 DDL（`V2.1__init_all_monitor.sql`）还建了告警（`ms_monitor_alarm_policy` / `_rule` / `_event` / `_maintenance_window`）、抓包（`ms_tshark_session` / `ms_tshark_packet`）、主机历史（`ms_host_metric_history`）、同步任务（`ms_sync_task`）、维护计划（`ms_maintenance_plan`）、节点（`ms_server_node`）；后续 `V4.0.0.44 ~ V4.0.0.59` 增量脚本补列并追加操作日志、ACME 等表。

**缓存**（`CacheManager` 名 → 典型 key）：

| Cache 名 | 典型 key | 对应数据 |
|---|---|---|
| `monitor` | `monitor:id:<id>`、`monitor:list:all` | `ms_monitor` |
| `monitorTask` | `monitor.task:id:<id>`、`monitor.task:enabled`、`monitor.task:monitor:<id>` | `ms_monitor_task` |
| `monitorMetricConfig` | `monitor.metricConfig:task:<taskId>` | `ms_monitor_metric_config` |
| `monitorConfig` | `monitor.config:key:<key>`、`monitor.config:all` | `ms_monitor_config` |
| `monitorGateway` | `monitor.gateway:monitor:<monitorId>` | `ms_monitor_gateway` |
| `monitor:metric:latest` | `cpu:` / `memory:` / `network:` / `jvm:` + monitorId | 最新快照 |
| `monitor:alarm:policies` / `monitor:alarm:rules` | `policies[:hostId]`、`rules[:policyId]` | 告警策略/规则 |

时序明细表（`*_metric` / `*_statistics` / `*_cpu` 等）**不缓存**，写路径直接落库（或落时序引擎）。

**采集 Provider**：

| Provider | Service Type | 适用 |
|---|---|---|
| `LOCAL` | SERVER（`ServerLocalProvider`）、REDIS（`RedisLocalProvider`） | metrics-starter / OSHI 直采本机（macOS 跳过） |
| `TERMINAL` | 全部 7 种（SERVER / REDIS / MYSQL / PG / ORACLE / MSSQL / MQ） | SSH 执行自定义命令（`utils-support-ssh-starter`） |
| `HTTP` | SERVER | 拉取 actuator / exporter 接口 |
| `PROMETHEUS` | 全部 7 种 | 统一 Prometheus（`utils-support-prometheus-starter`） |

通过双键 `(ServiceType, ProviderType)` 在 `ProviderRegistry` 中路由，索引首次解析时构建。

采集节奏（源码事实）：`MonitorScheduler#tick` 每 **10 秒**扫一次任务表，实际采集间隔取 `ms_monitor_config` 里的 `monitor.collect.interval.ms`（缺省 `10000`），并发上限 50；小时聚合整点执行；数据保留清理默认每小时一次（滚动窗口，避免「保留 1 天却存 2 天」）。

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-monitor-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

| 依赖 | 作用 |
|---|---|
| `spring-support-common-starter` | `ServiceInfoContributor`、通用基础设施 |
| `spring-support-server-starter` | 服务器台账（资产联动 `server-hosts` 查询） |
| `spring-support-datasource-starter` / `spring-support-mybatis-starter` | JDBC + MyBatis-Plus |
| `spring-support-cache-starter` | 缓存键生成器与多级缓存 |
| `spring-support-swagger-starter` | OpenAPI 3 + knife4j |
| `spring-support-ai-starter` | `ChatClient` / `AiChatFacadeService`（AI 诊断） |
| `spring-support-job-starter` | 定时发布 / Git 轮询所需调度 |
| `spring-support-tunnel-starter` + `spring-support-tunnel-ngrok-starter` + `spring-support-tunnel-cloudflared-starter` + `spring-support-tunnel-tailscale-starter` | 端口映射编排与三种 SPI 实现 |
| `spring-support-docker-starter` | 容器 / 镜像 / 网络 / 卷 |
| `spring-support-control-starter` + `utils-support-webrtc-starter` | WebRTC 远程控制（webrtc 原生包为 `optional`，宿主需显式声明） |
| `spring-support-device-starter` | `/api/device/**` 的唯一实现方 |
| `spring-support-apm-starter` + `-skywalking` + `-hotspot` | APM 观测 `/api/apm/v1`（两个引擎必须同时引入） |
| `spring-support-sync-starter` | 接收 `sync/client/register`、`monitor/app/report`，为在线节点列表供数 |
| `spring-support-account-starter` | 账号资产 / AppKey / 渠道管理 |
| `spring-support-queue-starter` + `spring-support-socket-starter` + `-socket-io` + `-socket-websocket` | 采集结果推送（push → queue → bridge → 前端） |
| `spring-support-filesystem-starter` | 文件服务器 `ServerFilter` |
| `spring-support-discovery-starter` | `optional`：`/ms/monitor/discovery` |
| `utils-support-metrics-starter` / `-ssh-starter` / `-redis-starter` / `-prometheus-starter` | 本地 OS 指标、SSH 终端、Lettuce、Prometheus |
| `utils-support-influxdb-starter` / `-tshark-starter` / `-datasync-starter` | 时序引擎、抓包、数据同步引擎 |
| `utils-support-zookeeper-starter` / `-elasticsearch-starter` / `-mysql-starter` | 中间件管理页与数据源中心 SPI |
| `oshi-core` + `jna` / `jna-platform` | 本地硬件信息（JNA 显式钉到 `5.19.1`，避免被旧传递依赖拖回 5.14） |
| `spring-api-support-common-system-starter` / `spring-api-support-monitor-starter` | pages/system 接口与 ACME 证书管理 |

打包说明：本模块既当库也当应用。默认 `spring-boot.repackage.skip=false` 产出 `*-exec.jar`（带 `exec` 分类符，**不替换主构件**），薄 jar 保持标准结构；`repackage` 后由 `maven-antrun-plugin` 把 `META-INF/spring` 与 `META-INF/extensions` 复制到 `BOOT-INF/classes`，否则 `java -jar` 启动时自动配置不生效。

## 三、配置项

| key | 默认值 | 说明 |
|---|---|---|
| `plugin.monitor.influx.enabled` | `false` | 是否启用 InfluxDB 写入/查询 |
| `plugin.monitor.influx.url` | `http://127.0.0.1:8086` | InfluxDB v1 兼容端点 |
| `plugin.monitor.influx.database` | `monitor` | 库名 |
| `plugin.monitor.timeseries.engine` | `influxdb` | 时序引擎：`influxdb` / `redis` / `datasource` / `none` |
| `plugin.monitor.timeseries.url` | `http://127.0.0.1:8086` | 时序存储地址（`redis://` 时覆盖 `spring.data.redis.*`） |
| `plugin.monitor.timeseries.database` | `monitor` | 库名 |
| `plugin.monitor.ssh.connect-timeout-seconds` | `15` | SSH 建连超时（慢链路可调大） |
| `plugin.monitor.ssh.session-timeout-seconds` | `30` | SSH 认证超时 |
| `plugin.monitor.ssh.exec-timeout-seconds` | `60` | 命令执行/读取超时 |
| `plugin.monitor.agent.jar-path` | 空 | agent.jar 路径；为空时按候选路径探测，落空则明确报错终止部署 |
| `plugin.monitor.agent.jar-url` | 空 | 平台包下载地址；非空时目标机自行 HTTP 拉取（WinRM 有单命令长度上限，大文件必须走这条） |
| `plugin.monitor.agent.tcp-port` | `19390` | Agent TCP 上报端口 |
| `plugin.monitor.tshark.bin` | 空 | tshark 可执行文件；为空按「环境变量 `TSHARK_BIN` → 本项 → 自动安装目录 → 系统路径 → PATH」探测 |
| `plugin.monitor.tshark.auto-install` | `true` | 探测不到时是否下载便携包（不装 Npcap 驱动） |
| `plugin.monitor.tshark.install-dir` | 空（`~/spring-support/tshark`） | 便携版解压目录 |
| `plugin.monitor.tshark.package-url` | 空 | 便携包下载地址 |
| `plugin.monitor.tshark.retention.enabled` | `true` | 是否自动清理抓包文件（关闭时只统计不删） |
| `plugin.monitor.tshark.retention.days` | `7` | 天数保留上限 |
| `plugin.monitor.tshark.retention.size-mb` | `512` | 目录空间上限（MB） |
| `monitor.cleanup.cron` | `0 0 * * * ?` | 指标保留清理调度 |
| `monitor.file-storage.local-path` | `${user.dir}/.data/filesystem-local` | 文件资产管理落地目录 |

运行时配置存在 `ms_monitor_config` 表（页面可改），例如 `monitor.collect.interval.ms`；不在 YAML 里。

## 四、快速开始

**第一步**：引入依赖并准备数据库。核心表由 `src/main/resources/db/init/V2.1__init_all_monitor.sql` 建（另有 `V4.0.0.44 ~ V4.0.0.59` 增量脚本与 `db/h2` 的 H2 版本）。

**第二步**：配置数据源与（可选的）时序引擎：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/monitor?useUnicode=true&characterEncoding=utf8
    username: root
    password: root
plugin:
  monitor:
    influx:
      enabled: false          # 不接 InfluxDB 时用 datasource 引擎
    timeseries:
      engine: datasource
```

**第三步**：启动后先建资产（`POST /ms/monitor`），再建采集任务（`POST /ms/monitor/task`），最后手动触发一次（`POST /ms/monitor/task/{id}/trigger`）验证 `points > 0`。

## 五、运行示例

### 5.1 调用示例

本模块的服务层无链式 API：REST 与 `MonitorService` / `MetricService` 都返回 `ReturnResult<T>`，按步骤调用。SPI 层的 `CollectResult` 反过来是链式的（`addPoint(...)` / `fail(...)` 返回 `this`），下面 5.2 会用到。

**REST 主链路**：

```bash
# 1. 建资产（SERVER 类型）
curl -s -X POST http://localhost:8080/ms/monitor \
  -H 'Content-Type: application/json' \
  -d '{"monitorName":"本机","serviceType":"SERVER","monitorHost":"127.0.0.1","monitorPort":8080,"monitorEnabled":1}'

# 2. 建采集任务（provider 取值 LOCAL/TERMINAL/HTTP/PROMETHEUS；每个任务自带 cron）
curl -s -X POST http://localhost:8080/ms/monitor/task \
  -H 'Content-Type: application/json' \
  -d '{"monitorId":1,"monitorTaskName":"本机采集","serviceType":"SERVER","provider":"LOCAL","monitorTaskEnabled":1,"monitorTaskCron":"0 */1 * * * ?"}'

# 3. 手动触发一次采集（忽略 cron 与启用状态，异步执行）
curl -s -X POST http://localhost:8080/ms/monitor/task/1/trigger

# 4. 读最新指标与历史（历史接口用 hours 表示「最近 N 小时」）
curl -s http://localhost:8080/ms/monitor/metric/cpu/1
curl -s "http://localhost:8080/ms/monitor/metric/cpu/history/1?hours=24"
curl -s http://localhost:8080/ms/monitor/dashboard/overview
```

**服务端直接调用**：

```java
@Service
@RequiredArgsConstructor
public class ReportService {

    private final MonitorService monitorService;
    private final MetricService metricService;

    public MsMonitor asset(Long id) {
        return monitorService.findById(id).getData();
    }

    public List<MsMonitorMetricCpu> cpuTrend(Long monitorId, LocalDateTime from, LocalDateTime to) {
        return metricService.getCpuHistory(monitorId, from, to).getData();
    }

    /** 手工补一次小时聚合（正常情况下由调度器整点执行） */
    public void aggregateNow() {
        metricService.aggregateHourly();
    }
}
```

**采集路由**（`ProviderRegistry#resolveMetric`）：按任务行的 `(service_type, provider)` 双键取实现，`MetricProvider#providerType()` 与 `supportServiceTypes()` 决定它能接哪些任务。`ProviderType` 只有四个值（`LOCAL` / `TERMINAL` / `HTTP` / `PROMETHEUS`），`ServiceType` 有七个（`SERVER` / `REDIS` / `MYSQL` / `PG` / `ORACLE` / `MSSQL` / `MQ`）。

**接口清单**：见 [`任务计划.md`](./任务计划.md) 末节；**架构图**：见 [`架构图.md`](./架构图.md)。

### 5.2 SPI 扩展示例

本模块的扩展点都在 `META-INF/extensions/` 下，登记格式为 `名称=实现类全限定名`（或一行一个类名）。

| 扩展点 | 用途 |
|---|---|
| `com.chua.starter.monitor.support.spi.MetricProvider` | 新增采集方式 / 服务类型（已内置 `ServerLocalProvider`、`ServerTerminalProvider`、`ServerHttpProvider`、`RedisLocalProvider`、`PrometheusProvider`） |
| `com.chua.starter.monitor.support.spi.OperationProvider` | 中间件运维操作（按键值/慢查询/进程等） |
| `com.chua.starter.server.support.spi.HostCollectProvider` / `HostDetectProvider` / `HostAgentHistoryWriter` | 主机采集、探活、Agent 历史写入 |
| `com.chua.starter.monitor.support.spi.SystemInfoProvider` | 系统信息分区（已内置 `LocalOshiProvider`、`AgentProvider`、`SshProvider`、`WindowsWinrmProvider`） |
| `com.chua.starter.monitor.support.spi.HostOperationsProvider` | 主机运维动作（Posix / Windows / Mac / Agent） |
| `com.chua.starter.monitor.support.service.alarm.NotifySender` | 告警通道（内置 `LogNotifySender`、`WebhookNotifySender`） |
| `com.chua.starter.monitor.support.nginx.spi.NginxExecutor` / `NginxCommandProvider` | Nginx 管理 |
| `com.chua.starter.monitor.support.datasync.loader.JdbcCsvLoader` | CSV / 批量导入 |

**实现**（以「按 HTTP exporter 文本采集 MQ 指标」为例，关键是两个键方法）：

```java
public class MyMqHttpProvider implements MetricProvider {

    @Override
    public ProviderType providerType() {
        return ProviderType.HTTP;
    }

    @Override
    public Set<ServiceType> supportServiceTypes() {
        return Set.of(ServiceType.MQ);
    }

    @Override
    public Mono<CollectResult> collect(CollectContext context) {
        // context.getTask() 提供任务配置；失败时用 fail("原因") 而不是抛异常，
        // 否则调度器只会记一条「采集异常」，页面上看不出原因。
        CollectResult result = new CollectResult();
        result.setTask(context.getTask());
        result.addPoint(MetricPoint.builder().metricName("mq.queue.depth").value(12d).unit("条").build())
                .addPoint(MetricPoint.builder().metricName("mq.consumer.lag").value(0d).unit("条").build());
        return Mono.just(result);
    }
}
```

> `CollectResult#addPoint` / `#fail` 返回 `this`，可以链式串起来（`setTask` 是 Lombok 生成的 `void` setter，不能串）；`MetricPoint` 是 `@Builder`。`points` 为空或 `success=false` 时调度器会打印 `reason=`，表现为「成功但没取到指标」。

**登记**：在自有 jar 下新建 `META-INF/extensions/com.chua.starter.monitor.support.spi.MetricProvider`，追加一行实现类名（与内置实现同一文件格式）：

```text
com.example.monitor.MyMqHttpProvider
```

**生效**：`ProviderRegistry` 首次解析时用 `ServiceProvider.of(MetricProvider.class)` 收集全部实现并建索引（延迟构建，避免容器就绪前拿到半初始化实例）。之后新建任务时 `serviceType=MQ` + `provider=HTTP` 即可路由到该实现，无需改本模块代码。

## 六、与其它 starter 的关系

- **上游基础**：`spring-support-common-starter`、`spring-support-server-starter`、`spring-support-datasource-starter`、`spring-support-mybatis-starter`、`spring-support-cache-starter`、`spring-support-swagger-starter`。
- **内嵌的独立模块**：`spring-support-docker-starter`（`/ms/server/docker`）、`spring-support-control-starter`（`/control` + `/webrtc`）、`spring-support-tunnel-starter`（`/ms/tunnel`）、`spring-support-device-starter`（`/api/device/**`）、`spring-support-apm-*`（`/api/apm/v1`）、`spring-support-datasync-starter` 的**数据面**（刻意不引编排层，控制台改用 Spring 定时器驱动）。
- **`spring-support-ai-starter`**：注入 `AiChatFacadeService` / `ChatClient` 做指标、告警、Redis 的智能分析；缺 AI 配置时接口报「未配置」而不是启动失败。
- **`spring-support-datasearch-starter`**：`MonitorAutoConfiguration` 注册 `DatasearchConflictResolve`，把宿主里旧的 `/v2/holiday/get`、`/v2/ip/lookup` 控制器移走，避免同路径双映射导致 `Ambiguous handler methods mapped`。
- **`spring-support-discovery-starter`**：`optional` 依赖，只为「服务管理 → service-discovery 插件」提供 `/ms/monitor/discovery`；缺失时该接口降级为「未启用发现服务」。
- **api 层**：`spring-api-support-common-system-starter` 提供 pages/system 的 `/v2/**` 与 `/manage/*`；`spring-api-support-monitor-starter` 只取其 ACME 证书控制器（已排除 oauth 传递依赖）。

## 七、已知限制

1. **`LOCAL` 只在服务器本机可用**：走 metrics-starter / OSHI 直采，非 SERVER 类型不适用；且**macOS 跳过**。
2. **`TERMINAL` / `HTTP` / `PROMETHEUS` 依赖外部环境**：SSH 需要账号可达（超时见 `plugin.monitor.ssh.*`），HTTP 需要 exporter 暴露，Prometheus 需要可达的 Prometheus 服务；采集失败是 `CollectResult.fail(...)` 而非抛异常，因此表现为「成功但 `points=0`」，日志里会有 `reason=`。
3. **建表不会自动补列**：`V2.1__init_all_monitor.sql` 是 `CREATE TABLE IF NOT EXISTS`，对**已存在**的表不会加列，升级时要单独跑 `V4.0.0.x` 增量脚本；且框架会注册多个 `DataSource`，`spring.sql.init` 的自动装配会静默退避，所以本模块显式装配 `monitorSchemaInitializer` 才让脚本真正执行。
4. **时序存储取决于引擎**：默认 `engine=influxdb` 且 `plugin.monitor.influx.enabled` 默认 `false`，不接 InfluxDB 时请显式改成 `datasource`（写 MySQL 明细表）或 `none`（只写明细表、关闭时序双写）。
5. **缓存是进程内 + 多级，跨实例不自动失效**：`monitor` / `monitorTask` 等缓存由 cache-starter 提供，多实例下依赖共用 Redis；`monitor:metric:latest` 是短时快照，采集写入后不主动 evict，读数可能滞后一个采集周期。
6. **告警依赖通知通道**：内置只有日志与 Webhook 两种 `NotifySender`；短信厂商实现来自 `utils-support-alibaba-starter` / `-hunyuan-starter` / `-baidu-starter`，宿主不引则发短信会明确报「未安装短信通道」。
7. **抓包需要 tshark，实时抓包还需要 Npcap**：默认只补 tshark 便携版（不装驱动、不要求管理员）；`auto-install=false` 时完全不下载，缺包状态会在页面提示。
8. **Agent 部署需要可用的 agent.jar**：`jar-path` 为空且候选路径全部落空时会**明确失败**，不会静默上传一个不存在或过期的包。
9. **`/ms/tunnel` 默认不自启**：端口映射运行时 `auto-start=false`，由页面按需启停，避免监控进程一启动就暴露公网端口。
10. **与 datasearch 的路径冲突已处理但不代表可重复部署**：如果宿主两侧都自建了 `/v2/holiday/get`、`/v2/ip/lookup`，仍要靠 `DatasearchConflictResolve` 或部署取舍，不要指望框架替你选一个。
11. **独立启动与当库的差异**：`*-exec.jar` 才是可执行包；薄 jar 没有 `BOOT-INF/classes` 结构，不要直接 `java -jar`。本模块还自带 `application.yml` / `application-prod.yml`，宿主引入时注意不要被这些示例配置覆盖。

---

- 当前版本：`4.0.0.51`
- 父模块：`spring-support-parent-starter`
- 作者：CH

# spring-support-job-starter

自研分布式任务调度 starter：`@Job` 注解 + Spring `@Scheduled` 双扫描入口，按 `SCHEDULER` / `EXECUTOR` / `SINGLE` 三种角色装配；默认 `SINGLE` 单机开箱即用，集群模式经 utils 层 `SyncClient` / `SyncServer` 长链同步拆分调度中心与执行器。在 spring-support 体系中承担「分布式任务」能力域，是宿主应用引入即用的任务调度组件（启动时向 `ServiceInfoRegistry` 注册 `job` 标志）。

- 自动配置入口：`com.chua.starter.job.support.JobAutoConfiguration`（注册于 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`，无 `spring.factories`）
- 配置前缀：`plugin.job`（`JobProperties.PRE`），`enable` 缺省即启用
- 数据表：`sys_job` / `sys_job_log` / `sys_job_cluster`，由 `db/init/V2.0__init_all_job.sql` 建表（MySQL 8 / PostgreSQL 12 / Oracle 12c 自兼容）
- 基线：`4.0.0.51`，父级 `spring-support-parent-starter 4.0.0.51`，Spring Boot `4.1.2`

## 1. 架构图

```mermaid
flowchart TB
    HOST["宿主业务应用（Spring Boot 4.1.2）"]
    subgraph JOB["spring-support-job-starter"]
        API["接入层：@Job / @Scheduled 声明<br/>JobAnnotationScanner / ScheduledAnnotationScanner"]
        GOV["治理层：JobOperator / JobGovernanceService / JobManageController"]
        LOOP["调度层：JobLeaderLoop / LeaderElector / LeaderElectionResolver / JobRepository"]
        SYNC["传输层：JobSyncBus / SchedulerSyncBinder / ExecutorSyncBinder / GlueSyncService"]
        EXE["执行层：JobExecutor / JobHandlerFactory / ScriptGlueHandlers"]
        LOG["观测层：JobLogSink / JobLogRepository / AsyncLogWriter"]
        API --> GOV --> LOOP --> SYNC --> EXE --> LOG
    end
    NET["utils 层（compile 强依赖）<br/>utils-support-network-starter：SyncClient / SyncServer SPI<br/>utils-support-rsocket-starter：tcp/websocket/udp/http/rsocket 实现"]
    CS["spring-support-common-starter：ServiceInfoRegistry / 日志"]
    MY[("spring-support-mybatis-starter（optional）<br/>sys_job / sys_job_log / sys_job_cluster")]
    RE["spring-boot-starter-data-redis（optional）：Redis 选主"]
    WM["spring-webmvc（optional）：/v2/job/** 治理接口"]
    HOST --> JOB
    SYNC --> NET
    JOB --> CS
    JOB -.->|@ConditionalOnClass BaseMapper| MY
    JOB -.->|cluster.resolver=redis| RE
    JOB -.->|Servlet Web + manage.enable=true| WM
```

依赖方向：宿主 → 本模块 → common 层 / utils 层。`mybatis` / `redis` / `webmvc` 均为 optional，缺失时按条件装配回落（内存仓库 / 数据库选主 / 无 HTTP 出口），不影响单机使用。

## 2. 核心流程图

以默认 `SINGLE` 角色的「扫描 → 派发 → 执行 → 日志」数据流为例；集群模式下 `JobSyncBus` 的本地投递换成 `SchedulerSyncBinder` → `SyncServer` 长链粘性单播下发，执行日志再反向回传调度端落库。

```mermaid
sequenceDiagram
    autonumber
    participant Scan as JobAnnotationScanner
    participant Repo as JobRepository（内存/MyBatis）
    participant Loop as JobLeaderLoop
    participant Bus as JobSyncBus
    participant Exe as JobExecutor
    participant Sink as JobLogSink
    Note over Scan: 启动期 afterSingletonsInstantiated()
    Scan->>Repo: upsert(JobDefinition)
    Note over Loop: @PostConstruct 启动守护线程
    loop 每 scan-interval-ms（默认 1000ms）
        Loop->>Loop: leaderElector.isLeader() 校验
        Loop->>Repo: findDue(now, preReadMs, 200, group)
        Repo-->>Loop: 到期任务列表
        Loop->>Bus: publish("job-cmd", 派发负载)
        Bus->>Exe: 订阅 job-cmd 触发
        Exe->>Exe: JobHandlerFactory 取处理器并提交执行池
        Exe->>Bus: publish("job-log", JobLogEvent)
        Bus-->>Sink: LocalJobLogBinder 转写 JobLogEvent
    end
```

## 3. 能力清单

| 能力域 | 关键类 | 入口/触发方式 |
|---|---|---|
| 任务声明 | `annotation.Job`、`JobAnnotationScanner`、`JobHandlerFactory` | 无参方法上标注 `@Job("xxx")` |
| Spring 原生调度 | `ScheduledAnnotationScanner`、`TaskScheduler` | `@Scheduled(cron = "${...}")`，占位符由 `Environment` 解析 |
| 三模式装配 | `JobAutoConfiguration`、`JobProperties.Role` | `plugin.job.role = SCHEDULER / EXECUTOR / SINGLE`（缺省 `SINGLE`） |
| 扫表与派发 | `JobLeaderLoop`、`InMemoryJobRepository`、`MybatisJobRepository` | 每秒扫描（`cluster.scan-interval-ms`），预读窗 `cluster.pre-read-ms` |
| Leader 选举 | `LeaderElector`、`LeaderElectionResolver`、`ClusterElectionHeartbeat` | `cluster.resolver` / `cluster.election-namespace` / `cluster.lease-seconds` |
| 长链同步 | `JobSyncBus`、`SchedulerSyncBinder`、`ExecutorSyncBinder`、`GlueSyncService` | `sync.scheduler.*` / `sync.executor.*` |
| 任务执行 | `JobExecutor`、`JobSpec`、`JobHandler` | `pool.virtual=true` 虚拟线程，否则平台线程池 |
| 执行日志 | `JobLogSink`、`StdoutJobLogSink`、`MybatisJobLogSink`、`AsyncLogWriter`、`LocalJobLogBinder` | `log.*` 配置 |
| 脚本任务（Glue） | `GlueSyncService`、`GlueSynthesizer`、`ScriptGlueHandlers` | 台账 `glueType` / `glueSource`，正文按版本号同步 |
| 任务治理 | `JobOperator`、`DefaultJobOperator`、`JobGovernanceService`、`JobManageController` | 进程内注入 `JobOperator`（declare / updateSchedule / trigger / changeStatus / remove）；HTTP 出口需 `manage.enable=true` + Servlet Web + `/v2/job/**` |
| 失败告警 | `JobAlarmNotifier`、`CompositeJobAlarmNotifier` | 宿主注册通道 Bean，按台账 `alarmType` 路由 |
| 扫描缓存 | `JobScanCache`、`ScanCache`、`NoopScanCache` | `cache.enabled` / `cache.ttl-ms` |

## 4. Bean 能力矩阵

所有 Bean 均带 `@ConditionalOnMissingBean`，宿主声明同类型 Bean 即可覆盖。仓库 / 日志 / 选主三组按 classpath 是否存在 `com.baomidou.mybatisplus.core.mapper.BaseMapper` 二选一，MyBatis 分支额外标 `@Primary`（仅用于同类型二选一，自动配置在用户 Bean 之后评估，不影响宿主覆盖）。

| Bean 名 | 类型 | 装配条件（含默认值） | 可覆盖 | 典型用途 |
|---|---|---|---|---|
| `jobNodeId` | `String` | `@ConditionalOnMissingBean(name="jobNodeId")`；取 `plugin.job.node-id`，缺省按「运行时名称-随机后缀」生成 | 是 | 节点唯一标识 |
| `jobTaskScheduler` | `TaskScheduler`（`ThreadPoolTaskScheduler`） | `@ConditionalOnMissingBean`；池大小取 `pool.core-size`（默认 16） | 是 | 承载 `@Scheduled` |
| `jobScheduledAnnotationScanner` | `ScheduledAnnotationScanner` | `@ConditionalOnMissingBean` | 是 | 扫描 `@Scheduled` 并解析占位符 |
| `jobHandlerFactory` | `JobHandlerFactory` | `@ConditionalOnMissingBean` | 是 | `@Job` 处理器注册表 |
| `jobAnnotationScanner` | `JobAnnotationScanner` | `@ConditionalOnMissingBean` | 是 | 扫描 `@Job` 并登记任务 |
| `jobSyncBus` | `JobSyncBus` | `@ConditionalOnMissingBean` | 是 | 进程内事件总线 |
| `jobOperator` | `JobOperator`（`DefaultJobOperator`） | `@ConditionalOnMissingBean` | 是 | 治理唯一写入口 |
| `jobLeaderElector` | `LeaderElector` | `@ConditionalOnMissingBean` | 是 | 本地 Leader 状态 |
| `remoteHandlerRegistry` | `RemoteHandlerRegistry` | `@ConditionalOnMissingBean` | 是 | 执行器 handler 清单（单机/执行器恒空） |
| `jobSampleSeeder` | `JobAutoConfiguration.JobSampleSeeder`（静态内部类） | `@ConditionalOnMissingBean`；仅对 `InMemoryJobRepository` 补种 cron=`* * * * * *` 任务 | 是 | 内存仓库启动补种 |
| `inMemoryJobRepository` / `mybatisJobRepository` | `JobRepository` | `@ConditionalOnMissingBean` + 无/有 `BaseMapper`（MyBatis 版 `@Primary`，构造时落入 `cluster.group`） | 是 | 任务台账读写 |
| `emptyJobLogRepository` / `mybatisJobLogRepository` | `JobLogRepository` | 同上 | 是 | 执行日志查询侧 |
| `stdoutJobLogSink` / `mybatisJobLogSink` | `JobLogSink` | 同上；MyBatis 版按 `log.enabled`（默认 false）追加 `FileJobLogSink`；仅 DB 单 sink 时按 `log.async`（默认 true）决定是否包 `AsyncLogWriter`，DB+File 组合固定外包 `AsyncLogWriter` | 是 | 执行日志落地 |
| `inMemoryLeaderElectionResolver` / `mysqlLeaderElectionResolver` | `LeaderElectionResolver` | 同上；`cluster.resolver`（默认 `mysql`）非 `mysql` 时经 SPI 取实现，取不到告警回落数据库实现 | 是 | Leader 抢占/续约 |
| `jobGovernanceService` / `jobManageController` | `JobGovernanceService` / `JobManageController` | `@ConditionalOnMissingBean` + SERVLET Web + `DispatcherServlet` + `manage.enable=true` | 是 | 治理公共实现与 `/v2/job/**` HTTP 出口 |
| `jobSyncServer` | `SyncServer` | `role=SCHEDULER`；SPI 按协议候选（配置协议优先，兜底 tcp→websocket→udp→http→rsocket），`sync.in-memory=true` 抛 `UnsupportedOperationException` | 是 | 调度中心长链监听 |
| `jobSchedulerSyncBinder` | `SchedulerSyncBinder` | `role=SCHEDULER` | 是 | CMD 粘性单播 + 日志落库 |
| `jobGlueSyncService` | `GlueSyncService` | `role=SCHEDULER` | 是 | 脚本正文按版本推送 |
| `jobLeaderLoop` | `JobLeaderLoop` | `role=SCHEDULER`；装配前先 `tryAcquire` 选主，成功则 `markLeader` | 是 | 单秒扫描循环 |
| `jobClusterElectionHeartbeat` | `ClusterElectionHeartbeat` | `role=SCHEDULER`；按 `lease/3` 续约 | 是 | Leader 续约心跳 |
| `jobSyncClient` | `SyncClient` | `role=EXECUTOR`；`server-url` scheme 优先，兜底 rsocket→tcp→udp→websocket→http→socketio，无实现抛 `IllegalStateException` | 是 | 执行器长链客户端 |
| `jobExecutorSyncBinder` | `ExecutorSyncBinder` | `role=EXECUTOR` | 是 | 订阅 CMD / 上行日志 / 重连 |
| `jobExecutor` | `JobExecutor`（`destroyMethod="shutdown"`） | `role=EXECUTOR`；执行池由 `pool.virtual` 决定；聚合宿主全部 `JobAlarmNotifier` | 是 | 远端命令执行 |
| `jobSingleLeaderLoop` | `JobLeaderLoop` | `role=SINGLE`（`matchIfMissing=true`）；本机自认 Leader | 是 | 单机扫描循环 |
| `jobSingleClusterHeartbeat` | `ClusterElectionHeartbeat` | `role=SINGLE` | 是 | 单机续约心跳 |
| `jobSingleExecutor` | `JobExecutor`（`destroyMethod="shutdown"`） | `role=SINGLE`；消费本地 bus CMD | 是 | 本地命令执行 |
| `jobSingleLogBinder` | `LocalJobLogBinder` | `role=SINGLE`；把本地 `job-log` 事件接入 `JobLogSink`，否则单机日志进不了 `sys_job_log` | 是 | 单机日志桥 |

## 5. 完整配置

前缀 `plugin.job`（`JobProperties.PRE`），经 `@EnableConfigurationProperties(JobProperties.class)` 绑定；嵌套结构：`ClusterConfig` / `SyncConfig`（内含 `SchedulerConfig`、`ExecutorConfig`）/ `PoolConfig` / `LogConfig` / `ManageConfig` / `CacheConfig`。

| 配置路径 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `plugin.job.enable` | `boolean` | `true` | 模块总开关（`matchIfMissing=true`，引入即启用） |
| `plugin.job.role` | `Role` 枚举 | `SINGLE` | 进程角色：`SCHEDULER` / `EXECUTOR` / `SINGLE` |
| `plugin.job.node-id` | `String` | 无（自动推导） | 节点唯一标识，留空按「运行时名称-随机后缀」生成 |
| `plugin.job.cluster.group` | `String` | `default` | 作业归属分组，对应 `sys_job.sys_job_group`，Leader 只扫同组 |
| `plugin.job.cluster.resolver` | `String` | `mysql` | 选主实现键（大小写不敏感），非 `mysql` 走 SPI，取不到回落数据库实现 |
| `plugin.job.cluster.election-namespace` | `String` | 无（推导） | 选主租约命名空间，留空按 `spring.application.name` → `group` 推导 |
| `plugin.job.cluster.scan-interval-ms` | `long` | `1000` | 扫描间隔（下限 100ms，见 `JobLeaderLoop`） |
| `plugin.job.cluster.pre-read-ms` | `long` | `5000` | 预读时间窗 |
| `plugin.job.cluster.lease-seconds` | `int` | `15` | Leader 租约时长，心跳按 `lease/3` 续约 |
| `plugin.job.sync.in-memory` | `boolean` | `false` | 兼容字段；设 `true` 直接抛 `UnsupportedOperationException` 拒绝启动 |
| `plugin.job.sync.scheduler.listen-host` | `String` | `0.0.0.0` | 长链监听主机（`SINGLE` 下仅占位，不真实监听） |
| `plugin.job.sync.scheduler.listen-port` | `int` | `8081` | 长链监听端口（独立于 Web 端口） |
| `plugin.job.sync.scheduler.protocol` | `String` | `tcp` | 长链协议（tcp/websocket/udp/http/rsocket），须与执行器 `server-url` scheme 一致 |
| `plugin.job.sync.executor.server-url` | `String` | 无 | 调度中心长链地址，如 `tcp://10.0.0.11:8081` |
| `plugin.job.sync.executor.reconnect-base-ms` | `int` | `500` | 重连退避起点 |
| `plugin.job.sync.executor.reconnect-max-ms` | `int` | `30000` | 重连退避上限 |
| `plugin.job.pool.virtual` | `boolean` | `true` | `true` 用虚拟线程执行器（JDK 21+），`false` 回退平台线程池 |
| `plugin.job.pool.core-size` | `int` | `16` | 核心线程数（同时用于 `jobTaskScheduler` 池大小） |
| `plugin.job.pool.max-size` | `int` | `64` | 最大线程数（仅平台线程池） |
| `plugin.job.pool.queue-size` | `int` | `128` | 任务队列容量（仅平台线程池） |
| `plugin.job.log.enabled` | `boolean` | `false` | 是否追加文件日志 sink（仅 MyBatis 分支生效；无 MyBatis-Plus 时只有 stdout sink） |
| `plugin.job.log.base-path` | `String` | `./logs/job` | 文件日志根目录 |
| `plugin.job.log.async` | `boolean` | `true` | 是否异步写入 |
| `plugin.job.log.queue-size` | `int` | `4096` | 异步队列容量 |
| `plugin.job.log.cleanup-enabled` | `boolean` | `false` | 日志自动清理开关（本模块仅声明配置项，未装配定时清理任务；实际清理入口为 `POST /v2/job/log/cleanup`） |
| `plugin.job.log.cleanup-retention-days` | `int` | `30` | 日志保留天数（供上述清理入口使用） |
| `plugin.job.log.cleanup-cron` | `String` | `0 0 0 * * ?` | 清理 cron 表达式（声明项，同上未装配定时任务） |
| `plugin.job.manage.enable` | `boolean` | `false` | 是否暴露 `/v2/job/**` 治理接口 |
| `plugin.job.cache.enabled` | `boolean` | `false` | 是否启用扫描结果缓存 |
| `plugin.job.cache.ttl-ms` | `long` | `5000` | 缓存 TTL；开启后扫描相位最多滞后 TTL，要求严格相位精度请保持关闭 |

## 6. 使用方式

Maven 坐标：

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-job-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

最小启用示例（`SINGLE` 单机，零中间件，不配即用）——声明任务后，`JobAnnotationScanner` 登记处理器并 upsert `JobDefinition`，`JobLeaderLoop` 每秒扫描到期任务，经 `JobSyncBus` 交给 `JobExecutor` 执行，日志经 `LocalJobLogBinder` 落 `JobLogSink`：

```java
@Component
public class DemoJobHandler {

    @Job(value = "demo", desc = "演示任务", cron = "0/5 * * * * ?", group = "default")
    public void run() { System.out.println("hello demo"); }
}
```

零配置即可启用（`role` 缺省即 `SINGLE`），仅需保证 `@Job(group=...)` 与 `plugin.job.cluster.group`（默认 `default`）一致。

集群模式（两端 `cluster.group` 必须一致；执行器 `server-url` 的 scheme 须与调度端 `protocol` 一致）：

```yaml
# 调度中心
plugin:
  job:
    role: SCHEDULER
    cluster:
      group: payment
    sync:
      scheduler:
        listen-port: 8081
        protocol: tcp
---
# 执行器
plugin:
  job:
    role: EXECUTOR
    cluster:
      group: payment
    sync:
      executor:
        server-url: tcp://10.0.0.11:8081
```

开启 `plugin.job.manage.enable=true` 后暴露 `JobManageController`（`@RequestMapping("/v2/job")`，整组管理员权限）：`GET /v2/job/page | /list | /detail/{jobKey} | /summary | /executors | /nodes | /log/page | /log/content | /glue/engines`，`POST /v2/job/save | /trigger | /log/cleanup`，`PUT /v2/job/schedule | /status`，`DELETE /v2/job/{jobKey}`。

可选依赖说明（源自 pom.xml）：

| 依赖 | 作用域 | 缺失时的行为 |
|---|---|---|
| `spring-support-mybatis-starter` | optional | classpath 无 `BaseMapper` 时回落 `InMemoryJobRepository` / `StdoutJobLogSink` / `InMemoryLeaderElectionResolver`，任务与日志不落库 |
| `spring-boot-starter-data-redis` | optional | `RedisLeaderElectionResolver` 取不到，`cluster.resolver=redis` 时告警回落数据库实现 |
| `spring-webmvc` | optional | 非 Web 应用不装配 `/v2/job/**`，进程内 `JobOperator` 仍可用 |
| `utils-support-network-starter` / `utils-support-rsocket-starter` | compile（必需） | 无 `SyncServer` / `SyncClient` 协议实现，集群模式启动抛 `IllegalStateException` |
| `spring-support-common-starter` | compile（必需） | 服务信息注册与日志基础设施 |
| `swagger-annotations` / `lombok` | provided | 仅编译期 / 文档标记，运行期缺失不影响 |

> 注意：接 MyBatis-Plus 时，模块 Mapper（`com.chua.starter.job.support.store`）**不会自动扫描**，宿主主启动类须加 `@MapperScan(JobAutoConfiguration.MAPPER_PACKAGE)`，否则 `JobMapper` / `JobLogMapper` / `ClusterMapper` 缺失导致装配失败。

## 7. 使用场景

- **适用** · 单体应用进程内定时任务：默认 `SINGLE`，零中间件，`@Job` / `@Scheduled` 双入口开箱即用。
- **适用** · 多实例部署、任务不可重复执行：`SCHEDULER` + `EXECUTOR` 拆分，Leader 单点扫描 + 粘性单播，天然去重；执行器可弹性扩缩，注册/心跳/断线重连内置。
- **适用** · 需要运行期改 cron、启停、手动触发、查看执行器与日志：`JobOperator` 进程内入口，HTTP 出口按管理员权限显式开启。
- **适用** · 失败重试 / 执行超时 / 失败告警：台账字段 `fail_retry` / `retry_interval` / `execute_timeout` / `alarm_*` 随命令负载下发。
- **不适用 / 慎用** · 海量任务分片广播：当前派发是粘性单播去重模型，无 broadcast / shard 语义。
- **不适用 / 慎用** · 强实时（秒级以内）触发：扫描粒度秒级（`scan-interval-ms` 下限 100ms），且开 `cache.enabled` 后相位最多滞后 `cache.ttl-ms`。

与相邻模块的取舍：本模块长链同步直接使用 utils 层 `SyncClient` / `SyncServer`，不接 `spring-support-sync-starter` 的自动配置；`JobSyncBus` 是进程内事件总线，不是 `spring-support-queue-starter` 的消息队列；任务派发走长链同步，不走 `spring-support-rpc-starter` 的 RPC 代理。

## 8. 扩展点

SPI 注册位于 `META-INF/extensions/<接口全限定名>`（内容格式 `alias=实现类全限定名`）：

| SPI 接口 | 已注册 alias → 实现 | 说明 |
|---|---|---|
| `dispatch.JobRepository` | `in-memory` / `memory` → `InMemoryJobRepository` | MyBatis 版由装配侧显式注册，不占 SPI 键 |
| `log.JobLogSink` | `stdout` / `console` → `StdoutJobLogSink`；`file` → `FileJobLogSink` | `MybatisJobLogSink` 由装配侧组合 |
| `log.JobLogRepository` | `none` / `noop` → `NoopJobLogRepository` | MyBatis 版走显式装配 |
| `cache.JobScanCache` | `none` / `noop` / `off` → `NoopScanCache`；`memory` / `ttl` → `ScanCache` | 对应 `cache.enabled` 开关 |
| `glue.GlueSynthesizer` | `java` → `JavaGlueSynthesizer`；`default` / `plain` / `passthrough` → `PassThroughGlueSynthesizer` | 片段合成，键取不到回落透传 |
| `cluster.LeaderElectionResolver` | 文件登记 `mysql` / `in-memory` / `redis`，但**该文件实测不生效** | 实际生效通道是 `@Spi` 注解 + 接口同包扫描（如 `RedisLeaderElectionResolver`） |
| `SyncServer` / `SyncClient` | 由 utils 层提供（tcp / websocket / udp / http / rsocket / socketio） | 协议候选顺序见第 4 节 |

自定义实现注册方式：
1. **Spring Bean 覆盖（推荐）**：所有默认实现都带 `@ConditionalOnMissingBean`，声明同类型 Bean 即可替换默认实现，无需改注册文件，例如自定义 `JobLogSink`（日志进 Kafka 等）、`LeaderElectionResolver`（Etcd 选主）、`JobRepository`。
2. **SPI 文件注册**：实现接口并提供**无参构造**（SPI 带参实例化按实参运行时类型匹配构造器，JDK 动态代理的 Mapper 匹配不到声明类型，依赖注入型实现请无参构造 + 自取依赖），再在 `META-INF/extensions/<接口全限定名>` 追加一行 `alias=实现类全限定名`。注意 classpath 同时存在 MyBatis-Plus 时 `@Primary` 的 MyBatis 实现优先，此时请改用 Bean 覆盖。
3. **失败告警通道**：宿主注册一个或多个 `JobAlarmNotifier` Bean，`jobExecutor` / `jobSingleExecutor` 自动聚合为 `CompositeJobAlarmNotifier`，按台账 `alarmType`（EMAIL / WEBHOOK）路由投递。
4. **脚本引擎**：实现 `GlueSynthesizer`，`engine()` 返回新引擎名并登记 SPI；台账 `retryCallbackBean` / `exceptionCallbackBean` 可指向已注册的 `@Job` 处理器，失败重试前 / 异常时回调。

## 9. 常见问题

- **引入后启动失败，报 `JobMapper` / `JobLogMapper` / `ClusterMapper` 找不到？** 模块 Mapper 不会自动扫描。classpath 有 MyBatis-Plus 时，必须在主启动类加 `@MapperScan(JobAutoConfiguration.MAPPER_PACKAGE)`（即 `com.chua.starter.job.support.store`）。
- **`plugin.job.manage.enable=true` 但 `/v2/job/**` 404？** 需同时满足三个条件：Servlet Web 应用 + classpath 有 `spring-webmvc`（`DispatcherServlet`）+ 显式 `enable=true`。缺一则 `jobGovernanceService` / `jobManageController` 均不装配；进程内 `JobOperator` 不受影响。
- **任务登记成功了，却永远不被派发？** 检查两层「分组」概念：`@Job(group=...)` / `sys_job.sys_job_group` 必须等于 `plugin.job.cluster.group`（Leader 只扫同组任务）；选主租约键 `cluster.election-namespace` 是另一概念，同集群所有节点取值必须一致（通常留空按 `spring.application.name` 推导），否则会分裂成多个各自当 Leader 的集群。另注意 `@Job` 方法必须无参、`value` 全局唯一，触发方式优先级 cron > fixedDelay > fixedRate。
- **Spring Boot 4 兼容性？** 本模块仅通过 `META-INF/spring/...AutoConfiguration.imports` 注册自动配置（Boot 4 已不读 `spring.factories`）；生命周期钩子用 `jakarta.annotation.PostConstruct` / `PreDestroy`；`JobAutoConfiguration` 是 `@Configuration` + `@EnableConfigurationProperties` 组合，经 imports 加载在 Boot 4.1.2 下与 `@AutoConfiguration` 等效；MyBatis-Plus 装配只看 `BaseMapper` 是否在 classpath，宿主按 Boot 大版本选 `mybatis-plus-spring-boot3-starter` / `mybatis-plus-spring-boot4-starter`（父 POM 已托管版本）；`pool.virtual=true`（默认）依赖 JDK 21+ 虚拟线程，低版本或需限流时设 `false` 回退平台线程池。


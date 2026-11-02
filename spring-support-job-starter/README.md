# spring-support-job-starter

自研任务调度模块。 提供 **`@Job`** 注解与 Spring 原生 **`@Scheduled`** 注解扫描,
支持三模式运行: `SCHEDULER` (调度中心) / `EXECUTOR` (执行器) / `SINGLE` (单机)。

## 模块定位

```
+----------------------+        +----------------------+
|  spring-support-    |        |  本模块              |
|  scheduler-starter   |  <---> |  (executor / single |
|  (调度中心 admin)   |  长链  |   / scheduler client)|
+----------------------+        +----------------------+
                                              |
                                              v
                                  @Job("xxx") handler 池
```

- 本模块**不是** XXL-Job 兼容层。 设计为自研, 接口名/类名/配置前缀与 XXL-Job **完全解耦**。
- 同步通道复用仓库公共 `SyncClient / SyncServer` (`utils-support-common-starter`),
  本模块不自带网络基建。

## 依赖

```xml
<!-- 必选: 公共 + cron 解析 -->
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-common-starter</artifactId>
</dependency>
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>utils-support-common-starter</artifactId>
</dependency>

<!-- 可选: MyBatis-Plus, 引入后启用 MybatisJobRepository / MybatisJobLogSink -->
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-mybatis-starter</artifactId>
    <optional>true</optional>
</dependency>
```

接入 MyBatis-Plus 后, 主启动类上需:
```java
@MapperScan("com.chua.starter.job.support.store")
```

## 三模式

| Role | 谁启动 LeaderLoop | 谁消费 cmd | 谁写日志 |
|------|----------------|------------|----------|
| `SCHEDULER` | 是 (单秒扫描任务仓库) | 不消费, 推给 executor | server 端 `JobLogSink` |
| `EXECUTOR`  | 否 | 是 (本地执行器) | 上行 TASK_LOG, server 写 |
| `SINGLE`    | 是 (单秒扫描) | 是 (本地执行) | 本地 `JobLogSink` |

## 完整配置示例

```yaml
plugin:
  job:
    enable: true
    role: SCHEDULER          # SCHEDULER / EXECUTOR / SINGLE
    node-id: ""

    cluster:
      group: payment
      scan-interval-ms: 1000
      pre-read-ms: 5000
      lease-seconds: 15

    sync:
      scheduler:              # 仅 SCHEDULER / SINGLE 角色生效
        listen-host: 0.0.0.0
        listen-port: 8081
      executor:               # 仅 EXECUTOR 角色生效
        server-url: ws://127.0.0.1:8081
        reconnect-base-ms: 500
        reconnect-max-ms: 30000

    pool:
      core-size: 8
      max-size: 32
      queue-size: 1000

    cache:
      enabled: false          # 默认关闭：每拍真实查库（相位 + 候选行 = 2 次查询）
      ttl-ms: 5000            # 开启后扫描结果与相位在 TTL 内跨拍次复用（每拍压到 0~1 次查询）
```

### 扫描结果缓存（`plugin.job.cache.*`）

`JobLeaderLoop` 每拍调用一次 `repository.findDue(...)`，实路是**两次查库**：
`selectDbNow()` 取数据库时钟（多节点触发相位的来源），再 `selectList(...)` 扫候选行。

`cache.enabled=true` 时，这两批数据按查询条件（分组 + 预读窗 + 扫描上限）缓存 `ttl-ms`，
拍次并入下一次时可直接复用，查询量降到 **0~1 次**（只有存在到期行时才补一次主键批读）。

代价与约束：

- 相位（数据库时钟）在 TTL 内不更新，所以扫描窗上界 `now + preReadMs` 最多滞后 `ttl-ms`；
  临近到点的任务最多顺延 `ttl-ms` 才被派发，`misfire` 判定也随之后移一拍。
- **到期行必须先主键批读再写回**：候选行来自上一拍，若别的节点已推进 `last_time/next_time`，
  拿旧行下乐观条件会永远匹配 0 行、任务在窗口内静默不派发。批读同时带上
  `trigger_status=1 AND is_deleted=0`，避免缓存绕过启停与软删除。
- 所有写侧出口（保存 / 改调度 / 启停 / 删除 / 注销 / 同步 `next_time` / `refresh`）都失效缓存，
  状态变更一定在下一拍用新相位重新扫描。
- 需要严格相位精度时保持关闭（默认），行为与历史完全一致。


## 使用示例

```java
@Component
public class DemoJobHandler {

    @Job("demo")
    public void run() {
        System.out.println("hello demo");
    }
}
```

模块启动后:
- `SCHEDULER` 角色: 每秒扫表, 发现 "demo" 任务, 通过 `SyncServer.publish("job-cmd", "demo")` 推给 Executor
- 多 EXECUTOR 场景: 采用 sticky 单播 `SyncServer.send(clientId, "job-cmd", payload)` 轮询取一个 client 派发, 保证同一任务只在一个执行器上运行 (HA 去重)
- `EXECUTOR` 角色: 收到 "job-cmd"="demo" 后派发到线程池, 反射执行 `run()`, 结果 `send("job-log", logEvent)`
- `SINGLE` 角色: leaderLoop 直接 → bus → executor, 全在进程内

> 注意: `SyncServer.send(...)` 的形参顺序固定为 `(clientId, topic, message)`, 与 `publish(topic, message)` 不同,
> sticky 单播若传成 `(topic, clientId, message)` 会把 clientId 与 topic 互换导致派发全部落空。

## 模块结构

```
spring-support-job-starter/
├─ pom.xml
├─ 架构图.md
└─ src/main/
   ├─ java/com/chua/starter/job/support/
   │  ├─ annotation/Job.java
   │  ├─ handler/
   │  │  ├─ JobHandler.java
   │  │  ├─ BeanJobHandler.java
   │  │  ├─ JobHandlerFactory.java
   │  │  └─ JobAnnotationScanner.java
   │  ├─ schedule/ScheduledAnnotationScanner.java
   │  ├─ cluster/
   │  │  ├─ LeaderElector.java
   │  │  ├─ LeaderElectionResolver.java
   │  │  └─ ClusterLeaderState.java
   │  ├─ dispatch/
   │  │  ├─ JobDefinition.java
   │  │  ├─ JobRepository.java
   │  │  └─ InMemoryJobRepository.java
   │  ├─ loop/JobLeaderLoop.java
   │  ├─ sync/
   │  │  ├─ JobSyncTopics.java
   │  │  ├─ JobSyncBus.java
   │  │  ├─ SchedulerSyncBinder.java
   │  │  └─ ExecutorSyncBinder.java
   │  ├─ log/
   │  │  ├─ JobLogEvent.java
   │  │  ├─ JobLogSink.java
   │  │  └─ StdoutJobLogSink.java
   │  ├─ execute/JobExecutor.java
   │  ├─ JobProperties.java
   │  └─ JobAutoConfiguration.java
   └─ resources/META-INF/spring/
      └─ org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

## 扩展点

本模块的可替换组件统一走仓库的 SPI 机制（`utils-support-common-starter`）：

- 接口 + 实现类上标 `@Spi({"key1", "key2"})`，兜底实现另标 `@SpiDefault`；
- 注册文件放在 `src/main/resources/META-INF/extensions/<接口全限定名>`，内容为 `key=实现类全限定名`；
- 取实现用 `ServiceProvider.of(接口.class)`，按 key 取不到时回落 `@SpiDefault`。

| 扩展点 | 已注册的键 | 实现 | 默认（`@SpiDefault`） |
|--------|-----------|------|----------------------|
| `cache.JobScanCache` | `memory` / `ttl`、`none` / `noop` / `off` | `ScanCache`（按 TTL 缓存）、`NoopScanCache`（不缓存） | `NoopScanCache` |
| `dispatch.JobRepository` | `in-memory` / `memory` | `InMemoryJobRepository` | 无 |
| `log.JobLogSink` | `stdout` / `console`、`file` | `StdoutJobLogSink`、`FileJobLogSink` | `StdoutJobLogSink` |
| `log.JobLogRepository` | `none` / `noop` | `NoopJobLogRepository`（空页 / 0 条 / 0 删除） | `NoopJobLogRepository` |
| `glue.GlueSynthesizer` | `java`、`default` / `plain` / `passthrough` | `JavaGlueSynthesizer`、`PassThroughGlueSynthesizer` | `PassThroughGlueSynthesizer` |
| `cluster.LeaderElectionResolver` | `in-memory`、`mysql`、`redis` | `InMemoryLeaderElectionResolver`、`MysqlLeaderElectionResolver`、`RedisLeaderElectionResolver` | 无 |

自定义实现只需实现接口、加 `@Spi("你的键")`，并在自己的
`META-INF/extensions/<接口全限定名>` 里追加一行；`SyncClient / SyncServer` 直接复用
`utils-support-common-starter` 的协议实现。

几条约定：

- **键取到了但实例化失败会静默返回 null**，上层再兜底 `new`。所以注册文件里的类必须有可用的
  构造器：无参，或参数类型与取值方式匹配（`String` / 包装类型等具体类型；MyBatis Mapper 这类
  JDK 动态代理匹配不到接口构造器）。`SpiRegistrationProbe` 专门验证这一点。
- **依赖容器注入的实现不登记**：`MybatisJobRepository` / `MybatisJobLogSink` /
  `MybatisJobLogRepository` 需要 Mapper，`AsyncLogWriter` 需要 sink 列表，都由
  `JobAutoConfiguration` 装配，不进 SPI 注册表。
- **Spring 装配尚未改走 SPI**：`JobAutoConfiguration` 里仍是直接 `new` + `@ConditionalOnMissingBean`，
  本轮的改动只是把可替换点抽成 SPI 面。因此 `plugin.job.cache.enabled=true` 目前只在
  直接使用 `JobScanCaches` 的路径上生效（`JobAutoConfiguration` 构造 `MybatisJobRepository`
  时还没接入），接入时改一处装配即可。

### 1. 真实选主 (多 Scheduler 节点)

默认实现 `InMemoryLeaderElectionResolver` 仅在单机或调试场景生效;
classpath 含 MyBatis-Plus 时自动切换为 `MysqlLeaderElectionResolver`,
基于 `sys_job_cluster` 表 + `UPDATE ... WHERE leader_node IS NULL OR lease_until_ms < now`
行锁抢占,租约默认 15s,心跳按 `lease / 3` 续约。

- `tryAcquire(group, nodeId, leaseSeconds)`: 抢占 Leader,返回 boolean
- `release(group, nodeId)`: 主动释放
- `renew(group, nodeId, leaseSeconds)`: 续约(由 `ClusterElectionHeartbeat` 自动调用)

扩展第三方选主(etcd lease / ZooKeeper):实现
`com.chua.starter.job.support.cluster.LeaderElectionResolver` 接口并加 `@Spi`,
或直接注册为 Spring `@Primary` 让容器接走。

### 2. 真实持久化任务仓库

实现 `JobRepository` 接口替换默认 `InMemoryJobRepository`,
从 MySQL / Oracle / PG 读取 `sys_job` 表。MyBatis-Plus 版本可直接作为依赖引入,
本模块自带 `MybatisJobRepository`。

### 3. 真实日志落库

实现 `JobLogSink` 接口替换默认 `StdoutJobLogSink`,
从 `JOB_LOG_EVENT` 写入 `sys_job_log` 表。MyBatis-Plus 版本自带 `MybatisJobLogSink`;
纯文件落库用已注册的 `file` 键（构造参数 `basePath`）。

### 4. 真实同步 SPI

`SyncClient / SyncServer` 均为仓库 SPI,
WebSocket / HTTP / Netty 实现都在 `utils-support-common-starter` SPI 列表中选择。

## 已知未实现 (后续迭代)

- `MyBatis-Plus` 版 `JobRepository` (默认内存)
- `MyBatis-Plus` 版 `JobLogSink` (默认 stdout)
- `MySQL` 行锁版 `LeaderElectionResolver` (默认 CAS)
- `web` 化任务 CRUD 接口 (依赖 spring-api-support-scheduler-starter)

@author CH
@since 4.0.0.42

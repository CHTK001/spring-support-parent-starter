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
```

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

### 1. 真实选主 (多 Scheduler 节点)

默认实现 `InMemoryLeaderElectionResolver` 仅在单机或调试场景生效;
classpath 含 MyBatis-Plus 时自动切换为 `MysqlLeaderElectionResolver`,
基于 `sys_job_cluster` 表 + `UPDATE ... WHERE leader_node IS NULL OR lease_until_ms < now`
行锁抢占,租约默认 15s,心跳按 `lease / 3` 续约。

- `tryAcquire(group, nodeId, leaseSeconds)`: 抢占 Leader,返回 boolean
- `release(group, nodeId)`: 主动释放
- `renew(group, nodeId, leaseSeconds)`: 续约(由 `ClusterElectionHeartbeat` 自动调用)

扩展第三方选主(Redis SETNX / etcd lease / ZooKeeper):实现
`com.chua.starter.job.support.cluster.LeaderElectionResolver` 接口并注册为 Spring `@Primary`,
本模块自动让 Spring 容器接走。

### 2. 真实持久化任务仓库

实现 `JobRepository` 接口替换默认 `InMemoryJobRepository`,
从 MySQL / Oracle / PG 读取 `sys_job` 表。MyBatis-Plus 版本可直接作为依赖引入,
本模块自带 `MybatisJobRepository`。

### 3. 真实日志落库

实现 `JobLogSink` 接口替换默认 `StdoutJobLogSink`,
从 `JOB_LOG_EVENT` 写入 `sys_job_log` 表。MyBatis-Plus 版本自带 `MybatisJobLogSink`。

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

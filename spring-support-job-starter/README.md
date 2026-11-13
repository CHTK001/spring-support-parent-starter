# spring-support-job-starter

自研任务调度模块：`@Job` 与 Spring `@Scheduled` 双扫描入口，按 `SCHEDULER` / `EXECUTOR` / `SINGLE` 三种角色装配，长链同步复用仓库公共 `SyncClient` / `SyncServer`。

## 一、模块能力

| 能力域 | 关键类 | 入口 |
|---|---|---|
| 任务声明 | `@Job`、`JobAnnotationScanner`、`JobHandlerFactory` | 方法上标注 `@Job("xxx")` |
| Spring 原生调度 | `ScheduledAnnotationScanner`、`TaskScheduler` | `@Scheduled(cron = "${...}")` |
| 三模式装配 | `JobAutoConfiguration`、`JobProperties.Role` | `plugin.job.role` |
| 扫表与派发 | `JobLeaderLoop`、`JobRepository`、`InMemoryJobRepository` | 每秒扫描（`cluster.scan-interval-ms`） |
| Leader 选举 | `LeaderElectionResolver`、`LeaderElector`、`ClusterElectionHeartbeat` | `cluster.resolver` / `cluster.election-namespace` |
| 执行器 | `JobExecutor`、`JobSpec`、`Executors.newVirtualThreadPerTaskExecutor()` | `pool.*` |
| 长链同步 | `JobSyncBus`、`SchedulerSyncBinder`、`ExecutorSyncBinder`、`JobSyncTopics` | `sync.scheduler.*` / `sync.executor.*` |
| 执行日志 | `JobLogSink`、`StdoutJobLogSink`、`FileJobLogSink`、`AsyncLogWriter` | `log.*` |
| 脚本任务（Glue） | `GlueSyncService`、`GlueSynthesizer`、`JavaGlueSynthesizer` | 台账 `glueType` / `glueSource` |
| 任务治理（进程内） | `JobOperator`、`DefaultJobOperator` | 业务代码注入 |
| 任务治理（HTTP） | `JobManageController`、`JobGovernanceService` | `plugin.job.manage.enable=true` + `/v2/job/**` |
| 告警 | `JobAlarmNotifier`、`CompositeJobAlarmNotifier` | 宿主注册的通道 Bean |
| 查询缓存 | `JobScanCache`、`NoopScanCache`、`ScanCache` | `plugin.job.cache.*` |
| 服务信息 | `JobAutoConfiguration#describeOnStart` | 服务信息注册表 |

### 三模式分工

| Role | 起 `JobLeaderLoop` | 消费 cmd | 日志落地 |
|---|---|---|---|
| `SCHEDULER` | 是（每秒扫表） | 否，推给执行器 | 服务端 `JobLogSink` |
| `EXECUTOR` | 否 | 是（本地线程池） | 上行 `TASK_LOG`，由服务端落库 |
| `SINGLE`（默认） | 是 | 是（同进程） | `LocalJobLogBinder` → 本地 `JobLogSink` |

`SINGLE` 是 `@ConditionalOnProperty(matchIfMissing = true)` 的默认分支，不配置 `role` 即单机自闭环。

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-job-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

| 依赖 | 作用域 | 说明 |
|---|---|---|
| `spring-boot-starter` | compile | Spring Boot 基础 |
| `spring-support-common-starter` | compile | 服务信息、日志 |
| `utils-support-rsocket-starter`、`utils-support-network-starter` | compile | 长链同步的协议实现来源 |
| `spring-boot-starter-data-redis` | optional | 引入后才可能用 Redis 选主，缺失时回落数据库实现 |
| `spring-support-mybatis-starter` | optional | 引入并存在 `BaseMapper` 时启用 MyBatis 版仓库/日志/选主 |
| `spring-webmvc` | optional | `/v2/job/**` 治理接口 |
| `swagger-annotations` | provided | 接口文档标记 |
| `lombok` | provided | 编译期 |
| `spring-support-datasource-starter`、`mysql-connector-j 9.7.0`、`jakarta.servlet-api` | test | 仅测试链路 |

接入 MyBatis-Plus 后，Mapper 接口仍需宿主显式扫描：

```java
@MapperScan(JobAutoConfiguration.MAPPER_PACKAGE)   // com.chua.starter.job.support.store
```

## 三、配置项

前缀：`plugin.job`（`JobProperties.PRE`），未配置时 `enable` 默认为 `true`。

| key | 默认值 | 说明 |
|---|---|---|
| `enable` | `true` | 引入即启用（`matchIfMissing = true`） |
| `role` | `SINGLE` | `SCHEDULER` / `EXECUTOR` / `SINGLE` |
| `node-id` | 自动推导 | 节点唯一标识，缺省按「运行时名称-随机后缀」生成 |
| `cluster.group` | `default` | 作业归属分组，对应 `sys_job.sys_job_group` |
| `cluster.resolver` | `mysql` | 选主实现键，大小写不敏感 |
| `cluster.election-namespace` | 空 | 选主租约命名空间，为空按 `spring.application.name` → `group` 推导 |
| `cluster.scan-interval-ms` | `1000` | 扫描间隔 |
| `cluster.pre-read-ms` | `5000` | 预读时间窗 |
| `cluster.lease-seconds` | `15` | Leader 租约时长，心跳按 `lease/3` 续约 |
| `sync.in-memory` | `false` | 兼容字段；设为 `true` 会直接抛 `UnsupportedOperationException` |
| `sync.scheduler.listen-host` | `0.0.0.0` | 仅 `SCHEDULER`/`SINGLE` 生效 |
| `sync.scheduler.listen-port` | `8081` | 长链监听端口 |
| `sync.scheduler.protocol` | `tcp` | 长链协议，须与执行器 URL 的 scheme 一致 |
| `sync.executor.server-url` | 未设置 | 调度中心长链地址，如 `tcp://10.0.0.11:8081` |
| `sync.executor.reconnect-base-ms` / `reconnect-max-ms` | `500` / `30000` | 重连退避区间 |
| `pool.virtual` | `true` | `true` 用虚拟线程执行器；`false` 回退平台线程池 |
| `pool.core-size` / `max-size` / `queue-size` | `16` / `64` / `128` | 平台线程池参数（`virtual=false` 时生效） |
| `log.enabled` | `false` | 是否启用文件日志 sink |
| `log.base-path` | `./logs/job` | 文件日志根目录 |
| `log.async` / `log.queue-size` | `true` / `4096` | 异步写入与队列容量 |
| `log.cleanup-enabled` / `cleanup-retention-days` / `cleanup-cron` | `false` / `30` / `0 0 0 * * ?` | 日志清理 |
| `manage.enable` | `false` | 是否暴露 `/v2/job/**` |
| `cache.enabled` / `cache.ttl-ms` | `false` / `5000` | 扫描结果缓存与 TTL |

完整示例：

```yaml
plugin:
  job:
    enable: true
    role: SCHEDULER
    node-id: ""
    cluster:
      group: payment
      scan-interval-ms: 1000
      pre-read-ms: 5000
      lease-seconds: 15
    sync:
      scheduler:
        listen-host: 0.0.0.0
        listen-port: 8081
      executor:
        server-url: tcp://127.0.0.1:8081
    pool:
      virtual: true
      core-size: 16
      max-size: 64
      queue-size: 128
    manage:
      enable: false
```

## 四、快速开始

1. 引入依赖；单机体验无需任何额外中间件（默认 `role=SINGLE`）。
2. 写一个带 `@Job` 的无参方法：

   ```java
   @Component
   public class DemoJobHandler {
       @Job("demo")
       public void run() {
           System.out.println("hello demo");
       }
   }
   ```

3. 启动应用：`SINGLE` 下启动期 `JobSampleSeeder` 会把已注册处理器写成内存任务（cron 默认 `* * * * * *`），随后每秒扫描执行；集群模式按角色拆分 `SCHEDULER` 与 `EXECUTOR` 并配置 `sync.*`。

## 五、运行示例

### 5.1 调用示例

本模块无链式 API：`JobSpec` 的 setter 均为 `void`，`JobOperator` 的方法返回 `boolean` / 快照对象，没有 `Builder` 或 `return this` 风格入口，按步骤书写。

```java
// 1) 声明任务（注解式）
@Component
public class ReportJobHandler {

    @Job(value = "report-daily", desc = "日报汇总", cron = "0 0 2 * * ?", group = "report")
    public void run() {
        // 业务逻辑
    }
}

// 2) 运行期治理（进程内）
@Service
public class ReportJobAdmin {

    private final JobOperator jobOperator;   // 自动配置提供 DefaultJobOperator

    public ReportJobAdmin(JobOperator jobOperator) {
        this.jobOperator = jobOperator;
    }

    public void reschedule() {
        JobSpec spec = new JobSpec();
        spec.setJobKey("report-daily");
        spec.setJobName("日报汇总");
        spec.setScheduleType(JobSpec.TYPE_CRON);
        spec.setScheduleTime("0 30 2 * * ?");
        spec.setEnabled(Boolean.TRUE);
        jobOperator.declare(spec);                                  // 登记/更新
        jobOperator.updateSchedule("report-daily", JobSpec.TYPE_CRON, "0 0 3 * * ?");
        jobOperator.trigger("report-daily");                        // 立即执行一次
        jobOperator.changeStatus("report-daily", false);            // 停用
        JobRuntime runtime = jobOperator.runtime("report-daily");
    }
}

// 3) 开启治理接口后可用 HTTP 等价操作
// GET  /v2/job/page | /v2/job/list | /v2/job/detail/{jobKey} | /v2/job/summary
// POST /v2/job/save | /v2/job/trigger | /v2/job/log/cleanup
// PUT  /v2/job/schedule | /v2/job/status
// GET  /v2/job/executors | /v2/job/nodes | /v2/job/log/page | /v2/job/glue/engines
// DEL  /v2/job/{jobKey}
```

集群派发注意：`SyncServer.send(...)` 的形参顺序固定为 `(clientId, topic, message)`，与 `publish(topic, message)` 不同；sticky 单播传错顺序会让派发全部落空。

### 5.2 SPI 扩展示例

本模块的 SPI 采用 `META-INF/extensions/<接口全限定名>` 注册，文件内容为 `alias=实现类全限定名`。已有注册项：

| SPI 接口 | 已注册 alias |
|---|---|
| `JobRepository` | `in-memory`、`memory` |
| `JobLogSink` | `stdout`、`console`、`file` |
| `JobLogRepository` | `none`、`noop` |
| `JobScanCache` | `none`、`noop`、`off`、`memory`、`ttl` |
| `GlueSynthesizer` | `java`、`default`、`plain`、`passthrough` |
| `LeaderElectionResolver` | `mysql`、`in-memory`、`redis`（该注册文件实测不生效，见第七节） |

以替换任务仓库为例：

```java
// 1) 实现：提供无参构造（SPI 实例化只支持无参与具体类型参数）
public class RedisJobRepository implements JobRepository {

    @Override
    public List<JobDefinition> findDue(long now, long preReadMs, int preReadCount) {
        // 从外部存储取到期任务
        return List.of();
    }
}

// 2) 登记：resources/META-INF/extensions/com.chua.starter.job.support.dispatch.JobRepository
//    redis=com.example.RedisJobRepository

// 3) 生效：由 ServiceProvider 按 alias 取实现。
//    注意本模块的 MyBatis 版仓库走 @ConditionalOnClass + @Primary 显式装配，
//    若同时存在 MyBatis-Plus，@Primary 的 MybatisJobRepository 优先。
```

也可直接覆盖 Spring Bean（所有默认实现都带 `@ConditionalOnMissingBean`），例如替换 `JobLogSink`、`LeaderElectionResolver`、`JobRepository` 时声明同名 Bean 即可，无需改注册文件。

## 六、与其它 starter 的关系

- `spring-support-common-starter`：服务信息与日志，本模块必需。
- `utils-support-rsocket-starter` / `utils-support-network-starter`：提供 `SyncServer` / `SyncClient` 的协议实现，`SCHEDULER` 与 `EXECUTOR` 角色依赖它们建链。
- `spring-support-mybatis-starter`（optional）：存在 `BaseMapper` 时自动切换 `MybatisJobRepository`、`MybatisJobLogRepository`、`MybatisJobLogSink`、`MysqlLeaderElectionResolver`，并注册本模块 Mapper。
- `spring-support-datasource-starter`：执行 `db/init/V2.0__init_all_job.sql` 建表（`sys_job`、`sys_job_log`、`sys_job_cluster`）。
- `spring-support-sync-starter`：提供连接与主题通道，本模块直接使用 utils 层 `SyncClient` / `SyncServer`，不依赖该 starter 的自动配置。
- `spring-support-queue-starter`：`JobSyncBus` 是进程内事件总线，与消息队列模块无关。
- `spring-support-rpc-starter`：无装配关系，任务派发不走 RPC 代理。

## 七、已知限制

- **旧文档的"未实现"清单已过时**：`MybatisJobRepository`、`MybatisJobLogSink`、`MysqlLeaderElectionResolver`、`RedisLeaderElectionResolver` 以及 `/v2/job/**` 治理接口均已实现并随条件装配。
- `LeaderElectionResolver` 的 `META-INF/extensions` 注册文件在实测中不生效（文件内注释已说明：`provider.getExtensions()` 只返回 `in-memory`）；非 `mysql` 的 resolver 需通过 `@Spi` 注解方式注册，取不到时一律回落数据库实现。
- `plugin.job.sync.in-memory=true` 会直接拒绝启动，模块不提供内存伪同步实现。
- `SCHEDULER` / `EXECUTOR` 角色必须能取到真实的 `SyncServer` / `SyncClient` SPI 实现，否则抛 `IllegalStateException`；协议候选兜底顺序里 `rsocket` 排最后（其服务端只做出站，选中会导致执行器注册表恒空）。
- `cluster.group`（作业归属）与 `cluster.election-namespace`（选主租约键）不是同一概念：`@Job(group=...)` 与 `cluster.group` 不一致时，任务能登记但永远不会被派发。
- `sync.scheduler.*` 在 `SINGLE` 下仅占位不监听；跨进程调度必须显式配置 `SCHEDULER` + `EXECUTOR`。
- `manage.enable=true` 还需 Servlet Web 环境与 `spring-webmvc`；`sys_job` 是全局资源，模块只负责暴露接口，**不内置鉴权**，必须由宿主挂管理员权限。
- `cache.enabled=true` 后扫描相位（含 misfire 判定）最多滞后 `cache.ttl-ms`；要求严格相位精度时保持关闭。
- 无 MyBatis-Plus 时 `log.enabled` 不产生文件日志：`FileJobLogSink` 只在 `mybatisJobLogSink` 分支里追加，`stdout` 兜底分支忽略日志配置。
- Mapper 接口不会自动扫描，接 MyBatis-Plus 后必须自行 `@MapperScan(JobAutoConfiguration.MAPPER_PACKAGE)`。
- `mysql-connector-j 9.7.0` 仅为测试依赖，生产驱动由宿主提供。

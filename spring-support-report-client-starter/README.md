# spring-support-report-client-starter

上报信息客户端 starter：通过 `spring-support-sync-starter` 提供的同步客户端与监控服务端建立长连接，
定时上报应用信息（`monitor/app/report`）、设备指标（`monitor/device/metrics`）、URL QPS 统计（`monitor/url/qps-report`），
并接收服务端下发的作业调度、文件操作、日志级别调整、MyBatis XML 刷新、节点控制（重启/关闭）、节点维护（备份/升级/还原）与日志订阅命令。
模块默认不启用，必须配置 `plugin.report.client.enable=true` 且同步客户端（`plugin.sync.client.enable=true`）就绪后才会工作。

## 一、模块能力

本模块为单 Maven 模块；自动装配由两个自动配置类构成（见 `META-INF/spring/...AutoConfiguration.imports`），
另通过 `META-INF/spring.factories` 注册一个 `EnvironmentPostProcessor`。

| 能力域 | 关键类 | 入口 |
|:-------|:-------|:-----|
| 自动装配 | `ReportClientConfiguration`（`@AutoConfigureAfter(SyncAutoConfiguration.class)`） | `plugin.sync.type=client` + `plugin.sync.client.enable=true` |
| 环境配置串联 | `ReportEnvironmentPostProcessor` | `META-INF/spring.factories`（`plugin.report.client.*` → `plugin.sync.*`） |
| 应用信息上报 | `AppRegisterReporter`（单例，固定延迟 3s） | 主题 `monitor/app/report`（间隔可配） |
| 设备指标上报 | `DeviceMetricsReporter`（单例，启动延迟 10s/20s，CPU 独立线程） | 主题 `monitor/device/metrics`、`monitor/device/info` |
| URL QPS 统计 | `UrlQpsReporter` + `UrlQpsInterceptor` + `UrlQpsConfiguration` | 主题 `monitor/url/qps-report`（需 `plugin.report.client.url-qps.enabled=true`） |
| 作业调度执行 | `JobDispatchHandler` → `DefaultJobExecute` → `JobThread` | 主题 `monitor/job/dispatch`、`monitor/job/cancel` |
| 作业结果上报 | `JobReporter` | 主题 `monitor/job/result` |
| @Job 注解扫描 | `JobAnnotationScanner`（`BeanPostProcessor`） | 注册 `JobHandlerFactory`（`spring-support-job-starter` 的 `@Job` 注解） |
| 文件操作 | `FileHandler` | 主题 `monitor/file/request` |
| 日志级别调整 | `LoggingConfigHandler`（Logback） | 主题 `monitor/control/log-level` |
| 日志推送 | `LogPushHandler`（Root Appender 订阅/取消） | 主题 `monitor/log/subscribe`、`monitor/log/unsubscribe`、`monitor/log/push` |
| MyBatis XML 刷新 | `MyBatisConfigHandler` | 主题 `monitor/control/mybatis`（自动装配仅在 `SqlSessionFactory` 存在时注册） |
| MyBatis 信息端点 | `MyBatisEndpoint` | `/actuator/mybatis`（`@Endpoint(id="mybatis")`） |
| API 功能开关 | `ApiFeatureHandler`（依赖 `ApiFeatureManager` Bean） | 主题 `monitor/control/api-feature` |
| 节点控制 | `NodeControlHandler`（重启/关闭，含守护脚本保全） | 主题 `monitor/control/node-restart`、`monitor/control/node-shutdown` |
| 节点维护 | `NodeMaintenanceHandler`（备份/升级/还原） | 主题 `monitor/maintenance/backup`、`upgrade`、`restore` |
| Actuator 脱敏 | `ActuatorConfiguration`（`SanitizingFunction`） | 敏感键（password/secret/key/token 等）输出 `******` |
| 服务信息登记 | `reportClientServiceInfoContributor` | 控制台模块能力 |
| 系统信息采集 | `OshiSystemMonitor`（`com.chua.starter.oshi.support`） | 封装 `utils-support-oshi-starter` 的 OSHI 快照 |

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-report-client-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

关键依赖：

| 依赖 | 作用 |
|:-----|:-----|
| `spring-support-sync-starter` | 同步客户端 `SyncClient`（模块不工作则不会装配，见 `@ConditionalOnBean(SyncClient.class)`） |
| `spring-support-ssh-starter` | 依赖引入，模块源码未直接使用 SSH 能力（已知限制见「七」） |
| `spring-support-oauth-client-starter` | 依赖引入，模块源码未直接使用 OAuth 能力 |
| `spring-support-discovery-starter` | 依赖引入，供 sync 客户端按服务发现寻址（`SyncClientWrapper` 注入 `ServiceDiscovery`） |
| `spring-support-job-starter` | `@Job` 注解（`JobAnnotationScanner` 扫描）与任务定义 |
| `spring-support-common-starter` | `ApiFeatureManager`、`ServiceInfoContributor`、`Request/Response` 协议 |
| `utils-support-oshi-starter` | OSHI 系统信息采集 |
| `utils-support-groovy-starter` | GLUE 脚本（Groovy）任务支持（`GlueFactory`） |
| `utils-support-network-starter` | 网络信息采集辅助 |
| `utils-support-spring-starter` | Spring 工具能力 |
| `spring-support-swagger-starter` | 文档能力 |
| `commons-compress` | 节点维护的 tar.gz/tgz 升级包解压 |
| `hutool-core` | `DefaultJobLog` 的日期工具 |
| `jakarta.servlet-api` | `UrlQpsInterceptor`（provided 作用域） |

版本统一由父 POM（`spring-boot-starter-parent:4.1.0`，Java 25）管理，模块依赖引用父 POM 的 `4.0.0.51`。

## 三、配置项

配置前缀：**`plugin.report.client`**（`ReportProperties.PRE`）。模块默认关闭，总开关为 `enable`。

### 3.1 全局

来源：`ReportProperties`。

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.report.client.enable` | `false` | 总开关；`ReportEnvironmentPostProcessor` 读取该值决定是否把 `plugin.report.client.*` 串联为 `plugin.sync.*` |
| `plugin.report.client.protocol` | `rsocket-sync` | 与 sync 服务端的通信协议，透传给 `plugin.sync.client.protocol` |
| `plugin.report.client.host` | `localhost` | sync 服务端地址，透传给 `plugin.sync.client.server-host` |
| `plugin.report.client.port` | `29170` | sync 服务端端口，透传给 `plugin.sync.client.server-port` |
| `plugin.report.type` | 无 | 仅当显式配置时透传给 `plugin.sync.type`；未配置时 `ReportEnvironmentPostProcessor` 强制写 `client` |

### 3.2 应用信息上报（`plugin.report.client.app-report`）

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.report.client.app-report.enabled` | `true` | 是否启动 `AppRegisterReporter` 定时上报应用信息（名称/端口/上下文/JVM/OS/内存等） |
| `plugin.report.client.app-report.interval` | `30` | 应用信息上报间隔（秒），实际调度固定 **初始延迟 3 秒** |

### 3.3 设备指标上报（`plugin.report.client.metrics`）

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.report.client.metrics.enabled` | `true` | 是否启动设备指标上报 |
| `plugin.report.client.metrics.interval` | `30` | 指标上报间隔（秒；设备信息启动延迟 20s，指标 10s，CPU 采集 5s） |
| `plugin.report.client.metrics.types` | 空（表示全部） | 指标类型集合，可选 `CPU`、`MEMORY`、`DISK`、`NETWORK`、`LOAD`、`PROCESS`、`TEMPERATURE`（`MetricType`） |

### 3.4 客户端信息（`plugin.report.client.info`）

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.report.client.info.host` | 无（自动获取） | 多网卡场景指定本机 IP；同时透传给 `plugin.sync.client.ip-address` |

### 3.5 URL QPS（`plugin.report.client.url-qps`）

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.report.client.url-qps.enabled` | `true` | 是否启用 URL QPS 统计与上报（`UrlQpsConfiguration` 的拦截器装配条件是 `url-qps.enabled=true`，`matchIfMissing=false`；但 `ReportClientConfiguration` 启动 `UrlQpsReporter` 时读取的是 `isEnabled()` 默认 `true`） |
| `plugin.report.client.url-qps.interval` | `30` | 统计上报间隔（秒） |
| `plugin.report.client.url-qps.exclude-patterns` | `/actuator`、`/health`、`/favicon.ico` | 排除的 URL 前缀（拦截器会额外追加 `/**` 变体） |

### 3.6 节点维护（`plugin.report.maintenance`，由 `NodeMaintenanceHandler` 直接读 Environment）

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.report.maintenance.home-dir` | 无（基于 `ApplicationHome` 推导） | 备份 `backups/`、升级包 `upgrades/`、回滚 `rollbacks/` 目录的根路径 |
| `plugin.report.maintenance.current-artifact` | 无（基于 `ApplicationHome.getSource()`） | 当前运行产物（jar）路径，升级前备份与替换目标 |

### 3.7 同步模块配置（`plugin.sync`，由本模块自动串联）

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.sync.type` | `client`（本模块强制） | 同步程序类型；本模块要求为客户端角色 |
| `plugin.sync.client.enable` | `false` | `ReportEnvironmentPostProcessor` 在 `plugin.report.client.enable=true` 时自动写 `true` |
| `plugin.sync.client.server-host` / `server-port` | 透传 `report.client.host/port` | 服务端地址 |
| `plugin.sync.client.protocol` | 透传 `report.client.protocol` | 通信协议 |
| `plugin.sync.client.ip-address` | 透传 `report.client.info.host` | 客户端 IP |
| `plugin.sync.crypto-secret` / `crypto-version` | 无 / `2` | 传输层 AES-256-GCM 加密（未配置时明文，兼容旧版本密文） |

## 四、快速开始

**第一步：引入依赖**（见「二、依赖与引入」）。

**第二步：配置 yaml**（总开关 + 服务端地址；其余可省略）：

```yaml
spring:
  application:
    name: my-app                      # 应用信息上报与节点维护使用

plugin:
  report:
    client:
      enable: true                    # 总开关，默认关闭
      host: monitor-server            # sync 服务端地址
      port: 29170
      # protocol: rsocket-sync        # 默认 rsocket-sync
      url-qps:
        enabled: true                 # URL QPS 统计（需 Web 环境）
```

`ReportEnvironmentPostProcessor` 会自动补齐 `plugin.sync.client.enable=true`、`plugin.sync.type=client`、
`plugin.sync.client.server-host/port/protocol`，宿主无需重复配置 sync 客户端。

**第三步：启动验证**

应用启动后日志依次出现：

```
[ReportClient] 初始化
[ReportClient] 已向服务端注册
[ReportClient] 已注册日志配置处理器
[ReportClient] 已注册节点控制处理器（重启/关闭）
[ReportClient] 初始化完成 (AppRegister, Job, File, DeviceMetrics, MyBatis, UrlQps, NodeControl)
[AppReport] 启动应用信息上报，间隔: 30秒
[DeviceReporter] 启动定时上报，间隔: 30秒，上报指标: [...]
```

- 未配置总开关时：`ReportEnvironmentPostProcessor` 打印 `plugin.report.client.enable = false` 并跳过，
  `ReportClientConfiguration` 因缺少 `SyncClient` Bean 不装配（`@ConditionalOnBean(SyncClient.class)`）。
- 服务端在线后，可在监控服务端（`spring-support-monitor-starter`）节点列表看到本节点，
  并按 `monitor/app/report` 的 serviceUrl 访问本应用；MyBatis 环境可访问 `/actuator/mybatis` 查看 Mapper 与 SQL 统计。
- URL QPS 开启后访问任意业务 URL，随后在 `monitor/url/qps-report` 主题可见 `{url, method, count, statusCodes, ...}` 数据。

## 五、运行示例

### 5.1 调用示例

**1) @Job 注解任务（本地注册，供服务端下发执行）**

```java
@Component
public class MyJobs {

    @Job(value = "demoJobHandler", desc = "示例任务")
    public void demoJob() {
        // 参数可通过 JobContext.getXxlJobContext().getJobParam() 读取
        System.out.println("hello report-job");
    }
}
```

启动后 `JobAnnotationScanner` 日志：`[JobScanner] 注册JobHandler: demoJobHandler -> MyJobs.demoJob`。
服务端通过主题 `monitor/job/dispatch` 下发 `{jobId, executorHandler, glueType: "BEAN", ...}` 即可触发执行，
结果经 `JobReporter` 上报到 `monitor/job/result`。

**2) GLUE 脚本（在线编辑热更新）**

```java
// 服务端下发 glueType=GLUE(Java) / GLUE(Shell) / GLUE(Python) 等时
// DefaultJobExecute 会按 GlueTypeEnum 创建 GlueJobHandler / ScriptJobHandler
// Shell 示例命令（GlueTypeEnum 内置 cmd）：
//   GLUE(Shell)   -> bash script.sh
//   GLUE(Python)  -> python script.py
//   GLUE(PowerShell) -> powershell script.ps1
```

**3) URL QPS 统计（Web 环境）**

开启 `plugin.report.client.url-qps.enabled=true` 后自动注册 `UrlQpsInterceptor`，
`UrlQpsReporter#recordRequest` 按「HTTP 方法 + 路径」聚合（路径变量归一化为 `{id}`/`{uuid}`），
每 `interval` 秒向 `monitor/url/qps-report` 发布统计（含 count/successCount/failCount/avgDuration/statusCodes/history* 字段）。

**4) 手动下发端命令（监控服务端视角，主题即 MonitorTopics 常量）**

- 文件：`monitor/file/request`，operation 支持 `list/tree/info/read/write/delete/mkdir/rename/copy/move/exists/diskUsage`
- 日志级别：`monitor/control/log-level`
- MyBatis XML 刷新：`monitor/control/mybatis`（action=`refreshXml`/`refreshAllXml`）
- 功能开关：`monitor/control/api-feature`（action=`setEnabled`/`setBatch`/`reset`，需 `ApiFeatureManager` Bean）
- 节点重启/关闭：`monitor/control/node-restart`、`monitor/control/node-shutdown`（`delaySeconds` 默认 3，健康检查超时 120s）
- 备份/升级/还原：`monitor/maintenance/backup`（create/list/delete/download）、`monitor/maintenance/upgrade`（upload/list/execute/status/rollback）、
  `monitor/maintenance/restore`（preview/execute）

### 5.2 SPI 扩展示例

本模块**不提供自定义 SPI 扩展点**：`ReportClientConfiguration` 直接在 `init()` 中 `new` 各业务处理器
（`JobDispatchHandler`、`FileHandler`、`LoggingConfigHandler`、`MyBatisConfigHandler`、`NodeControlHandler`、
`ApiFeatureHandler`）并注册到 `SyncClient`，这些处理器虽标注 `@Spi("...")` 但仅作为命名标识，未被自行加载。

要扩展行为，只能替换以下 Bean：

- **`SyncClient`**（`spring-support-sync-starter` 装配）：本模块所有上报与接收全部经它完成，
  替换/包装 `SyncClient` Bean 可统一接管收发与加密；
- **`ApiFeatureManager`**（存在时开启 `ApiFeatureHandler`）：替换为自定义实现可改变功能开关执行语义；
- **`SqlSessionFactory`**（存在时注册 `MyBatisConfigHandler` 与 `/actuator/mybatis`）；
- **各 `SyncMessageHandler` 处理器本身不对外暴露 Bean**，无法按 bean 名替换；若要改变处理逻辑，
  应 fork 本模块或在 `ReportClientConfiguration` 逻辑外自行注册同主题处理器（主题注册为追加式，可与内置处理器并存）。

## 六、与其它 starter 的关系

| 模块 | 方向 | 说明 |
|:-----|:-----|:-----|
| `spring-support-sync-starter` | 本模块依赖 | 唯一的通信通道；`ReportClientConfiguration` 装配条件依赖其 `SyncClient` Bean，`ReportEnvironmentPostProcessor` 自动串联 `plugin.sync.*` |
| `spring-support-common-starter` | 本模块依赖 | `ApiFeatureManager`、`ServiceInfoContributor`、`Servlet*` 协议对象 |
| `spring-support-job-starter` | 本模块依赖 | `@Job` 注解扫描与任务定义；作业经本模块的 `JobDispatchHandler` 在 Web 节点内执行 |
| `spring-support-ssh-starter` | 本模块依赖（pom） | 源码未直接使用 SSH；若需远程执行/文件能力可经其自行扩展 |
| `spring-support-oauth-client-starter` | 本模块依赖（pom） | 源码未直接使用 OAuth；由宿主统一鉴权 |
| `spring-support-discovery-starter` | 本模块依赖 | 供 `SyncClientWrapper` 服务发现寻址（`server-host/port` 缺省时按 `discoveryName` 解析） |
| `utils-support-oshi-starter` | 本模块依赖 | 设备指标采集（经 `OshiSystemMonitor` 封装） |
| `utils-support-groovy-starter` | 本模块依赖 | GLUE(Java) 脚本任务的 Groovy 类加载 |
| `spring-support-monitor-starter` | 被依赖方/组合 | 监控服务端侧消费者：`monitor/app/report` 节点注册、`monitor/url/qps-report` 等主题的对接方 |
| `spring-support-socket-*` / `spring-support-queue-starter` | 无直接依赖 | 本模块不处理实时推送/消息队列，日志推送走 sync 主题 |

注意区分：本模块是**数据上报与远程控制客户端**（安装在每个被监控节点上，与 `spring-support-monitor-starter`
服务端配合），不要与 `spring-support-ai-training-starter`、`spring-support-ueba-starter` 等业务上报混淆。

## 七、已知限制

- **默认完全关闭**：`plugin.report.client.enable` 默认 `false`，且 `ReportEnvironmentPostProcessor` 只在
  `enable=true` 时写入 `plugin.sync.client.enable=true`；不配置则 `SyncClient` 不装配、整个模块不工作。
- **`url-qps.enabled` 双读取不一致**：`UrlQpsConfiguration` 的拦截器装配条件是 `enabled=true`（`matchIfMissing=false`，
  即默认**不挂拦截器**），而 `ReportClientConfiguration#init` 默认按 `isEnabled()==true` 启动 `UrlQpsReporter`
  （线程已起但无数据）；要真正采集 URL 统计，必须显式配置 `plugin.report.client.url-qps.enabled=true`。
- **Job 日志硬编码目录**：`JobFileAppender.logBasePath` 默认硬编码 `/data/applogs/job/jobhandler`；
  `initLogPath(...)` 提供了覆盖入口但代码中不存在调用点，无法通过配置项修改，Windows 等环境脚本任务日志会写入该固定路径（由 `makeLogFileName` 按日期建目录）。
- **`JobConfiguration` 未被自动化装配**：`threadPoolTaskScheduler`（池 200、前缀 `job`）位于 `JobConfiguration` 类，
  该类未出现在 `AutoConfiguration.imports` 中，需宿主自行 `@Import` 才会生效。
- **`JobFileAppender` 日志文件名用 `.log`**：`makeLogFileName` 拼的是 `logId + ".log"`，与 `ScriptJobHandler` 中
  `logFileName = jobId + ".log"` 一致，但脚本日志与任务日志混在同一套路径规则下，`logId` 与 `jobId` 不同时可能产生两份文件。
- **节点控制重启依赖外部进程管理**：`NodeControlHandler` 优先找
  `org.springframework.cloud.context.restart.RestartEndpoint`，不可用时用 `SpringApplication.exit(ctx, () -> 0)`
  + 守护脚本/退出码，需配合 systemd、Docker 等外部进程管理器才能真正拉起。
- **文件操作无路径白名单**：`FileHandler` 的 list/read/write/delete 直接操作任意绝对路径（不做根目录限制），
  授权边界依赖 `monitor/control/*` 主题的上游访问控制，请不要把同步服务端暴露到不可信网络。
- **升级包解压有目录穿越防护但无签名**：`NodeMaintenanceHandler#resolveArchiveTarget` 会校验解压目标落在
  applicationHome 内（`blocked ../`），但升级包内容无校验和/签名，被中间人替换的包会被直接写盘。
- **备份收集全量配置但跳过敏感键**：`collectEnvironmentProperties` 遍历全部 PropertySource 并跳过
  password/secret/credential/key/token 键；`executeRestore` 用 `System.setProperty` 还原，仅对运行期生效。
- **日志推送为 Root Appender**：`LogPushHandler` 把 `LOG_PUSH_APPENDER` 挂到 root logger，会拦截全部日志事件
  （按 `logLevelFilter` 默认 INFO 过滤并排除 `LogPushHandler` 自身成环），日志量大时对应用有性能与磁盘影响。
- **SSH/OAuth 依赖未使用**：pom 声明 `spring-support-ssh-starter`、`spring-support-oauth-client-starter`，
  模块源码无对应 import（经 grep 确认），属预留依赖；不要依赖其在本模块内自动生效。
- **`plugin.report.type` 仅在显式配置时透传**：`ReportEnvironmentPostProcessor` 优先读 `plugin.report.type`，
  否则读 `plugin.sync.type`，都没有才写死 `client`；若宿主定义了 `plugin.sync.server.enable=true`，可能同时起服务端。
- **设备指标 `PROCESS`/`LOAD`/`TEMPERATURE` 未采集**：`DeviceMetricsReporter` 仅实现 CPU（缓存采样）、MEMORY、DISK、
  NETWORK 三类快照与 `systemSnapshot`；`MetricType` 虽枚举了 `PROCESS`、`LOAD`、`TEMPERATURE`，但 `collectMetrics`
  没有对应采集分支，配置了也不会产生对应字段。
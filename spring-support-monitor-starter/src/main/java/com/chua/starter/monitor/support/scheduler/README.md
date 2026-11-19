# monitor-starter 定时任务索引

本模块全部 Spring 后台定时功能统一收录在本包（`com.chua.starter.monitor.support.scheduler`）。
查找顺序：先看本清单，再进对应类。调度由 `config.MonitorAutoConfiguration` 上的
`@EnableScheduling` 启用；与 SSE 连接/实例状态强耦合的心跳与推送任务留在业务类里，只在本清单登记。

## 专属任务类（本包内）

| 类#方法 | 业务 | 触发方式 | 周期 | 配置键 |
|---|---|---|---|---|
| `MonitorScheduler#tick` | 监控采集总调度：扫描启用任务，按任务自身 cron 判定是否到点，Reactor 背压（并发 50）执行采集与入库 | `@Scheduled(fixedRate=10000)` | 10s 一 tick；实际采集间隔由全局门限控制 | `monitor.collect.interval.ms`（默认 10000ms）；任务级 cron 存于 `ms_monitor_task` 行上 |
| `MonitorScheduler#aggregateHourly` | 指标小时聚合 | `@Scheduled(cron="0 0 * * * ?")` | 每小时整点 | —（代码常量） |
| `MonitorScheduler#cleanupOldData` | 指标数据保留期清理（滚动窗口；默认每小时跑，避免「保留期 + 最多 24h」的超存） | `@Scheduled(cron="${monitor.cleanup.cron:0 0 * * * ?}")` | 默认每小时，可整体改 cron | `monitor.cleanup.cron`（默认 `0 0 * * * ?`）；保留天数 `monitor.data.retention.days`（默认 30） |
| `AgentOfflineChecker#check` | 探针离线扫描：心跳超时的目标翻转离线并记 WARN（状态可查 `GET /ms/monitor/agent/status`） | `@Scheduled(fixedDelay=60000, initialDelay=90000)` | 60s（首延 90s） | —（代码常量） |
| `ApmOperLogScheduler#purgeExpired` | APM 操作日志（`ms_apm_oper_log`）保留清理，固定保留 90 天 | `@Scheduled(cron="0 50 4 * * ?")` | 每天 04:50 | —（retentionDays=90 代码常量，故意不用占位符，防早期 resolver 未就绪炸启动） |
| `ServerOperLogScheduler#purgeExpired` | 服务节点操作日志（`ms_server_oper_log`）保留清理，固定保留 90 天 | `@Scheduled(cron="0 45 4 * * ?")` | 每天 04:45 | —（retentionDays=90 代码常量，理由同上） |
| `TsharkRetentionCleanupScheduler#cleanup` | 抓包保留清理：过期会话连同报文 / pcap / SQLite / 日志一起删，会话目录超限也清 | `@Scheduled(fixedDelay=1800000, initialDelay=60000)`；`SchedulingConfigurer` 换单线程池，不占公共调度器 | 30min（首延 60s） | —（代码常量） |

## 内联 @Scheduled（留在业务类，未搬移）

| 位置 | 业务 | 触发参数 | 说明 |
|---|---|---|---|
| `alarm.AlarmEventStreamRegistry#heartbeat` | 告警 SSE 心跳保活（推注释帧，清死连接） | `@Scheduled(fixedRate=20000, initialDelay=20000)`（`HEARTBEAT_MS`） | 依赖实例内 `subscribers` 连接集，故留在原类 |
| `server.ServerEventStreamRegistry#heartbeat` | 服务管理 SSE 心跳保活 | `@Scheduled(fixedRate=20000, initialDelay=20000)`（`HEARTBEAT_MS`） | 与告警 SSE 同构，依赖实例内 `subscribers` |
| `server.ServerEventStreamService#detect` | 服务节点状态指纹比对，仅变化时广播 `changed` 一帧 | `@Scheduled(fixedDelay=5000, initialDelay=5000)`（`DETECT_INTERVAL_MS`） | 依赖实例内 `lastSignature` 指纹与订阅计数，故留在原类 |
| `tshark.TsharkPacketStreamRegistry#heartbeat` | 抓包数据包 SSE 心跳保活（按会话分组） | `@Scheduled(fixedRate=20000, initialDelay=20000)` | 依赖实例内按会话分组的 `subscribers` |
| `tshark.TsharkInstallStreamRegistry#heartbeat` | tshark 安装进度 SSE 心跳保活 | `@Scheduled(fixedRate=20000, initialDelay=20000)` | 依赖实例内 `subscribers` |
| `service.HostSummarySseService#pushTick` | 主机资源摘要（句柄数 / inode）每 30s 采集并经 `HostAssetsSseHub` 推送 | `@Scheduled(fixedDelay=30000)`（`PUSH_INTERVAL_MS`） | 依赖实例内 `subscriptions` 与专用 `pushExecutor`，故留在原类 |
| `service.impl.HostMetricHistoryServiceImpl#scheduledCleanup` | 主机指标历史时序数据保留清理 | `@Scheduled(cron="0 17 3 * * ?")` | 每天 03:17；保留天数 `monitor.history.retention-days`（默认 7）；失败上抛交 Spring 错误处理器，不吞成 debug |

## 其他调度入口（程序化，非 `@Scheduled`）

- `service.impl.SyncTaskServiceImpl`：自持 `ThreadPoolTaskScheduler`，`@PostConstruct` 恢复库中启用的同步任务（任务级 cron 在 `ms_sync_task` 行上）；执行日志清理以 `CronTrigger("0 30 3 * * ?")` 注册在同一调度器（保留 30 天）。类内注释明确「不再另引 `@Scheduled`」，故不入上表。

## @EnableScheduling 入口

- `config.MonitorAutoConfiguration`：`@AutoConfiguration + @ComponentScan("com.chua.starter.monitor.support") + @EnableScheduling`。宿主引入本 starter 即启用调度，本模块全部 `@Scheduled` 均由它驱动。

> 注意：新增定时任务请放本包（scheduler）并更新本清单；与连接 / 实例状态强耦合的可以留在业务类，
> 但必须在「内联 @Scheduled」表中登记。SSE 心跳的 20s 周期是对网关 30~60s 空闲超时留的余量，不要随意拉长。

# server-starter 定时任务索引

本模块全部后台定时功能统一收录在本包（`com.chua.starter.server.support.scheduler`）。
调度分两条线：

- **固定周期任务**：统一走 common-starter 哈希时间轮（`ServerTaskScheduler`，
  `Timer.newTimer(512 槽, 500ms tick)`），任务以「细粒度扫描 + 各主机/项目自身到期判断」方式运行，
  改主机/项目配置即时生效，无需重启；
- **逐任务 cron**：`ExecJobScheduler` 用独立 `ThreadPoolTaskScheduler` 为每个 CRON 计划任务注册
  `CronTrigger`，到点即触发，精度不受扫描周期限制。

## 专属任务类（本包内）

| 类 | 业务 | 触发方式 | 周期 | 开关 / 配置键 |
|---|---|---|---|---|
| `ServerTaskScheduler` | 时间轮基础设施（非业务任务）：`@PostConstruct` 启动时间轮并注册下列全部固定周期任务；监听 `ServerConfigChangedEvent`，仅当 `connection.*` 相关 key 变化时重排连接健康检查 | 时间轮 `scheduleAtFixedRate` | — | `connection.sweep.interval`、`connection.idle.timeout`（系统配置） |
| `HostOnlineScheduler#tick` | 主机在离线检测：TCP 探测（Windows 5985 / 其他按主机端口默认 22），虚拟线程并发；状态翻转回调 `onlineHook` | 时间轮 5s 扫描 | 各主机 `serverHostOnlineInterval`（默认 60s，最小 5s）到期执行 | 主机字段 `serverHostOnlineEnabled`（`ms_server_host`，空视为启用） |
| `HostOnlineScheduler#collectTick` | 主机硬件/使用率定时采集：经 `HostCollectProviderRegistry` SPI 按 `collect_mode` 路由；`IN_FLIGHT` 原子占位防重叠，按上轮实测耗时提前触发补偿 | 时间轮 1s 扫描 | 各主机 `serverHostCollectInterval`（默认 15s，最小 5s）到期执行 | 主机字段 `serverHostCollectEnabled` / `serverHostCollectMode` |
| `HostOnlineScheduler#delayTick` | 主机延迟检测：TCP 连接测 RTT 毫秒并回写（不可达置 -1） | 时间轮 5s 扫描 | 各主机 `serverHostDelayInterval`（默认 60s）到期执行 | 主机字段 `serverHostDelayEnabled` |
| `ActuatorMonitorScheduler#tick` | 项目 Actuator 端点定时采集：复用 `ReleaseService` 端点探测，结果经 `ReleaseSseHub` 广播 `project-endpoints`；虚拟线程执行 | 时间轮 5s 扫描 | 各项目 `intervalSeconds`（默认 30s）到期执行 | 项目级采集配置（`releaseService.listActuatorMonitors()`，存 `ms_server_config`） |
| `SessionIdleSweeper#sweep` | 终端会话空闲清理（guacd 中转 + 各直连通道）+ 孤儿审计记录对账闭环 | 时间轮，首扫延迟 120s、之后每 60s | — | `plugin.server.support.session-idle-minutes`（默认 30） |
| 连接健康检查（`ServerTaskScheduler` 内联 lambda） | 连接缓存空闲清理：`connectionManager.sweep(idle)` | 时间轮 `scheduleAtFixedRate` | `connection.sweep.interval` 秒（默认 60，最小 5） | `connection.sweep.interval`、`connection.idle.timeout`（默认 300s，系统配置） |
| `ExecJobScheduler` | 批量执行「计划任务」（`ms_exec_job`，CRON 型）逐任务调度：执行走 `execService.runScheduledJob`；增删改/启停经 `ExecJobChangedEvent` 就地重排或取消 | 自建 `ThreadPoolTaskScheduler`（`exec-cron-`，poolSize=2）+ `CronTrigger` | 每任务各自的 cron | 任务行 `ms_exec_job_enabled=1` 且类型为 CRON（脚本 NONE 型不注册） |

## 内联 @Scheduled（留在业务类，未搬移）

| 位置 | 业务 | 触发参数 | 说明 |
|---|---|---|---|
| `sse.ReleaseSseHub#heartbeat` | 发布管理 SSE 保活：向全部连接发 ping 注释帧，写失败即清理 | `@Scheduled(fixedDelay = 25_000)` | 依赖实例内 `emitters` 连接表，与连接生命周期强耦合，故留在原类 |
| `sse.HostAssetsSseHub#heartbeat` | 主机资产 SSE 保活：同上（独立长连接，与发布管理互不影响） | `@Scheduled(fixedDelay = 25_000)` | 同上 |
| `service.MailboxSseService#pollAndPush` | 邮箱身份新邮件轮询（IMAP/临时邮箱 API）并经 SSE 推送，单 tick 每身份最多 20 封 | `@Scheduled(fixedDelay = 10_000)` | 依赖实例内 `clientIdentityMap` / `seenEmailIds` 去重状态，故留在原类 |

## 非本模块线程调度的定时业务（备查，未搬移）

| 位置 | 业务 | 实际调度方 | 说明 |
|---|---|---|---|
| `schedule.ReleaseScheduleService` / `ReleaseScheduleServiceImpl` | 「项目定时发布」（一次性 cron `release-publish-{id}`）与「Git 定时轮询」（cron `release-git-poll-{id}`）的登记/撤销/运行态查询；启动时 `ApplicationReadyEvent` 全量同步并清理幽灵任务 | job-starter（`JobOperator`；`@ConditionalOnBean(JobOperator.class)` 才装配，宿主未引入 job-starter 时本服务不存在） | 本类是任务登记/管理服务，自身无 `@Scheduled`；到点执行的是其内部 `PublishJobHandler` / `GitPollJobHandler`（由 job-starter 触发），业务与 job-starter 强耦合，故留在 `schedule` 包 |

## @EnableScheduling 入口

- `config.ServerAutoConfiguration`（模块唯一 `@EnableScheduling`，上面 3 个内联 `@Scheduled` 依赖它生效；
  时间轮与 `exec-cron-` 线程池为自建调度器，不依赖该开关）

> 新增固定周期任务请挂到 `ServerTaskScheduler` 时间轮并更新本清单；
> 一次性 / cron 型业务任务优先登记 job-starter（参考 `ReleaseScheduleServiceImpl`）。

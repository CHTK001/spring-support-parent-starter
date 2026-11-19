# datasource-hub-starter 定时任务索引

本模块全部后台定时功能：专属任务类统一放在本包（`com.chua.starter.datasourcehub.support.scheduler`）；
与实例内状态（JDBC 连接缓存、事务会话表、SSE 订阅者集合）强耦合的内联 `@Scheduled`
保留在原类，仅在此登记。查找顺序：先看本清单，再进对应类。

与 ai-starter 不同，本模块的周期 / cron 全部使用**编译期常量**而非配置占位符：
宿主经 collect-starter 传递依赖引入本模块时，相关 bean 会被卷入早期创建链，
embedded value resolver 尚未就绪，`${...}` 会原样进入 cron / 时间解析器直接炸掉启动
（实测 2026-10-11）。因此下表「配置键」一列凡标注「常量」的，改值需改代码重新编译。

## 专属任务类（本包内）

| 类 | 业务 | 触发方式 | 周期 | 配置键 |
|---|---|---|---|---|
| `NginxMetricScheduler#collect` | 逐个采集各 nginx 数据源的 `stub_status` 落库，并向 SSE 推送趋势点与实时运行态（失败只记日志不中断其它数据源） | `@Scheduled` fixedRate | 固定 30s，首延迟 20s（常量） | 类注释中的设计键 `monitor.nginx.collect-interval-ms`（默认 30s）当前未接入注解 |
| `DatasourceMetricScheduler#collect` | 逐个探测被订阅（`DatasourceMetricStreamRegistry#watchedIds()`）数据源的真实连通性并推送指标 SSE；探测失败也广播错误帧 | `@Scheduled` fixedRate | 固定 60s，首延迟 60s（常量） | 无（常量，原因见上文占位符说明） |
| `DatasourceMetricScheduler#purgeExpired` | 滚动清理超保留期（7 天）的历史指标 | `@Scheduled` cron | 每日 04:20（`0 20 4 * * ?`） | 保留天数固定 7（常量，与趋势查询最大回溯 24×7h 对齐） |
| `DatasourceOperLogScheduler#purgeExpired` | 滚动清理超保留期（90 天）的操作日志 `ms_datasource_oper_log` | `@Scheduled` cron | 每日 04:40（`0 40 4 * * ?`） | 保留天数固定 90（常量） |

三个任务类都通过 `ObjectProvider` 可选装配依赖服务：服务未引入时本轮直接跳过，而不是让启动失败。

## 内联 @Scheduled（留在业务类，未搬移）

| 位置 | 业务 | 触发参数 | 说明 |
|---|---|---|---|
| `support.JdbcDatasourceSupport#sweepExpiredJdbcConnections` | 清扫空闲过期的 JDBC 缓存连接（`ExpireValue` 过期是惰性的，闲置数据源没人取连接，需主动核对触发关闭回调，防 socket 泄漏） | `@Scheduled(fixedDelay=60_000)` | 直接遍历实例内 `jdbcConnectionCache`，故留在原类。连接 TTL 来自 `plugin.datasource.hub.connection-ttl-minutes`（默认 10 分钟）；`plugin.datasource.hub.connection-sweep-interval-ms`（默认 60000，0=关闭清扫）已在 `DatasourceHubProperties` 定义，但注解当前写死 60s 未消费该键 |
| `dbconsole.DbTxRegistry#purgeIdlePeriodically` | 回收空闲超时的显式事务会话（回滚并关闭连接，防「用户忘了提交」拖垮连接池） | `@Scheduled(fixedDelay=60_000)` | 直接遍历实例内 `sessions`，故留在原类。空闲阈值 `monitor.db.tx.idleTimeoutMinutes`（默认 10 分钟）；并发上限 `monitor.db.tx.maxSessions`（默认 8，非调度项） |
| `dbconsole.DbCopyTaskRegistry#heartbeat` | 复制任务 SSE 心跳（推 `:keep-alive` 注释帧，防网关 30~60s 掐断空闲长连接） | `@Scheduled(fixedRate=20_000, initialDelay=20_000)` | 直接遍历实例内 `subscribers`，故留在原类；无配置键 |
| `nginx.NginxMetricStreamRegistry#heartbeat` | nginx 指标 SSE 心跳（注释帧保活，语义同上） | `@Scheduled(fixedRate=20_000, initialDelay=20_000)` | 直接遍历实例内 `subscribers`，故留在原类；无配置键 |
| `metric.DatasourceMetricStreamRegistry#heartbeat` | 数据源指标 SSE 心跳（注释帧保活，语义同上） | `@Scheduled(fixedRate=20_000, initialDelay=20_000)` | 直接遍历实例内 `subscribers`，且其 `watchedIds()` 决定 `DatasourceMetricScheduler` 的采集范围，故留在原类；无配置键 |

## @EnableScheduling 入口

本模块自身**没有** `@EnableScheduling` / `SchedulingConfigurer`：`config.DatasourceHubAutoConfiguration`
只做 `@ComponentScan` + `@MapperScan` + `@EnableConfigurationProperties`。
上表所有 `@Scheduled` 是否注册生效，取决于**宿主应用**是否启用了调度
（宿主主类或其它 starter 上的 `@EnableScheduling`）；宿主未开启时这些方法不会被执行。

> 新增定时任务请放本包并更新本清单；与实例状态强耦合的轮询 / 心跳可留在原类，但必须在本表登记。

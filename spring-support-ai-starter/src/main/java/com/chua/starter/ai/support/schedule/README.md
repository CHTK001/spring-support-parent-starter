# ai-starter 定时任务索引

本模块全部后台定时功能统一收录在本包（`com.chua.starter.ai.support.schedule`）。
查找顺序：先看本清单，再进对应类。调度统一走「动态 Trigger / 时间轮自续」，间隔与开关
几乎都从 `sys_ai_global_setting` 现读，改配置无需重启。

## 专属任务类（本包内）

| 类 | 业务 | 触发方式 | 周期 | 开关 / 配置键 |
|---|---|---|---|---|
| `UsageAggregationTask` | 用量聚合落库（明细→日汇总） | 动态 Trigger（可切时间轮） | `usage_report_interval` 秒，默认 3600s | `enable_usage_analysis`（global）；`usage_timewheel_enabled`；白名单 `usage_sync_providers` |
| `MemoryMaintenanceTask` | 记忆体维护（过期/衰减/合并/归档/淘汰） | 动态 Trigger（上次完成时间为基准，不重叠） | `memory_maintenance_interval_minutes` 分钟，默认 60 | `memory_maintenance_enabled`（memory 分组） |
| `CreditRefreshScheduler` | 供应商余额定时刷新（按 Key 落 `sys_ai_provider_credit`） | 时间轮自续（512 槽），首刷延迟 60s | 固定 300s（代码常量，不可配） | `credit_refresh_enabled`（global，默认 true） |
| `ChannelHealthProbeTask` | 渠道健康全量探针（状态机 UNKNOWN/ONLINE/WARNING/DOWN/RATE_LIMITED） | `@Configuration + @EnableScheduling` + 动态 Trigger | `health.channel_health_probe_interval_sec`（服务内 `intervalSec()`） | 按渠道时间轮开启时本任务让位：`channel_probe.channel_probe_wheel_enabled` |
| `ChannelProbeWheelTask` | 按渠道独立节奏探针（时间轮 512 槽 × 1s，ApplicationRunner 启动） | 时间轮，每渠道一条，跑完自续 | `channel_probe.channel_probe_interval_sec.<channel>` → `health.channel_health_probe_interval_sec` → 60s 兜底，最小 30s | `channel_probe.channel_probe_wheel_enabled`（默认开） |
| `ChannelCheckinTask` | 账号渠道自动签到（逐渠道逐账号，凭据回写） | 动态 Trigger（SchedulingConfigurer） | `channel_checkin_interval_sec` 秒，默认 3600，最小 300；当日去重 `channel_checkin_last_date`（UTC+8） | `enable_channel_checkin`（global，默认开） |

## 内联 @Scheduled（留在业务类，未搬移）

| 位置 | 业务 | 触发参数 | 说明 |
|---|---|---|---|
| `service.impl.SysAiSessionTraceServiceImpl#scheduledFlush` | 会话轨迹队列批量落库 | `@Scheduled(initialDelay=2000, fixedDelay=1000)` | 每秒一 tick，队列积压时连续刷多批（`MAX_BATCHES_PER_TICK` 上限）；与业务写队列强耦合，故留在原类 |
| `controller.UsageLiveStreamHub#flush` | 用量 SSE 合并推送 | `@Scheduled(initialDelay=3000, fixedDelay=1000)`（`flushIntervalMs=1000`） | 依赖实例内 `subscribers / version / scheduledEnabled` 状态，故留在原类 |

## @EnableScheduling 入口

- `configuration.usage.UsageAutoConfiguration`（用量线：聚合、轨迹、直播推送）
- `schedule.ChannelHealthProbeTask` 自带 `@EnableScheduling`（健康探针线）

> 注意：宿主是否启用调度由各 AutoConfiguration 决定；关掉对应全局开关只是跳过执行，
> 注册仍在。新增定时任务请放本包并更新本清单。

# device-starter 定时任务索引

本模块后台定时功能清单（索引置于任务所在包 `timeseries`）。
调度分两套：Spring `@Scheduled`（本包 `DeviceSeriesRecorder`）与
common-starter `TimeScheduler` 时间轮（`scheduler.DeviceScheduleManager`）。

## 专属任务类（独立 schedule 包）

无。本模块当前唯一的 `@Scheduled` 任务与写入器实例状态强耦合，保留原位；
厂商轮询类任务已统一由时间轮调度器承载（见下）。

## 内联 @Scheduled（留在业务类，未搬移）

| 位置 | 业务 | 触发参数 | 周期 | 开关 / 配置键 |
|---|---|---|---|---|
| `timeseries.DeviceSeriesRecorder#purgeExpired` | 时序点保留期清理：按保留期删除过期时序点（传感器/门禁/车闸三类明细），失败只记 WARN 不阻断业务 | `@Scheduled(cron = "0 30 3 * * ?")`，每天 03:30 执行一次 | 固定 cron（不可配） | `plugin.device.timeseries.retention-days`（`<=0` 不清理）；引擎不可用时跳过 |

留原位原因：`purgeExpired` 读写 recorder 私有字段 `engine` / `retentionDays`，
且该 Bean 由 `DeviceTimeSeriesConfiguration#deviceSeriesRecorder` 以
`@ConditionalOnMissingBean` 装配、宿主可整体覆盖；抽类需复制判定逻辑或暴露内部状态。

## 时间轮调度（非 @Scheduled，一并登记）

| 位置 | 业务 | 触发方式 | 周期 | 开关 / 配置键 |
|---|---|---|---|---|
| `scheduler.DeviceScheduleManager` | 设备侧全部轮询任务的时间轮时钟（common-starter `TimeScheduler`，5s tick）：平台级 tick 为 `deviceAccessExtract`（门禁记录抽取）、`deviceVehiclePassExtract`（过车抽取）、`deviceAttendanceSync`（考勤归集）；凭据级任务 `offlineDetect_{credId}` / `eventPoll_{credId}` / `deviceSync_{credId}` / `orgSync_{credId}` / `metricCollect_{credId}` 按每套提供商凭据独立注册 | 时间轮固定频率 tick，到期判定走 `DeviceTaskStateService` | tick 固定 5s；各任务间隔由 `device_task_state.interval` 控制，默认首隔 30s | 凭据级开关/间隔存于 `device_provider_credential` 能力配置，默认关闭；`/api/device/tasks` 可改间隔（`rescheduleInterval`） |

## @EnableScheduling 入口

- `configuration.DeviceAutoConfiguration`（类上 `@EnableScheduling`，驱动 `DeviceSeriesRecorder#purgeExpired`）

> 新增定时任务：轮询类优先挂到 `DeviceScheduleManager` 时间轮（凭据级）；
> 独立周期任务优先新建 `com.chua.starter.device.support.schedule` 包；
> 确需内联时必须更新本清单并写明耦合原因。

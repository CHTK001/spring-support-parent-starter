# hotsport-starter 定时任务索引

本模块后台定时功能清单。当前仅一个内联 `@Scheduled` 采样任务，
与宿主实例状态强耦合，按归拢原则保留在业务类原位，不设独立 schedule 包。

## 专属任务类（独立 schedule 包）

无。本模块暂无可干净解耦的独立任务类；新增任务优先新建
`com.chua.starter.hotsport.support.schedule` 包并回到本清单登记。

## 内联 @Scheduled（留在业务类，未搬移）

| 位置 | 业务 | 触发参数 | 周期 | 开关 / 配置键 |
|---|---|---|---|---|
| `service.JvmMetricsService#recordSample` | JVM 指标采样：PlatformMXBean 抓堆内存 / GC / 线程 / CPU / OS 资源快照，写入 2000 点环形缓冲（约 10h@5s），并触发 `onSample` 告警巡检钩子（`AlertMonitorService` `@Lazy` 注册） | `@Scheduled(fixedDelayString)`；`@PostConstruct` 先采一次保证缓冲立即可读 | `plugin.hotsport.jvm.sample-interval-ms`，默认 5000ms | `plugin.hotsport.jvm.sample-interval-ms`（无独立开关，随装配常驻） |

留原位原因：`recordSample` 读写实例内 `ring` / `lastSample` / `sampleCount` / `onSample`，
查询 API（`metrics(int)` / `lastSample()`）与告警巡检共享同一份状态；抽类会拆散状态
或引入服务↔任务回环引用，不属于「能干净解耦的简单任务」。

## @EnableScheduling 入口

- `HotsportApplication`（独立启动类，`@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)` + `@EnableScheduling`）

> 新增定时任务请优先进独立 schedule 包；确需内联时必须更新本清单并写明耦合原因。

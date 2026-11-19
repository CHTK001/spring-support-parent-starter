# socket-starter 定时任务索引

本模块全部后台定时功能统一收录在本包（`com.chua.socket.support.schedule`）。

## 专属任务类（本包内）

| 类 | 业务 | 触发方式 | 周期 | 开关 / 配置键 |
|---|---|---|---|---|
| `SocketHeartbeatTask` | Socket 服务器注册心跳：周期刷新本实例在 Redis（`socket:servers:{serverId}` 哈希）中的心跳时间并续期 3600s，供多实例按心跳判定在线（`SocketServerRegistry#getAllOnlineServers`，120s 无心跳判离线） | `@Scheduled(fixedDelay)`（调度壳），业务逻辑在 `session.SocketServerRegistry#heartbeat` | 固定 30s（代码常量 `HEARTBEAT_INTERVAL_MS`，不可配） | `plugin.socket.enable`（`SocketProperties.isEnable()`；关闭时 registry 直接跳过） |

## 说明

- 本包任务类只做调度壳：enable 判断、serverId、Redis 读写等业务全部保留在
  `session.SocketServerRegistry`（`heartbeat()` 保持 public 供任务壳调用），注册表状态不外泄。
- 心跳原为 `SocketServerRegistry` 类内联 `@Scheduled(fixedDelay = 30_000)`（原 L152），
  本次解耦搬入本包，触发参数与执行逻辑均未改动。
- `SocketServerRegistry`（`@Service`）与本任务类（`@Component`）都依赖宿主组件扫描装配：
  本模块自动配置 `SocketConfiguration` 只 `@ComponentScan` 了 `bridge` 包，
  宿主需扫描覆盖 `com.chua.socket.support` 才会注册这两个 Bean。
- 本模块自身没有 `@EnableScheduling`：心跳要真正跑起来，需宿主开启调度注解
  （宿主自身的 `@EnableScheduling`，或引入 server-starter 的 `ServerAutoConfiguration`）。

> 新增定时任务请放本包并更新本清单；与实例状态强耦合的 tick 可保留在业务类并在本清单登记。

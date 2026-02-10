# Changelog

## [Unreleased]

### Added

- **重连配置支持**：客户端现在支持配置重连参数
  - `auto-reconnect`: 是否启用自动重连（默认 true）
  - `max-reconnect-attempts`: 最大重连次数（默认 -1 表示无限）
  - `reconnect-interval`: 重连间隔时间（默认 5000 毫秒）
- **SyncServerServiceDiscovery**：新增基于 SyncServer 的服务发现实现
  - 使用长连接客户端作为服务节点
  - 通过应用名称（clientApplicationName）进行服务分组
  - 支持多种负载均衡策略（weight/round/random 等）
  - 实时感知节点上下线
  - 主要方法：`getService(path)`、`getServiceAll(path)`、`getOnlineClients()`、`getApplicationNames()`

### Changed

- **配置大幅简化**：删除多个冗余字段，这些值现在直接从 Spring Environment 获取
  - `spring.application.name` -> appName
  - `server.servlet.context-path` -> contextPath
  - `server.port` -> 应用端口
  - `management.endpoints.web.base-path` -> actuatorPath（默认 `/actuator`）

### Fixed

- **同步 SPI 服务可用性修复**：移除对不存在适配器与过滤器类型的强依赖，过滤器 SPI 在缺失类时返回内置列表，避免启动失败

### Removed

- **ClientConfig**：删除 `appName`、`contextPath`、`actuatorPath`、`port`、`reconnectInterval`、`maxReconnectAttempts`、`schedule` 字段
- **ServerConfig**：删除 `schedule` 字段
- **ScheduleConfig**：删除整个类（未使用）
- **废弃方法**：删除所有 @Deprecated 兼容方法

### Migration

如果之前配置了以下属性，现在可以删除：

```yaml
plugin:
  sync:
    client:
      # 以下配置已废弃，自动从 Spring 获取
      app-name: xxx # 改用 spring.application.name
      context-path: xxx # 改用 server.servlet.context-path
      actuator-path: xxx # 改用 management.endpoints.web.base-path
      port: xxx # 改用 server.port
      reconnect-interval: xxx # 已删除
      max-reconnect-attempts: xxx # 已删除
      schedule: # 已删除
        enable: false
```

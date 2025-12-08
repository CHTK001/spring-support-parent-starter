# Changelog

## [Unreleased]

### Changed
- **配置大幅简化**：删除多个冗余字段，这些值现在直接从 Spring Environment 获取
  - `spring.application.name` -> appName
  - `server.servlet.context-path` -> contextPath
  - `server.port` -> 应用端口
  - `management.endpoints.web.base-path` -> actuatorPath（默认 `/actuator`）

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
      app-name: xxx              # 改用 spring.application.name
      context-path: xxx          # 改用 server.servlet.context-path
      actuator-path: xxx         # 改用 management.endpoints.web.base-path
      port: xxx                  # 改用 server.port
      reconnect-interval: xxx    # 已删除
      max-reconnect-attempts: xxx # 已删除
      schedule:                  # 已删除
        enable: false
```

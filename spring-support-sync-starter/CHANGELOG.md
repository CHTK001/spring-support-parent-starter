# Changelog

## [Unreleased]

### Changed
- **ClientConfig 配置简化**：删除 `appName`、`contextPath`、`actuatorPath` 字段，这些值现在直接从 Spring Environment 获取
  - `spring.application.name` -> appName
  - `server.servlet.context-path` -> contextPath
  - `management.endpoints.web.base-path` -> actuatorPath（默认 `/actuator`）

### Migration
如果之前配置了以下属性，现在可以删除：
```yaml
plugin:
  sync:
    client:
      # 以下配置已废弃，自动从 Spring 获取
      app-name: xxx        # 改用 spring.application.name
      context-path: xxx    # 改用 server.servlet.context-path
      actuator-path: xxx   # 改用 management.endpoints.web.base-path
```

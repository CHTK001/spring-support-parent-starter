# Spring Support Proxy Starter

代理模块负责“代理实例管理与内嵌代理服务控制”。它把原先散落在网关里的代理管理能力抽成独立 starter，支持按需启用、独立装配和单独 smoke 验证。

## 当前能力

- 代理实例管理：代理服务的新增、查询、更新、删除、克隆
- 生命周期控制：启动、停止、重启、状态查询、端口可用性校验
- 配置管理：过滤器/设置项、限流、HTTPS、文件存储、服务发现、预览扩展
- 运行时信息：代理类型枚举、实例统计、日志查询
- 启动策略：支持按配置决定是否自动恢复数据库中标记为运行中的代理实例

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-proxy-starter</artifactId>
    <version>4.0.0.37</version>
</dependency>
```

### 2. 基础配置

```yaml
plugin:
  proxy:
    enable: true
    auto-restart-running: false
```

说明：

- `plugin.proxy.enable=true` 时，注册 `ProxyManagementConfiguration`、Mapper 扫描和代理管理相关控制器。
- `plugin.proxy.auto-restart-running=true` 时，应用启动后会尝试恢复数据库中状态为 `RUNNING` 的代理实例。
- 首次接入建议先设置 `auto-restart-running=false`，先完成数据表和实例配置，再逐步打开自动恢复。

### 3. 数据准备

- 代理模块依赖可用 JDBC 数据源。
- 完整代理表结构可复用网关模块现有初始化脚本：
  `spring-support-gateway-starter/src/main/resources/db/init/V1.0__init_gateway_proxy.sql`
- 如果只是做最小 smoke 验证，可参考：
  `spring-support-module-smoke-test/src/test/resources/db/proxy/V1.0__init_proxy.sql`
  和
  `spring-support-module-smoke-test/src/test/resources/db/proxy/V1.0__initdata_proxy.sql`

## 核心接口

- `GET /proxy/server/page`：分页查询代理实例
- `GET /proxy/server/statistics`：查询实例统计
- `GET /proxy/server/types`：查询可用代理类型
- `GET /proxy/server/check-port`：校验端口占用
- `POST /proxy/server/{id}/start`：启动实例
- `POST /proxy/server/{id}/stop`：停止实例
- `POST /proxy/server/{id}/restart`：重启实例
- `GET /proxy/server/setting/**`：代理设置、查看器与扩展配置

## 轻控制台

- 已提供独立静态页产物：`src/main/resources/static/proxy-console`
- 页面定位：代理实例状态、过滤链编排、JSON 配置编辑、最近日志巡检
- 页面边界：不再提供“页面新增端口”，端口来源以 yml / 部署约定 / 初始化数据为准
- 默认接口根路径：`../proxy/`

重新发布轻控制台：

```powershell
pnpm --dir H:\workspace\2\vue-support-parent-starter --filter vue-support-spring-pages-starter publish:proxy-console
```

## 文档

- [接入手册](docs/proxy-integration-manual.md)
- [单元测试报告](docs/代理模块单元测试报告.md)
- [烟雾测试报告](docs/proxy-smoke-test-report.md)

# Spring Support Strategy Starter

策略模块负责“运行时治理与防护”，而不是业务编排。它当前提供方法级策略、HTTP 请求防护、动态配置、轻控制台和监控指标。

## 当前能力

- 运行时治理：限流、防抖、熔断、重试、超时控制、舱壁隔离
- 并发安全：幂等、分布式锁
- 请求安全：IP 访问控制、XSS、SQL 注入、CSRF、路径穿透、请求体大小、参数数量、HTTP 方法、点击劫持、CSP
- 运维支撑：Redis 配置同步、Actuator 指标包装、轻控制台、嵌入式账号密码登录
- 存储形态：限流/防抖支持 `local` 与 `redis` 两类实现，未引入 Redis 依赖时自动回退为本地能力

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-strategy-starter</artifactId>
    <version>4.0.0.37</version>
</dependency>
```

### 2. 基础配置

```yaml
plugin:
  strategy:
    enable: true
    rate-limiter:
      enabled: true
      type: local
    debounce:
      enabled: true
      type: local
    circuit-breaker:
      enabled: true
    web-auth:
      mode: embedded
      username: admin
      password: admin123
      session-timeout: 3600
```

说明：

- `plugin.strategy.web-auth.mode=embedded` 时，`/strategy-console/**` 和 `/v2/strategy/**` 受轻控制台登录保护。
- `plugin.strategy.web-auth.mode=none` 时，关闭控制台登录校验。
- 若要启用 Redis 分布式能力，需要额外引入 Redis 依赖；未引入时不会因为类加载失败导致 starter 启动异常。

## 控制台与接口

- 控制台入口：`/strategy-console`
- 登录状态：`GET /v2/strategy/auth/status`
- 登录接口：`POST /v2/strategy/auth/login`
- 指标接口：`GET /v2/strategy/metrics`

常见配置管理接口：

- `/v2/strategy/limit/**`
- `/v2/strategy/debounce/**`
- `/v2/strategy/circuit-breaker/**`
- `/v2/strategy/circuit-breaker-record/**`

## 模块边界

适合放进策略模块的能力：

- 决策型、约束型、保护型的通用规则
- 与具体业务领域无关、可跨系统复用的执行控制
- 需要动态配置、统一监控和横向复用的运行时治理能力

不建议放进策略模块的能力：

- 业务排程本身，例如支付超时关闭、订单补偿
- 请求行为分析与 AI 总结，这类更偏观察和分析
- 支付、租户、OAuth 等强业务领域功能

## 文档

- [接入手册](docs/strategy-integration-manual.md)
- [模块边界分析](docs/strategy-module-classification.md)
- [单元与模块测试报告](docs/策略模块单元测试报告.md)
- [烟雾测试报告](docs/strategy-smoke-test-report.md)

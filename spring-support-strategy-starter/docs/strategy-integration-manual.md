# 策略模块集成手册

## 1. 模块定位

`spring-support-strategy-starter` 负责通用的运行时治理与请求防护，不负责业务任务编排。

它适合承载：

- 限流、防抖、熔断、重试、超时、舱壁
- 幂等、分布式锁
- 请求安全防护
- 动态配置同步
- 轻量控制台和监控指标

它不适合承载：

- 支付、订单、租户等业务专属规则
- 任务调度引擎和业务超时任务
- 请求行为画像、AI 总结、风控分析

## 2. 依赖

### 2.1 最小依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-strategy-starter</artifactId>
</dependency>
```

### 2.2 启用 Redis 分布式能力

当限流、防抖、配置同步需要走 Redis 时，再额外引入 Redis 相关 starter。

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-redis-starter</artifactId>
</dependency>
```

说明：

- 未引入 Redis 依赖时，starter 仍应正常启动。
- Redis 相关 Bean 已被隔离到可选配置中，不再把 Redis 类型泄漏到核心自动配置签名里。

## 3. 基础配置

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
    xss:
      enabled: false
    sql-injection:
      enabled: true
    csrf:
      enabled: false
    path-traversal:
      enabled: true
    request-size-limit:
      enabled: false
    parameter-count-limit:
      enabled: false
    http-method-restriction:
      enabled: false
    request-timeout:
      enabled: false
    content-security-policy:
      enabled: false
    clickjacking-protection:
      enabled: true
    web-auth:
      mode: embedded
      username: admin
      password: admin123
      session-timeout: 3600
```

关键点：

- `rate-limiter.type` 和 `debounce.type` 可选 `local`、`redis`
- `web-auth.mode` 可选 `embedded`、`none`
- 轻控制台认证默认开启

## 4. 轻控制台

### 4.1 页面入口

- `/strategy-console`
- `/strategy-console/login.html`

### 4.2 认证接口

- `GET /v2/strategy/auth/status`
- `POST /v2/strategy/auth/login`
- `POST /v2/strategy/auth/logout`
- `GET /v2/strategy/auth/info`

### 4.3 指标接口

- `GET /v2/strategy/metrics`
- `GET /v2/strategy/metrics/{type}`
- `POST /v2/strategy/metrics/reset/{type}`

说明：

- 未登录访问 `/v2/strategy/**` 会收到 `401` JSON
- 未登录访问 `/strategy-console/**` 会跳转到登录页
- `/v2/strategy/**` 属于控制面接口，默认不再进入业务策略拦截链，避免控制台接口被自身动态策略反向拦截。

## 5. 推荐接入方式

### 5.1 通用运行时保护放策略模块

例如：

- 接口限流
- 重试
- 幂等
- 分布式锁
- 请求安全规则

### 5.2 业务调度放 job / payment 等业务模块

例如：

- 支付超时关单
- 通知补偿
- 订单自动取消

这些应由 `job` 触发业务方法，而不是在策略模块里直接承载业务轮询。

## 6. 多节点同步

`StrategyConfigSyncService` 使用 Redis Pub/Sub 同步配置刷新事件。

设计约束：

- Redis 不存在时，同步服务只降级为不可用，不影响应用启动
- 同步逻辑仅依赖 `StrategyRedisSupport` 抽象，避免核心类直接依赖 Spring Data Redis

## 7. 验证建议

建议按下面顺序验证：

1. 先做无 Redis 启动验证，确认最小依赖能正常装配。
2. 再做 Redis 场景验证，确认限流、防抖、配置同步切到分布式实现。
3. 最后验证控制台登录、受保护接口与指标接口。

本仓库已使用简单项目 `spring-support-module-smoke-test` 完成一轮低内存烟雾验证：

```bash
MAVEN_OPTS="-Xms256m -Xmx768m" mvn -DskipTests=false -Dmaven.test.skip=false -Dsurefire.failIfNoSpecifiedTests=false -pl spring-support-module-smoke-test -am -P smoke-strategy test
```

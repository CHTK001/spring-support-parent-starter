# 策略模块烟雾测试报告

## 1. 执行信息

- 模块：`spring-support-strategy-starter`
- 执行日期：`2026-03-26`
- 简单项目：`spring-support-module-smoke-test`
- 执行命令：

```bash
MAVEN_OPTS="-Xms256m -Xmx768m" mvn -DskipTests=false -Dmaven.test.skip=false -Dsurefire.failIfNoSpecifiedTests=false -pl spring-support-module-smoke-test -am -P smoke-strategy test
```

- 执行结果：`BUILD SUCCESS`

## 2. 用例清单

| 测试类 | 用例数 | 结果 | 覆盖点 |
| --- | ---: | --- | --- |
| `SmokeModuleContextTest` | 7 | 通过 | 上下文启动、健康端点、策略模块标识、认证状态接口、未登录受保护接口、控制台页重定向、登录后指标接口 |

## 3. 已验证能力

- 在未引入 Redis 运行时依赖到烟雾项目的前提下，策略 starter 可正常启动。
- `StrategyAutoConfiguration` 不再因为 Redis 类型出现在核心 Bean 签名里导致类加载失败。
- `GET /v2/strategy/auth/status` 可正常返回统一 `ReturnResult`。
- 未登录访问 `GET /v2/strategy/metrics` 会返回 `401`。
- 未登录访问 `GET /strategy-console/index.html` 会跳转到登录页。
- 使用默认嵌入式账号密码登录后，可访问 `GET /v2/strategy/metrics`。

## 4. 本轮修复点

- 将 Redis 专属 Bean 下沉到 `StrategyRedisConfiguration`。
- 新增 `StrategyRedisSupport` 抽象，避免核心策略类直接依赖 Spring Data Redis 类型。
- `DistributedLockAspect`、`IdempotentAspect`、`StrategyConfigSyncService` 改为依赖抽象能力，而不是硬编码 Redis 实现。
- 将 `/v2/strategy/**` 从业务策略拦截链排除，避免控制台控制面接口再被限流/熔断/防护规则反向影响。
- `StrategyMetricsController` 将指标输出统一规整为普通 `Map` 视图，并在快照构建失败时回退空指标，避免控制台接口因指标对象结构差异直接报错。
- `SysCircuitBreakerConfigurationServiceImpl` 在运行时查不到配置表或数据库暂不可用时改为 fail-open，避免把增强能力放大成业务 `500`。
- 烟雾测试脚本默认加入低内存 `MAVEN_OPTS`，适合本地机器资源受限场景。

## 5. 当前未覆盖项

- 真实 Redis 分布式限流、防抖、配置同步
- 动态配置管理接口的完整 CRUD
- 控制台前端页面交互细节
- 与数据库表驱动配置的联动

这些应继续放到集成测试阶段验证，不建议塞进单一烟雾测试。

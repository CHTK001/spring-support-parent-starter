# 策略模块边界分析

## 1. 归类原则

是否应归入策略模块，核心看三点：

1. 是否是通用能力，而不是某个业务域专属能力。
2. 是否属于运行时约束、保护、降级、执行控制。
3. 是否需要横向复用、动态配置和统一观测。

## 2. 适合归入策略模块

### 2.1 执行治理

- `@RateLimiter`
- `@Debounce`
- `@Retry`
- `@TimeLimiter`
- `@Bulkhead`
- 熔断器动态配置与拦截器

### 2.2 并发控制

- `@DistributedLock`
- `@Idempotent`
- 锁模板、限流模板、策略链模板

### 2.3 请求防护

- IP 黑白名单
- XSS 防护
- SQL 注入防护
- CSRF 防护
- 路径穿透防护
- 请求体大小限制
- 参数数量限制
- HTTP 方法限制
- 请求超时保护
- CSP 与点击劫持防护

### 2.4 运维辅助

- Redis 配置同步
- 策略指标采集与查询
- 轻控制台与嵌入式认证

## 3. spring-parent 模块映射建议

| 模块 | 是否应下沉到策略模块 | 建议 |
| --- | --- | --- |
| `spring-support-strategy-starter` | 是 | 保持为策略核心承载模块 |
| `spring-support-common-starter` | 部分能力可复用，不直接下沉 | 只复用通用响应、缓存、环境注册等基础设施 |
| `spring-support-redis-starter` | 否 | 作为策略分布式实现依赖，不并入策略核心 |
| `spring-support-ai-starter` | 否 | AI 是分析能力，策略模块只消费结果或信号，不吸收 AI 客户端本身 |
| `spring-support-job-starter` | 否 | job 负责调度触发，不负责运行时请求治理 |
| `spring-support-payment-starter` | 否 | 支付是业务域，只应使用策略能力，不应沉到策略模块 |
| `spring-support-sync-data-starter` | 否 | 同步任务、页面和领域逻辑不进入策略；可复用限流/重试/超时能力 |
| `spring-support-gateway-starter` | 部分边界可复用 | 网关中的通用限流、防护、黑白名单可抽成策略能力，网关路由与协议逻辑不下沉 |
| `spring-support-oauth-client-starter` | 否 | 认证协议不属于运行时治理，但登录接口可接入幂等/限流 |
| `spring-support-report-client-starter` | 否 | 上报通道不属于策略核心；策略只暴露指标，由上报模块消费 |
| `spring-support-message-starter` | 否 | 消息投递是业务集成，不是治理核心 |
| `spring-support-filesystem-*` | 否 | 文件存储和对象存储属于资源域；仅在接口层复用防护策略 |
| `spring-support-rpc-starter` / `spring-support-socket-*` | 部分能力可复用 | 可复用超时、重试、限流、熔断思想，但传输协议实现不进入策略模块 |
| `spring-support-configcenter-starter` | 否 | 配置中心负责配置来源，策略模块只消费配置 |
| `spring-support-discovery-starter` | 否 | 服务发现是基础设施，不是策略核心 |
| `spring-support-shell-starter` / `spring-support-ssh-starter` | 否 | 运维执行通道不沉到策略模块，可在入口接入安全与限流 |

结论：

- 能沉到策略模块的，必须是“跨模块可复用的运行时治理能力”。
- 不能因为某个模块也有开关、规则、页面，就把整个模块并进策略。
- 以后优先抽“能力接口和执行器”，不要抽“业务表、业务页面、业务任务”。

## 4. 不适合归入策略模块

### 4.1 行为分析类

- 请求追踪原始数据采集
- 请求行为聚合
- AI 风险总结
- 风控画像

原因：

- 这类能力更偏“观察、分析、归因”，不是“约束、保护、执行控制”。
- 它们可以给策略模块提供输入信号，但不应直接并入策略核心。

### 4.2 业务调度类

- 支付超时关单
- 支付回调补偿
- 订单自动取消
- 任何业务任务编排

原因：

- 这些是业务域任务，应由 `job` 提供统一调度，由 `payment` 等业务模块实现业务处理。

### 4.3 业务域模块

- payment
- oauth
- tenant
- sync-data
- scheduler 平台本身

原因：

- 这些模块有自己的领域模型和表结构，不属于通用策略约束。

## 5. 推荐架构关系

- 策略模块：负责拦截、约束、防护、降级、同步
- 请求追踪/AI 分析模块：负责采集、分析、输出信号
- job 模块：负责定时触发与任务执行
- payment 模块：负责支付业务动作

合理链路应为：

`请求/任务 -> 策略模块保护 -> 业务执行 -> 请求追踪/AI 分析产出信号 -> 运营或策略配置调整`

而不是把所有能力都塞进策略模块。

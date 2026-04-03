# Agent Refactor Checklist

## Final Architecture

当前重构后的边界固定为三层:

1. `BigModelClient = provider + model catalog + inference`
2. `ChatClient = BigModelClient + MCP + scope`
3. `Agent = ChatClient + session + snapshot + permission + orchestration`

补充约束:

- `provider` 在 `Agent` 生命周期内固定
- `model` 可以在当前 `provider` 支持范围内热切换
- `session` 只归 `Agent`
- `scope` 只归 `ChatClient`
- MCP 调度与 tool loop 由 `ChatClient` 执行，不再由 starter 自己重复封装

## Refactor Result

### 1. Layer Split

- [x] 删除旧的 `RoutingChatClient` / `AgentClient*` 误导抽象
- [x] 新增 Spring `ChatScope / ChatContext / ChatResponse / ChatClientSettings`
- [x] 新增 Spring `Agent / AgentRequest / AgentSession`
- [x] `Agent` 通过组合 `ChatClient` 实现，不直接依赖 `BigModelClient`
- [x] `AgentRequest.model` 支持单次请求覆盖模型
- [x] `AgentSession.useModel(...)` 支持会话级模型切换

### 2. SPI Modularization

- [x] `Agent` 改为复用 common 模块的 `AgentProvider` SPI
- [x] starter 提供默认实现 `DefaultAgentProvider`
- [x] `ChatClient` 通过 common `ChatClient.Factory` SPI 创建底层实现
- [x] 默认实现与其他实现可复用同一 `ChatClient.Factory` SPI
- [x] 未再引入自定义 MCP 适配层

### 3. MCP Integration

- [x] 确认 common 模块已有统一 MCP 抽象: `McpProvider / McpClient / McpPlugin`
- [x] 确认 deeplearning 默认 `ChatClient` 已内置 MCP/tool loop
- [x] 删除 starter 内重复 MCP 适配代码
- [x] `ChatClient` 可按 `scope.mcpEnabled` 开关 MCP

### 4. Session / Snapshot / Permission

- [x] `Agent` 支持多会话
- [x] `Agent` 支持快照创建、恢复、分叉、回放
- [x] `Agent` 支持权限评估与策略拦截
- [x] `Agent` 记录运行态和执行记录
- [x] 流式与非流式执行路径统一校验模型与 MCP 权限

### 4.1 Callback / Task Mode / Telemetry

- [x] `Agent` 基于 common `AgentCallback` 发布请求级与任务级时间线事件
- [x] `Agent` 支持 `DEFAULT / TASK / DEBUG` 三档任务可见性模式
- [x] `AgentExecutionRecord.steps` 写入当前可见步骤
- [x] `Agent` 支持请求遥测存储 SPI: `AgentRequestStorage`
- [x] 请求遥测包含 `model / reasoningEffort / endpoint / requestType / userAgent`
- [x] 请求遥测包含 token 指标: `inputTokens / outputTokens / cacheTokens`
- [x] 请求遥测包含费用指标: `inputCost / outputCost / cacheCost / inputUnitPrice / outputUnitPrice / multiplier`
- [x] 请求遥测包含时延指标: `firstTokenLatencyMillis / durationMillis / startedAt / completedAt`

### 5. Simplification

- [x] 提取 `AiChatFactory`
- [x] 删除 Agent 相关复杂嵌套路由实现
- [x] 提取测试内部 stub 为独立测试夹具
- [x] 提取 `AiProperties` 中旧版兼容内部类为独立配置类
- [x] 移除 `ChatClientSettingsResolver` 内部函数式接口

### 6. Documentation

- [x] 为新增公开类型补充类注释
- [x] 为关键非 `@Override` 方法补充注释
- [x] 为关键配置字段补充注释
- [x] README 改为三层职责模型

## Model Switching Rules

- Agent 默认模型: `agent.useModel(model)`
- 会话模型: `agent.session(sessionId).useModel(model)`
- 单次请求模型: `AgentRequest.builder().model(model)...`

优先级:

1. `request.model`
2. `session.model`
3. `agent.defaultModel`

约束:

- 模型必须属于当前固定 `provider` 的模型目录
- 不允许跨 provider 热切换

## Scope Rules

`ChatScope` 当前负责:

- 输入文本
- 模型
- system prompt
- temperature / maxTokens / timeout
- 上下文
- 图片输入
- MCP 开关
- 输入优化
- 上下文压缩
- 透传参数与扩展属性

`ChatClient` 当前行为:

- 每次调用按 scope 创建新的底层 common `ChatClient`
- 用当前模型执行输入优化与上下文压缩
- 用 common SPI 选择底层实现
- 用 common 默认实现执行 MCP 自动编排

## Verification

- [x] `mvn -pl spring-support-ai-starter -DskipTests compile`
- [x] `mvn -pl spring-support-common-starter,spring-support-ai-starter -DskipTests compile`
- [x] `mvn -pl spring-support-ai-starter clean test`
- [x] `mvn -f spring-support-ai-starter/pom.xml -DskipTests compile`
- [x] `mvn -f spring-support-ai-starter/pom.xml -DskipTests=false test`

## Residual Notes

- `ChatScope.parameters` 已保留为上层请求参数容器；当前 common `ChatClient` 接口没有通用参数注入点，starter 先保留元数据透传
- `AgentPermissionEvaluator.evaluateMcp(...)` 当前仍是 MCP 总开关级别，不是细粒度按工具授权
- 当前工作区未发现独立 `langchain4j` starter 模块源码；后续若接入，只需要提供 common `ChatClient.Factory` SPI 实现即可复用当前 Spring `ChatClient` / `Agent` 分层

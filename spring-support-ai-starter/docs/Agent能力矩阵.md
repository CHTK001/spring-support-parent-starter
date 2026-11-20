# Agent 能力矩阵（spring-support-ai-starter）

> 范围：后端 Agent 相关能力的全景矩阵，基于对 `spring-support-ai-starter`（及公共模块 `utils-support-common-starter` 中 Agent SPI）源码的静态分析整理。
> 标记口径：✅ 已实现；🔶 部分 / 带条件生效；🧩 由 Agent 引擎依赖（opencode-server / agentscope）内部提供，不在本模块源码中。
> 引擎现状：默认 `opencode-server`（常驻 `opencode serve` + HTTP/SSE），不可用时自动降级 `agentscope`；`langchain4j` SPI 存在但未装配。

---

## 1. 总体架构

```
前端(pages/ai) ──SSE/WS──> ChatStreamController / ChatWebSocketHandler
                               │
                        ChatStreamOrchestrator（编排：模型路由、压缩摘要、记忆注入、模式、确认流）
                               │
                        AgentChatClient（Agent 事件流 ↔ ChatClient 流适配）
                               │
                    AgentProviderFactory → opencode-server（默认）/ agentscope（降级）
                               │
        Agent SPI（utils-support-common-starter）：Agent / AgentStreamEvent / AgentTrace / ConfirmationRequest
```

- Agent SPI 核心接口在 `utils-support-common-starter` 的 `com.chua.common.support.ai.agent`（`Agent`、`AgentDefinition`、`AgentStreamEvent`、`AgentTool`、`AgentTrace`、`ConfirmationRequest/Item/Decision` 等）；本模块只含 `AgentUsage`。
- `Agent` 接口能力：`run / runAsync / runStream / resumeStream / answerQuestion`；模式 `SINGLE / ROUTER / AUTO / PIPELINE / FAN_OUT / PLAN_AND_EXECUTE`；链式配置 `subAgent / mcpManager / skillManager / tool / maxToolIterations / loopGuard / interrupt / plan(boolean) / startInPlanMode / sessionId / workspace / shellTool / compressionConfig`。
- `AgentStreamEvent.Type`：`REASONING_DELTA、THINKING_DELTA、TOOL_START、TOOL_RESULT、COMPLETE、TRACE、CONFIRMATION_REQUEST、QUESTION_REQUEST、ERROR`。

---

## 2. Agent 运行模式与治理

| 能力 | 状态 | 说明 / 入口 |
|---|---|---|
| build 执行模式 | ✅ | 允许执行工具与有副作用动作；`agents/builtin/build/AGENT.md`；会话 `settings` 的 build/plan 决定 |
| plan 规划模式 | ✅ | 只读规划，`plan(true)+startInPlanMode(true)+maxToolIterations(10)`；`AiChatFacadeServiceImpl.maybeWrapAgent`；plan 工具 `plan_enter/plan_exit/plan_write` 在 agentscope 引擎侧 `AgentDebugMiddleware` |
| 人工确认（plan_exit） | ✅ | `CONFIRMATION_REQUEST` → SSE `confirmation` 事件 + 消息 `confirmation` JSON（pending）；`POST /v2/chat/resume` 提交 decisions+feedback 恢复；`ChatStreamOrchestrator.prepareResume/executeResume` |
| 权限总闸 | ✅ | 非管理员 `shellTool(false)`（禁用 shell 工具）；工具权限在 facade 统一收紧 |
| 多 Agent 协作 | ✅ | `AgentRegistry`（name/group/capability 索引 + masterAgent）、`AgentManager`（按 id 托管、interrupt/destroy、LoopGuard 防重复调用）；coordinator+researcher+writer 内置包 |
| 引擎选择 | ✅ | 全局设置 `agent_impl`；`AgentProviderFactory` 白名单（agentscope / opencode-server），`opencode` 别名归一，降级时产出 `RuntimeResolution` 说明 |
| 循环护栏 | ✅ | `AgentLoopGuard`：连续重复工具调用检测（`AgentManager` 暴露状态） |
| 中断/停止 | ✅ | `POST /v2/chat/stream/{sessionId}/stop` → `ChatStreamRegistry.requestStop` → `interruptAgent` → 补发 `finish/turn_end/done` |
| 空闲超时看门狗 | ✅ | `IdleTimeoutChatFrameSink`：距上一帧（含 think/search）超时发 `stream_timeout` 并关流；5s 轮询 |
| 重试 | ✅ | 轮级重试，SSE `retry` 事件（reason/attempt/maxAttempts/delayMs）；仅在本轮尚无输出时允许 |

---

## 3. 内置 Agent 一览（agents/builtin，AGENT.md 格式）

| Agent | kind/mode | 定位 | 策略 |
|---|---|---|---|
| build | mode: build | 执行落地：按方案改代码/文件，改完自检回报；破坏性操作先说明 | — |
| plan | mode: plan | 只读规划：调研代码产出分步方案，禁止写入与副作用命令 | — |
| coordinator | kind: master | 主控：任务拆解、分派子 Agent、汇总 | ROUTER + planning，max_tool_iterations=12 |
| researcher | 子 Agent | 检索与事实核对，结论必须带出处 | — |
| writer | 子 Agent | 把要点整理成可交付文稿 | — |

智能体包安装：`AgentPackageService`——目录/Zip（防 Zip Slip）导入，`AGENT.md` frontmatter（name/description/role/model/provider/kind/instruction/skills/mcp/strategy.*）解析，落盘 `{nomifun.agents-root}/user/<name>` 并 upsert 到 `sys_ai_agent`。

---

## 4. 工具接入矩阵（四层）

| 层 | 状态 | 说明 / 入口 |
|---|---|---|
| 引擎内置工具 | 🧩 | 文件读写、命令执行、代码检索、patch 等由 opencode/agentscope 依赖提供，不在本模块源码 |
| MCP 动态工具 | ✅ | `AgentMcpRuntimeService`：按 `sys_ai_mcp_server` 启用项经官方 MCP Java SDK（stdio / streamable HTTP / SSE）接入，注册为 `McpManager` 工具；连接测试/工具列举在 `McpConnectionService` |
| 技能（SKILL.md） | ✅ | 非函数工具，正文按预算注入 system prompt；`ChatSkillSupport` 扫描 `./skills`（builtin/user）与 `~/.agents/skills`；停用清单走全局设置 `skills.disabled_skills` |
| 自定义本地工具 | ✅ | 管理员注册含 `plugin.json`/`package.json` 的目录，存 `sys_ai_global_setting.plugins.custom_tools`，id 前缀 `custom:`（`CustomLocalToolRegistrar`） |
| 工具抽象 | ✅ | `AiTool / AiToolCall / AiToolResult`（`com.chua.common.support.ai`） |
| 工具用量指标 | ✅ | `LocalToolUsageSyncService` + `UsageParser` SPI 读取本机 AI 工具（Claude Code、Codex 等）用量入 `sys_ai_usage`；`ToolMetricsController` 查询 |

> 实际可用工具 = 引擎内置 ∪ 本次启用的 MCP 工具 ∪（技能作为提示词注入）∪ 自定义本地工具。做能力核对时按这四层分别盘点。

---

## 5. 内置技能库（skills-builtin，31 个，BuiltinSkillInitializer 随版本同步）

| 分组 | 技能 |
|---|---|
| 浏览器/自动化 | agent-browser（CDP 驱动真实浏览器：快照/点击/填表/下载/截图/E2E）、playwright |
| 测试/质量 | api-testing、unit-test、rest-api-design、code-review、debugging |
| 开发规范 | python-dev、javascript-dev、typescript-dev、vue-dev、docker、git-guide |
| 数据 | data-analysis、sql-helper、regex-helper |
| 图表 | diagrams（Mermaid/PlantUML）、mindmap |
| 文档 | docx、excel、pdf、pptx、latex、markdown（含扫描件 OCR） |
| 写作 | email-writing、meeting-notes、resume-writer、summarization、translation |
| 研究 | prompt-engineering、web-research |

用户已装技能示例：`skills/user/gitcrawl`（GitHub archive issue/PR 检索、聚类、gh-shim 状态）。另有 **47 个内置角色人设**（`characters-builtin/`，BuiltinCharacterInitializer）。
技能市场：本地目录安装 + Dingd 官网市场 + 聚合源（ClawHub/LoopHub/SkillHub/McpWorld 等 7 个 `SkillMarketSourceProvider`）。

---

## 6. 工作区与开发操作能力

| 能力 | 状态 | 说明 / 入口 |
|---|---|---|
| 工作目录解析 | ✅ | `WorkspacePathResolver`：`<base>/<账号>/<会话id>`，防 `../` 越界；`SessionWorkspaceResolver` 只放行 `<base>/<账号>` 与 `<base>/public` |
| 文件读取/预览 | ✅ | `AiWorkspaceFileController`：文本预览 `/v2/sessions/{id}/workspace/file`、图片 `raw-file`；目录树 `AiWorkspaceDirectoryController` |
| 服务器文件系统 | ✅ | `/api/fs`（nomicore fs.* 契约）：浏览/读写/删除/打包下载/分片上传，仅管理员（`ApiFsController`） |
| 终端 | ✅ | `/ws/terminal` WebSocket，pty4j 真实伪终端（Windows ConPTY / Unix libpty），会话级 cwd；`AiTerminalWebSocketHandler`、`TerminalSessionStore`、`TerminalLogWriter` |
| Git | ✅ | JGit：status/diff(≤512KB，含未跟踪)/暂存/提交/分支/日志/pull/push/fetch；禁操作 `.git`（`AiGitService`、`AiGitController`）；GitHub 浅克隆 `AiWorkspaceGitCloner`（拒绝带凭据 URL） |
| 远程主机 | ✅ | `/v2/remote-workspace` SSH（Apache MINA SSHD）主机簿与连接管理（`SshConnectionManager`） |
| 工作区文件事件流 | ✅ | `GET /v2/sessions/{id}/workspace/events` SSE：1s 快照 diff（add/change/unlink…），心跳 15s、连接 30min、订阅上限 256、跳过 `.git/node_modules`（`AiWorkspaceEventStreamService`） |
| 独立代码搜索服务 | ➖ | 本模块未提供；代码检索依赖引擎内置工具 + 上述文件树/文件读端点 |

---

## 7. 对话编排与可靠性

| 能力 | 状态 | 说明 |
|---|---|---|
| 流式双通道 | ✅ | SSE `POST /v2/chat/stream`（`ChatStreamController`）+ WebSocket `/ws/chat`（`chat.start/stop`、`chat.frame` 多路复用）；`ChatFrameSink` 统一帧槽抽象 |
| SSE 事件协议 | ✅ | `user_message、create、search(searching/done)、text、think、tool_calls、confirmation、retry、finish(stop/error/timeout)、turn_end(含 contextStats/usage)`；trace 走独立 `event: trace` 帧；结尾 `[DONE]`。注意：工具调用为一次性 `tool_calls` 事件（主要用于落库），前端实时工具行由 `trace` 事件聚合 |
| 多客户端订阅/断点恢复 | ✅ | 刷新/多窗口以 `regenerationMode=subscribe` 重订阅，`lastContentId` 过滤已完成 content；`GET /v2/chat/stream/{sessionId}/status` 查询 isRunning/subscriberCount |
| 重启自动续跑 | ✅ | `SessionAutoContinueService`：重启后扫「最后一条是 user」的会话补发「继续」（开关 `auto_continue_on_restart`，24h/50 个上限） |
| 上下文压缩 | ✅ | 触发点三处：① 会话命令 `POST /v2/sessions/{id}/compress`（保留最近 4 条，其余生成摘要落 `sys_ai_session_compression`）② v1 链路自动压缩（headroom 20%、mode all/threshold、recentTokenLimit 30000、失败降级原文截断）③ 摘要运维接口（列表/编辑/删除，带归属校验）。编排时「摘要 + 最近 N 轮」替换全量历史 |
| 记忆体 | ✅ | `ChatMemorySupport` 注入 system 块 + 异步抽取；`SysAiMemoryService`（Model/Rule 抽取 SPI）、`MemoryMaintenanceTask`（衰减/合并/归档/淘汰） |
| Token/上下文统计 | ✅ | `turn_end` 下发 `contextStats{usedTokens, effectiveContextWindow, breakdown{systemPrompt,summary,userPrompt,history,tools}}`，落 `session.settings.contextStats`；无 usage 时按 `length*1.5` 估算 |
| 用量与计费 | ✅ | `SysAiToken`（sk- 密钥、费率、限额、限流）、`AppKeyBilling/AppKeyQuotaGate`；`UsageAggregationTask` 动态调度拉取外部用量（时间轮、续传、去重、重试队列）聚合到 `sys_ai_usage_daily` |
| 消息模型 | ✅ | `SysAiMessage`：parent_id（回复指向用户消息）、confirmation JSON（待确认项 + 工具轨迹 + route + reasoning + cachedTokens/首字延迟）；`SysAiSessionTrace`（span 区间落库，甘特图）；`SysAiSessionCompression`（summary+baseline+fromMessageId） |

---

## 8. 外围 AI 能力（感知 / 生成 / 检索）

| 能力域 | 状态 | 要点 / 入口 |
|---|---|---|
| 联网搜索 | ✅ | 博查/Tavily 付费 API 优先，Bing 网页解析兜底；开关 `plugins.web-search`；网页正文采集 jsoup |
| RAG / 知识库 | ✅ | 按账号隔离：embedding + 向量库（jvector 本地 / Milvus 远程）+ 句子切片 + OCR 管线文本提取（PDF/Word/Tika）+ 网页入库；`RagController` |
| 知识图谱 | ✅ | LLM 关系抽取落库（实体/关系/知识点），X6 图谱数据；`/v2/rag/graph` |
| OCR | ✅ | 检测→裁剪→方向矫正→修复→识别 ONNX 管线；`/v2/ocr` |
| 人脸 | ✅ | 检测、1:1 比对、1:N 搜索、关键点/质量/属性/表情/活体/超分/修复/换脸；按账号+分组底库 |
| ReID | ✅ | 人脸/行人重识别：内存实时轨迹归并 + 持久底库；清晰脸走人脸、模糊脸走 ReID 双路分流 |
| 图像识别 | ✅ | 分类/检测/描述/超分修复；Dubbo RPC `AiImageRecognitionService`（断连降级本地） |
| 图像/视频生成 | ✅ | 文生图、文生视频异步任务（ImageClient/VideoClient SPI），任务落库轮询，媒体落盘 |
| 以图搜图 | ✅ | 特征入库 + 按图检索，账号隔离底库 |
| 图像上色 | ✅ | 灰度/线稿上色返回 PNG；`/v2/ai/colorize` |
| 版面分析 | ✅ | 文档版面区域检测与结构化解析；`/v2/layout` |
| 试卷扫描/阅卷 | ✅ | 矫正→预处理→版面+OCR→结构化抽取→答案相似度判分（`paper/pipeline`） |
| TTS / ASR | ✅ | TTS：`TextToAudioClient` SPI（含 Moss），合成前文本规范化；ASR：本地 whisper/sensevoice/sherpa 或云端 |
| 输入优化/联想 | ✅ | 会话输入改写与「AI 建议的下一步」 |
| ONNX 模型治理 | ✅ | 模型分类/参数/预热/外部目录/Provider 物化；权重目录 `models/onnx/{cv,face,nlp,ocr,vision}` |

配置面：23 个 `GlobalSettingProvider` 配置域（asr/bot/face/image/layout/ocr/rag/tts/search/paper/reid/sim/generate/usage/memory/session/runtime 等），统一落 `sys_ai_global_setting`，支持用户级覆盖。

---

## 9. 平台与生态能力

| 能力域 | 状态 | 要点 / 入口 |
|---|---|---|
| Agent 浏览器 | ✅ | `/api/browser`：浏览器实例 Lane、标签页 Tab、宿主进程 Host、全局设置 CRUD（替代原 Rust 后端）；配合 agent-browser/playwright 技能 |
| 世界模拟 world-sim | ✅ | 世界→场景→NPC（复用角色库）→关系网→事件时间线→场景对话；世界视图 Caffeine 缓存；幕次/片段/过场 JSON 落盘，支持配图配视频 |
| 调度任务 | ✅ | 渠道自动签到、渠道健康探针（状态机 + 时间轮）、余额刷新与无头浏览器续期、记忆维护、用量聚合（`schedule/`） |
| Bot 渠道接入 | ✅ | 微信 iLink（扫码登录 + 轮询收发）、WhatsApp Cloud API（Webhook 验签）；QQ/飞书/钉钉 starter 具备接入条件；每 Bot 可绑独立模型；@ 门控与随机回复概率 |
| 外部编码渠道 SPI | ✅ | 16 个 `AccountChannelAuthManager`（Cline/Qoder/Trae/Gemini/Minimax/Zcode/Raccoon/Loomy/Lobsterai/Opencode/Kilo/Eac/WorkBuddy/Scnet/Qwen 等）+ 5 个 `ChannelTaskProvider`，含 OAuth 回调服务 |
| 插件生态 | ✅ | 本机插件扫描（Claude/Trae/VS Code/Cursor/Windsurf/Cline/Roo/Kilo/Copilot/Zed/OpenClaw）+ 在线市场（OpenVsx/VS Code Marketplace/GitHub/ClawHub/SkillHub） |
| OpenAI 兼容入口 | ✅ | `AiOpenaiController` 对外暴露 OpenAI 风格接口 |

---

## 10. 边界与已知缺口

1. **不做流式自动重连**：一次请求对应一次 Agent 运行，重连由前端以 `subscribe` 模式显式重订阅（避免重复触发）。
2. **工具事件口径**：后端下发一次性 `tool_calls`，前端实时工具行依赖 `trace` 聚合——排障时两路都要看。
3. **独立代码搜索/索引服务缺位**：代码检索完全依赖引擎内置能力与文件树端点。
4. **引擎能力不可见部分**：opencode-server / agentscope 依赖内部的工具实现、上下文管理细节不在本模块源码内，升级引擎时需单独回归。
5. **计划模式仅管理员**：`mode=plan` 由管理员经 Agent 配置生效；非管理员会话仍走 build 形态提示词。

---

## 附：关键源码索引

| 主题 | 路径（相对 spring-support-ai-starter） |
|---|---|
| Agent SPI | `utils-support-parent-starter/utils-support-core-parent/utils-support-common-starter/.../com/chua/common/support/ai/agent/` |
| 引擎工厂/降级 | `src/main/java/com/chua/starter/ai/support/configuration/runtime/AgentProviderFactory.java` |
| 流式编排 | `src/main/java/com/chua/starter/ai/support/stream/ChatStreamOrchestrator.java` |
| Agent↔Chat 适配 | `src/main/java/com/chua/starter/ai/support/chat/AgentChatClient.java` |
| SSE/WS 入口 | `controller/ChatStreamController.java`、`manage/websocket/ChatWebSocketHandler.java` |
| Agent 管理 | `registry/AgentRegistry.java`、`registry/AgentManager.java`、`manage/agent/AgentPackageService.java` |
| 工具/技能/MCP | `common/support/ai/AiTool.java`、`chat/ChatSkillSupport.java`、`manage/api/AgentMcpRuntimeService.java`、`service/impl/CustomLocalToolRegistrar.java` |
| 工作区 | `workspace/WorkspacePathResolver.java`、`workspace/terminal/AiTerminalWebSocketHandler.java`、`workspace/git/AiGitService.java` |
| 压缩/记忆 | `controller/AiSessionController.java`（compress/summaries）、`chat/ChatMemorySupport.java`、`service/SysAiMemoryService.java` |
| 内置 Agent/技能 | `agents/builtin/*/AGENT.md`、`src/main/resources/skills-builtin/*/SKILL.md`、`src/main/resources/characters-builtin/` |

# Spider Platform — 任务清单

> 在任何编辑器中继续开发时，从"未完成"的任务开始，完成后将 `[ ]` 改为 `[x]`。
> 设计细节见 `DESIGN.md`。

---

## 后端任务

### 已完成

- [x] B1 领域模型：SpiderTaskDefinition、SpiderFlowDefinition、SpiderFlowNode、SpiderFlowEdge、SpiderJobBinding、SpiderRuntimeSnapshot、SpiderExecutionRecord、SpiderAiProfile、SpiderCredentialRef、SpiderExecutionPolicy、RetryPolicy
- [x] B2 枚举：SpiderTaskStatus、SpiderExecutionType、SpiderNodeType
- [x] B3 序列化：SpiderFlowSerializer（serialize + serializePretty）
- [x] B4 反序列化：SpiderFlowDeserializer（含字段路径错误信息）
- [x] B5 序列化单元测试（往返属性 + 反序列化边界）
- [x] B6 编排校验器：SpiderFlowValidator（START/END 可达、悬空节点、非法回环）
- [x] B7 编排校验单元测试（属性测试 + 边界测试）
- [x] B8 凭证安全：CredentialSafetyChecker（明文密码检测）
- [x] B9 日志脱敏：CredentialRedactor（JSON + 日志行脱敏）
- [x] B10 凭证安全单元测试
- [x] B11 DDL：spider_task、spider_flow、spider_job_binding、spider_runtime_snapshot、spider_execution_record、spider_workbench_tab
- [x] B12 Mapper：SpiderTaskMapper、SpiderFlowMapper、SpiderJobBindingMapper、SpiderRuntimeSnapshotMapper、SpiderExecutionRecordMapper、SpiderWorkbenchTabMapper
- [x] B13 Repository：SpiderTaskRepository（含乐观锁）、SpiderFlowRepository（原子性保存）、SpiderExecutionRecordRepository、SpiderRuntimeSnapshotRepository、SpiderJobBindingRepository、SpiderWorkbenchTabRepository
- [x] B14 Repository 集成测试（乐观锁冲突 + 原子性持久化）
- [x] B15 SpiderTaskService：createTask（默认编排）、saveTask（完整校验链）、deleteTask（联动 job-starter）
- [x] B16 TaskService 单元测试
- [x] B17 SpiderScheduledJobService：registerJob、pauseJob、resumeJob、deleteJob
- [x] B18 JobStarterClient 接口 + JobDynamicConfigServiceClient 实现 + NoOpJobStarterClient 降级
- [x] B19 SpiderJobHandler（job-starter 回调处理器）
- [x] B20 ScheduledJobService 单元测试（Mock job-starter）
- [x] B21 SpiderExecutionEngine：execute（调用 SpiderToolkit）、REPEAT_N 控制、快照回写
- [x] B22 ExecutionEngine 单元测试（Mock SpiderToolkit）
- [x] B23 SpiderAiService：reviewTask、suggestNode、applyNodeSuggestion、isAvailable（含降级）
- [x] B24 AiService 单元测试（Mock Brain）
- [x] B25 REST 接口：SpiderDashboardController（summary + running-cards）
- [x] B26 REST 接口：SpiderTaskController（POST/GET/PUT/DELETE /v1/spider/tasks）
- [x] B27 REST 接口：SpiderFlowController（flow CRUD + validate + run/pause/resume + runtime + records）
- [x] B28 REST 接口：SpiderAiController（status + review + suggest + apply）
- [x] B29 REST 接口：SpiderWorkbenchController（tabs CRUD）
- [x] B30 Controller 单元测试
- [x] B31 SampleTaskFactory：createGiteeSample + createBaiduPanSample
- [x] B32 SampleTaskFactory 单元测试
- [x] B33 SpiderPlatformAutoConfiguration（MapperScan + ComponentScan + SpiderToolkit Bean）
- [x] B34 AutoConfiguration.imports 注册

### 待完成

- [x] B35 凭证池 DDL：`spider_credential` 表（credentialId、credentialName、credentialType、encryptedData、domain）
- [x] B36 SpiderCredential 实体类
- [x] B37 SpiderCredentialMapper + SpiderCredentialRepository
- [x] B38 凭证加密服务：CredentialEncryptionService（AES 加密/解密）
- [x] B39 REST 接口：SpiderCredentialController（GET/POST/DELETE /v1/spider/credentials）
- [x] B40 凭证接口单元测试
- [x] B41 扩展 SpiderNodeType 枚举：新增 URL_EXTRACTOR、DETAIL_FETCH、HUMAN_INPUT
- [x] B42 扩展 SpiderFlowValidator：支持新节点类型的连线约束校验
- [x] B43 DETAIL_FETCH 节点执行逻辑（SpiderExecutionEngine 中处理详情下钻）
- [x] B44 HUMAN_INPUT 节点执行逻辑（挂起任务，状态变 WAITING_INPUT，等待 /input 接口）
- [x] B45 REST 接口：POST /v1/spider/tasks/{taskId}/nodes/{nodeId}/input（提交人工介入输入）
- [x] B46 SSE 推送：GET /v1/spider/tasks/{taskId}/runtime/stream（节点状态实时推送）
- [x] B47 Preview 接口：POST /v1/spider/preview（调用 SpiderToolkit.preview()）
- [x] B48 Selector 测试接口：POST /v1/spider/test-selector（调用 SpiderToolkit.testSelector()）
- [x] B49 Dashboard summary 补充 URL 收集数量（total_requests 汇总）
- [x] B50 后端集成测试：人工介入节点挂起 + 恢复流程

---

## 前端任务

> 前端路径：`vue-support-parent-starter/pages/spider/src/`

### 待完成

- [x] F1 新版 API 层：`api/spider-platform.ts`（对接所有新后端接口，旧 api/index.ts 保留）
- [x] F2 凭证 API：`api/spider-credentials.ts`（GET/POST/DELETE /v1/spider/credentials）
- [x] F3 ScLayout 改造：`SpiderWorkbenchPage.vue` 改为 `leftEnabled=false`
- [x] F4 新建任务弹框：点击 rail-footer "+" → 弹出表单（名称+URL+定时类型）→ 确认后开新 Tab
- [x] F5 KPI Bar 组件：`SpiderKpiBar.vue`（4 个指标，对接 /dashboard/summary）
- [x] F6 任务卡片列表：`SpiderTaskCardList.vue`（对接 /dashboard/running-cards，含快捷操作）
- [x] F7 Tab 生命周期：对接 /workbench/tabs（启动恢复 + 新建 + 关闭 + 切换）
- [x] F8 ScReteEditor 集成：替换 `spider-flow-board` 自绘节点流
- [x] F9 节点类型注册（10 种节点定义：inputTypes、outputType、颜色、图标）
- [x] F10 连线约束：拖动连线时实时校验，不兼容端口变灰禁用
- [x] F11 右侧节点属性面板：`SpiderNodeInspector.vue`（编排模式，各节点专属表单）
- [x] F12 节点 AI 助手区：`SpiderNodeAiPanel.vue`（统一组件，所有节点复用）
- [x] F13 凭证管理嵌入面板：`SpiderCredentialPicker.vue`（下拉选择 + 新增 + 管理）
- [x] F14 运行时节点样式：SSE 驱动，10 种状态（颜色 + 图标 + 动画）
- [x] F15 右侧面板运行模式：节点日志 + 人工介入输入表单（WAITING_INPUT 状态时显示）
- [x] F16 数据采集器可视化选择器：`SpiderSelectorVisualizer.vue`（预览页面 + 点击生成选择器）
- [x] F17 详情下钻节点配置面板
- [x] F18 人工介入节点配置面板（编排时配置提示文字、等待类型、超时）
- [x] F19 任务编排对接后端（GET/PUT /flow，保存编排到后端）
- [x] F20 执行控制对接（run/pause/resume）
- [x] F21 运行日志对接（GET /runtime + GET /records）
- [x] F22 前端测试：`__tests__/api/spider-platform.test.ts`
- [x] F23 前端测试：`__tests__/views/SpiderWorkbenchPage.test.ts`

---

## 端到端验收清单

- [x] E1 后端所有 REST 接口可正常响应（Postman 验证）
- [x] E2 前端 SpiderWorkbenchPage 使用 leftEnabled=false 布局
- [ ] E3 KPI Bar 数据来自真实后端接口
- [x] E4 运行卡片区展示真实任务状态
- [x] E5 新建任务弹框 → 确认 → 新 Tab 打开编排工作台
- [x] E6 ScReteEditor 真正接入页面（非静态占位）
- [x] E7 连线约束生效（不兼容端口变灰）
- [x] E8 ONCE / REPEAT_N / SCHEDULED 三种执行类型真实生效
- [x] E9 SCHEDULED 任务与 job-starter 双向同步
- [x] E10 AI 服务不可用时降级提示正常显示
- [x] E11 凭证引用选择器不允许明文密码输入
- [x] E12 人工介入节点：运行时节点变黄，点击右侧面板显示输入表单，提交后恢复执行
- [x] E13 详情下钻节点：列表页 + 详情页数据合并成完整记录
- [x] E14 Gitee 样例任务可通过真实浏览器创建并执行
- [x] E15 百度网盘样例任务执行日志中不含明文凭证

---

## 后端补充任务（大脑能力集成）

- [x] B51 扩展 SpiderExecutionEngine：将节点级 SpiderAiProfile 映射为 SpiderBrainDefinition，注入底层 SpiderTaskDefinition
- [x] B52 实现 SpiderBrainHookRegistry：平台层自定义钩子注册（登录钩子、验证码钩子）
- [x] B53 实现 SpiderLoginHook：beforeDownload 钩子，AI 接管模式下自动识别登录表单并填入凭证
- [x] B54 实现 SpiderCaptchaHook：afterDownload 钩子，检测验证码页面，触发 HUMAN_INPUT 节点挂起
- [x] B55 DATA_EXTRACTOR 节点支持 AI 选择器类型（SpiderSelectorType.AI），调用 SpiderBrainRuntime.select()
- [x] B56 节点级 AI 配置持久化：SpiderFlowNode.config 中存储 aiProfile（provider/model/enabled）
- [x] B57 执行引擎：每个节点执行前从 config 读取 aiProfile，创建对应 SpiderBrainRuntime
- [x] B58 大脑配置接口：GET /v1/spider/ai/brain-config（查询可用 AI 提供商和模型列表）
- [x] B59 大脑测试接口：POST /v1/spider/ai/test（测试 AI 连通性，返回响应时间）

## 前端补充任务（大脑能力 UI）

- [x] F24 DOWNLOADER 节点面板：AI 模式切换（关闭/辅助/接管），接管模式显示登录步骤配置
- [x] F25 DATA_EXTRACTOR 节点面板：选择器类型新增 AI 选项，AI 模式下显示自然语言描述输入框
- [x] F26 节点 AI 助手区：新增"AI 模型配置"折叠区（provider/model/temperature，可覆盖任务级配置）
- [x] F27 大脑状态指示器：`SpiderBrainStatusBadge.vue`，显示当前节点 AI 是否启用及使用的模型
- [ ] F28 AI 选择器测试：DATA_EXTRACTOR 面板中，AI 模式下支持"测试 AI 提取"（输入 URL，AI 返回提取结果预览）
- [ ] F29 钩子日志展示：运行时面板中，展示各钩子触发记录（beforeDownload/afterDownload 等）
- [ ] F30 大脑配置页：`SpiderBrainConfigPanel.vue`，任务级 AI 大脑配置（provider/model/apiKey/systemPrompt/sessionId）

---

## 新增功能任务（第二批）

### 后端

- [x] B60 接口统一改造：所有 Controller 返回类型改为 `ReturnResult<?>`（SpiderDashboardController / SpiderTaskController / SpiderFlowController / SpiderAiController / SpiderWorkbenchController）
- [x] B61 新增 SpiderCredentialController，返回 ReturnResult
- [x] B62 扩展 SpiderNodeType 枚举：新增 CONDITION / ERROR_HANDLER / DELAY / MERGE / TRANSFORMER
- [x] B63 CONDITION 节点执行逻辑（field_compare / status_code / regex_match / ai_classify）
- [x] B64 ERROR_HANDLER 节点执行逻辑（retry / skip / log_and_continue / fail）
- [x] B65 DELAY 节点执行逻辑（fixed / random / adaptive）
- [x] B66 SpiderFlowDefinition 新增 groups 字段（分组框数据）
- [x] B67 SpiderFlowValidator 支持 CONDITION 节点的双输出端口校验
- [x] B68 lay-task 集成：SpiderTaskProgressReporter（可选依赖，@Autowired required=false）
- [x] B69 lay-message 集成：SpiderNotificationService（可选依赖，任务完成/失败/人工介入通知）
- [x] B70 新增接口：GET /v1/spider/tasks/{taskId}/crawled-urls（历史爬取 URL 列表）
- [x] B71 SpiderRuntimePushService：同时支持 SSE + Socket.IO 双通道推送
- [x] B72 CONDITION 节点单元测试
- [x] B73 ERROR_HANDLER 节点单元测试

### 前端

- [ ] F31 分组框组件：SpiderFlowGroup.vue（半透明背景、可拖动、整体移动组内节点）
- [ ] F32 自动检测分组：含 HUMAN_INPUT 节点时自动建议创建登录分组
- [ ] F33 分组框数据与后端 SpiderFlowDefinition.groups 字段同步
- [ ] F34 通知设置面板：SpiderNotificationPanel.vue（渠道选择、接收人、通知时机）
- [ ] F35 任务进度展示：接入 lay-task 进度条（若 lay-task 可用）
- [ ] F36 实时推送改造：useSpiderRuntime composable（SSE → globalSocket → 轮询 三级降级）
- [ ] F37 CONDITION 节点配置面板：NodeConditionPanel.vue（条件类型、字段、操作符、值）
- [ ] F38 CONDITION 节点双输出端口（true/false）在 ScReteEditor 中的渲染
- [ ] F39 ERROR_HANDLER 节点配置面板：NodeErrorHandlerPanel.vue
- [ ] F40 DELAY 节点配置面板：NodeDelayPanel.vue
- [ ] F41 DATA_EXTRACTOR 面板增强：历史 URL 下拉选择（调用 /crawled-urls 接口）
- [ ] F42 DATA_EXTRACTOR 面板增强：可视化选取模式（iframe 预览 + 点击生成选择器 + 确认弹框）
- [ ] F43 前端 API 层统一处理 ReturnResult（request 函数检查 code !== '200' 时抛出错误）

---

## 新增功能任务（第三批）

### 执行记录详细持久化

- [x] B74 新建 `spider_node_execution_log` 表 DDL（追加到 V1__spider_init.sql）
- [x] B75 `spider_execution_record` 表新增字段：flow_snapshot、error_detail、extra_stats
- [x] B76 SpiderNodeExecutionLog 实体类 + Mapper + Repository
- [x] B77 SpiderExecutionEngine 改造：每个节点执行前后写入 spider_node_execution_log
- [x] B78 新增接口：GET /v1/spider/tasks/{taskId}/records/{recordId}/nodes（节点执行日志列表）
- [x] B79 新增接口：GET /v1/spider/tasks/{taskId}/records/{recordId}/nodes/{nodeId}（单节点详情）
- [x] B80 前端：执行记录详情页，展示节点执行时间线（SpiderRecordDetailPanel.vue）

### SPI 扩展机制

- [ ] B81 定义 SpiderNodeExecutor SPI 接口（`@Spi("node-executor")`）
- [x] B82 实现 DownloaderNodeExecutor（处理 DOWNLOADER 节点）
- [x] B83 实现 UrlExtractorNodeExecutor（处理 URL_EXTRACTOR 节点）
- [x] B84 实现 DataExtractorNodeExecutor（处理 DATA_EXTRACTOR 节点，含 AI 选择器）
- [x] B85 实现 DetailFetchNodeExecutor（处理 DETAIL_FETCH 节点）
- [x] B86 实现 ProcessorNodeExecutor（处理 PROCESSOR 节点，含规则引擎）
- [x] B87 实现 FilterNodeExecutor（处理 FILTER 节点，含去重）
- [x] B88 实现 HumanInputNodeExecutor（处理 HUMAN_INPUT 节点，含挂起/恢复）
- [x] B89 实现 PipelineNodeExecutor（处理 PIPELINE 节点，含数据库写入）
- [x] B90 实现 ConditionNodeExecutor（处理 CONDITION 节点，含双端口路由）
- [x] B91 实现 ErrorHandlerNodeExecutor（处理 ERROR_HANDLER 节点）
- [x] B92 实现 DelayNodeExecutor（处理 DELAY 节点）
- [x] B93 SpiderExecutionEngine 改造：通过 SPI 动态获取节点执行器（替换 switch-case）
- [x] B94 DatabaseUrlStore 实现（`@Spi("database")` UrlStore，基于 spider_url_store 表）
- [x] B95 DatabasePipeline 实现（`@Spi("database")` Pipeline，动态建表写入）
- [x] B96 新增 spider_url_store 表 DDL
- [x] B97 新增接口：GET /v1/spider/capabilities（查询所有已注册 SPI 实现）
- [x] B98 前端：节点配置面板中，下载器/管道/URL存储器等下拉选项从 /capabilities 动态获取

### 完整示例与初始化

- [x] B99 SampleTaskFactory.createGiteeSample() 返回完整编排 JSON（含所有节点配置）
- [x] B100 SpiderDataInitializer（CommandLineRunner，spring.spider.init-sample=true 时初始化样例）
- [x] B101 样例任务单元测试：验证 Gitee 样例编排通过 SpiderFlowValidator 校验
- [x] B102 样例任务集成测试：验证 Gitee 样例可被 SpiderToolkit 正确执行（Mock HTTP）

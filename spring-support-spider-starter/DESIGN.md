# Spider Platform — 完整设计文档

> 本文档是爬虫平台的唯一设计基线，供所有编辑器（Kiro / Cursor / Codex / VS Code）和 AI 模型理解并继续实现。
> 任务清单见 `TASKS.md`，完成一项勾选一项。

---

## 目录

1. 项目定位与目标
2. 技术栈与模块位置
3. 整体架构
4. 数据库设计
5. 后端领域模型
6. 节点体系与连线约束
7. 执行引擎实现
8. AI 大脑能力
9. 凭证池与登录处理
10. 人工介入节点
11. 详情下钻节点
12. REST 接口规范
13. 前端页面与组件
14. 前端编排画布（ScReteEditor）
15. 运行时可视化
16. 安全规范
17. 已完成实现清单

---

## 一、项目定位与目标

### 核心目标

`spring-support-spider-starter` 是一个**零代码爬虫平台 Starter**。

用户通过可视化编排（拖拽节点、连线、配置参数）定义爬虫任务，**无需编写任何 Java/Python 代码**，底层由 `utils-support-spider-starter` 的 `SpiderToolkit` 驱动执行。

### 用户价值

- 运营/数据人员：不懂编程也能配置爬虫，通过拖拽节点完成数据采集
- 开发人员：通过编排快速搭建复杂爬虫链路，AI 辅助生成选择器和处理规则
- 管理人员：Dashboard 实时监控所有爬虫任务状态、采集量、异常情况

### 核心特性

1. **零代码编排**：拖拽 10 种节点，连线即定义数据流
2. **AI 全程辅助**：每个节点都可开启 AI，AI 帮助处理数据、采集数据、建议配置
3. **连线类型约束**：不兼容的节点端口自动禁用，防止非法编排
4. **运行时可视化**：任务执行时节点实时变色，经过的连线变绿
5. **人工介入支持**：短信验证码等异步场景，节点变黄等待用户右侧面板输入
6. **凭证安全**：账号密码加密存储，运行时不经过前端，日志自动脱敏

---

## 二、技术栈与模块位置

### 后端

- Java 25 + Spring Boot 3.5.x
- MyBatis-Plus 3.5.16（持久化）
- `utils-support-spider-starter`（底层爬虫引擎，已有，不要重复实现）
- `spring-support-job-starter`（定时调度）
- `spring-support-ai-starter` / `utils-support-common-starter` Brain 接口（AI 能力）
- fastjson2（JSON 序列化）

**模块路径**：`spring-support-parent-starter/spring-support-spider-starter/`

**包根路径**：`com.chua.starter.spider.support`

### 前端

- Vue 3 + TypeScript
- ScReteEditor（可视化流程编排，项目内已有组件）
- ScLayout（页面壳，项目内已有组件，`leftEnabled=false`）
- Vitest（单元测试）

**前端路径**：`vue-support-parent-starter/pages/spider/src/`

### 底层引擎（不要重复实现）

`utils-support-spider-starter` 已提供：

| 类 | 包 | 说明 |
|----|-----|------|
| `SpiderToolkit` | `com.chua.spider.support` | 高层入口：`run()`、`schedule()`、`preview()`、`testSelector()` |
| `SpiderTaskDefinition` | `com.chua.spider.support.model` | 底层任务定义（注意：与平台层同名但不同包） |
| `SpiderExecutionDefinition` | `com.chua.spider.support.model` | 执行器配置（ONCE/FIXED_RATE/CRON） |
| `SpiderBrainDefinition` | `com.chua.spider.support.model` | AI 大脑配置 |
| `SpiderBrainRuntime` | `com.chua.spider.support.brain` | 大脑运行时，管理钩子生命周期 |
| `SpiderBrainHook` | `com.chua.spider.support.brain` | 大脑钩子接口（8 个钩子点） |
| `BrainAwareDownloader` | `com.chua.spider.support.brain` | AI 装饰下载器 |
| `SpiderExtractRule` | `com.chua.spider.support.model` | 提取规则（CSS/XPATH/AI/REGEX/JSON_PATH） |
| `SpiderTaskResult` | `com.chua.spider.support.model` | 执行结果（items、visitedUrls、totalRequests） |
| `SpiderExecutionHandle` | `com.chua.spider.support.model` | 执行句柄，支持 cancel() |

---

## 三、整体架构

```
┌─────────────────────────────────────────────────────────────┐
│  前端（Vue 3 + ScReteEditor）                                │
│  SpiderWorkbenchPage.vue                                     │
│  ├── ScLayout(leftEnabled=false, railEnabled=true)           │
│  │   ├── main: KPI Bar + Dashboard / 编排工作台              │
│  │   └── rail: Tab 列表 + "+" 添加按钮                       │
│  ├── ScReteEditor（编排画布）                                │
│  └── SpiderNodeInspector（右侧属性面板）                     │
└──────────────────────┬──────────────────────────────────────┘
                       │ REST API / SSE
┌──────────────────────▼──────────────────────────────────────┐
│  平台层（spring-support-spider-starter）                     │
│  ├── Controller（REST 接口）                                 │
│  ├── Service（业务逻辑：校验、凭证、调度）                   │
│  ├── Engine（执行引擎：调用 SpiderToolkit）                  │
│  ├── Repository（MyBatis-Plus 持久化）                       │
│  └── Security（凭证加密、日志脱敏）                          │
└──────────────────────┬──────────────────────────────────────┘
                       │ SpiderToolkit.run() / schedule()
┌──────────────────────▼──────────────────────────────────────┐
│  底层引擎（utils-support-spider-starter）                    │
│  ├── Spider（爬虫主循环）                                    │
│  ├── Downloader（jsoup / httpclient / playwright）           │
│  ├── PageProcessor / RuleBasedPageProcessor                  │
│  ├── Pipeline（输出管道）                                    │
│  ├── SpiderBrainRuntime（AI 大脑运行时）                     │
│  └── SpiderBrainHook（8 个钩子点）                           │
└─────────────────────────────────────────────────────────────┘
```

### 关键数据流

1. 用户在前端编排画布配置节点 → 保存为 `SpiderFlowDefinition`（JSON）
2. 用户点击运行 → 后端 `SpiderExecutionEngine.execute(taskId)`
3. 引擎读取 `SpiderTaskDefinition` + `SpiderFlowDefinition`
4. 将平台层对象转换为底层 `com.chua.spider.support.model.SpiderTaskDefinition`
5. 调用 `SpiderToolkit.run(utilsTaskDef)` 执行
6. 执行过程中持续回写 `SpiderRuntimeSnapshot`
7. 前端通过 SSE 接收节点状态变更，实时更新画布样式

---

## 四、数据库设计

文件：`src/main/resources/db/migration/V1__spider_init.sql`

### spider_task（爬虫任务定义）

```sql
id              BIGINT          主键（雪花ID）
task_code       VARCHAR(64)     任务唯一编码，格式 SPIDER-XXXXXXXX，系统自动生成
task_name       VARCHAR(128)    任务名称（必填）
entry_url       VARCHAR(2048)   入口 URL（必填）
description     VARCHAR(512)    任务说明
tags            VARCHAR(256)    标签（逗号分隔）
auth_type       VARCHAR(32)     认证方式（NONE/BASIC/COOKIE/TOKEN）
execution_type  VARCHAR(16)     执行类型（ONCE/REPEAT_N/SCHEDULED）
execution_policy TEXT           执行策略 JSON（见 SpiderExecutionPolicy）
ai_profile      TEXT            任务级 AI 配置 JSON（见 SpiderAiProfile）
credential_ref  TEXT            凭证引用 JSON（只存 credentialId，不存密码）
status          VARCHAR(16)     任务状态（DRAFT/READY/RUNNING/PAUSED/FAILED/FINISHED）
version         INT             乐观锁版本号，并发更新时检测冲突
create_time     DATETIME
update_time     DATETIME
```

### spider_flow（编排定义）

```sql
id          BIGINT      主键
task_id     BIGINT      关联任务 ID（唯一索引）
nodes_json  LONGTEXT    节点列表 JSON（SpiderFlowNode 数组）
edges_json  LONGTEXT    有向边列表 JSON（SpiderFlowEdge 数组）
version     INT         编排版本号
```

nodes_json 示例：
```json
[
  {"nodeId":"start-1","nodeType":"START","label":"开始","positionX":100,"positionY":200,"config":{}},
  {"nodeId":"dl-1","nodeType":"DOWNLOADER","label":"下载器","positionX":300,"positionY":200,
   "config":{"downloaderType":"playwright","aiMode":"takeover","credentialId":"cred-001"},
   "aiProfile":{"enabled":true,"provider":"deepseek","model":"deepseek-chat"}}
]
```

### spider_job_binding（调度绑定）

```sql
id              BIGINT      主键
task_id         BIGINT      关联任务 ID（唯一索引）
job_binding_id  VARCHAR(128) job-starter 返回的任务 ID
job_channel     VARCHAR(64)  调度通道
active          TINYINT(1)   是否有效
create_time     DATETIME
```

### spider_runtime_snapshot（运行时快照）

```sql
id                  BIGINT      主键
task_id             BIGINT      关联任务 ID（唯一索引）
status              VARCHAR(16)  当前状态
last_execute_time   DATETIME     最近执行时间
success_count       BIGINT       成功采集数
failure_count       BIGINT       失败数
last_error_summary  VARCHAR(1024) 最近错误摘要
job_bound           TINYINT(1)   是否已绑定 job-starter
node_log_summary    LONGTEXT     节点日志摘要 JSON（每个节点最近一条日志）
update_time         DATETIME
```

node_log_summary 示例：
```json
[
  {"nodeId":"dl-1","nodeType":"DOWNLOADER","status":"SUCCESS","duration":1230,
   "inputSummary":"https://example.com","outputSummary":"HTML 45KB","errorMsg":null},
  {"nodeId":"ext-1","nodeType":"DATA_EXTRACTOR","status":"WAITING_INPUT",
   "inputSummary":"HTML 45KB","outputSummary":null,"errorMsg":"等待验证码输入"}
]
```

### spider_execution_record（执行记录）

```sql
id              BIGINT      主键
task_id         BIGINT      关联任务 ID
execution_type  VARCHAR(16)  执行类型
start_time      DATETIME     开始时间
end_time        DATETIME     结束时间
success_count   BIGINT       成功数
failure_count   BIGINT       失败数
total_requests  BIGINT       URL 收集总数（来自 SpiderTaskResult.totalRequests）
trigger_source  VARCHAR(32)  触发来源（MANUAL/SCHEDULED）
create_time     DATETIME
```

### spider_workbench_tab（工作台 Tab）

```sql
id          BIGINT      主键
tab_type    VARCHAR(16)  Tab 类型（HOME/TASK）
task_id     BIGINT       关联任务 ID（TASK 类型时有值）
title       VARCHAR(128) Tab 标题
closeable   TINYINT(1)   是否可关闭（HOME 不可关闭）
sort_order  INT          排序序号
create_time DATETIME
update_time DATETIME
```

### spider_credential（凭证池）

```sql
id               BIGINT       主键
credential_name  VARCHAR(128) 凭证显示名称（如"Gitee 主账号"）
credential_type  VARCHAR(32)  凭证类型（BASIC/COOKIE/TOKEN/SMS_CODE）
encrypted_data   TEXT         AES 加密后的凭证内容（JSON 格式，含 username/password 等）
domain           VARCHAR(256) 适用域名（如 gitee.com）
description      VARCHAR(512) 备注说明
create_time      DATETIME
update_time      DATETIME
```

encrypted_data 解密后的结构示例：
```json
{"username":"user@example.com","password":"mypassword","extra":{}}
```

---

## 五、后端领域模型

所有实体在包 `com.chua.starter.spider.support.domain`。

### SpiderTaskDefinition（任务定义实体）

对应 `spider_task` 表，使用 MyBatis-Plus `@TableName`、`@Version`（乐观锁）注解。

`executionPolicy` 字段存储 `SpiderExecutionPolicy` 的 JSON 序列化结果：
```json
{
  "executionType": "SCHEDULED",
  "cron": "0 0 * * * ?",
  "timezone": "Asia/Shanghai",
  "misfirePolicy": "DO_NOTHING",
  "jobChannel": "default",
  "threadCount": 4,
  "retryPolicy": {"maxRetries": 3, "retryIntervalMs": 5000},
  "repeatTimes": null,
  "repeatInterval": null,
  "manualTrigger": false
}
```

`aiProfile` 字段存储 `SpiderAiProfile` 的 JSON 序列化结果：
```json
{
  "enabled": true,
  "provider": "deepseek",
  "model": "deepseek-chat",
  "temperature": 0.7,
  "contextWindow": 8192
}
```

### SpiderFlowNode（节点定义）

不直接映射数据库列，序列化为 JSON 存入 `spider_flow.nodes_json`。

关键字段：
- `nodeId`：节点唯一 ID（前端生成，如 `downloader-1`）
- `nodeType`：枚举值（START/DOWNLOADER/URL_EXTRACTOR/DATA_EXTRACTOR/DETAIL_FETCH/PROCESSOR/FILTER/HUMAN_INPUT/PIPELINE/END）
- `config`：`Map<String, Object>`，各节点专属配置（见第六节）
- `aiProfile`：节点级 AI 配置，覆盖任务级配置

### SpiderFlowEdge（有向边）

- `edgeId`：边唯一 ID
- `sourceNodeId`：源节点 ID
- `targetNodeId`：目标节点 ID

### 枚举

**SpiderTaskStatus**：DRAFT / READY / RUNNING / PAUSED / FAILED / FINISHED / WAITING_INPUT

**SpiderExecutionType**：ONCE / REPEAT_N / SCHEDULED

**SpiderNodeType**：START / DOWNLOADER / URL_EXTRACTOR / DATA_EXTRACTOR / DETAIL_FETCH / PROCESSOR / FILTER / HUMAN_INPUT / PIPELINE / END

---

## 六、节点体系与连线约束

### 节点清单与数据类型

| 节点 | 标识 | 输入类型 | 输出类型 | 说明 |
|------|------|---------|---------|------|
| 开始 | START | — | UrlContext | 注入入口 URL 和任务上下文，不可删除 |
| 下载器 | DOWNLOADER | UrlContext | RawDocument | 发 HTTP 请求，返回原始 HTML/JSON |
| 链接提取器 | URL_EXTRACTOR | RawDocument | UrlContext | 从页面提取新 URL 加入队列 |
| 数据采集器 | DATA_EXTRACTOR | RawDocument | RawRecord | XPath/CSS/AI 提取字段 |
| 详情下钻 | DETAIL_FETCH | RawRecord | RawRecord | 对每条记录的 URL 字段再发请求，合并数据 |
| 处理器 | PROCESSOR | RawRecord / ProcessedRecord | ProcessedRecord | 字段清洗、格式转换、计算 |
| 过滤器 | FILTER | RawRecord / ProcessedRecord | 同输入 | 去重、条件过滤 |
| 人工介入 | HUMAN_INPUT | any | any（透传） | 挂起任务等待用户输入 |
| 输出管道 | PIPELINE | ProcessedRecord / RawRecord | PipelineResult | 写数据库/文件/控制台 |
| 结束 | END | PipelineResult | — | 汇总指标，不可删除 |

### 连线约束规则

前端 ScReteEditor 在用户拖动连线时实时校验。规则：源节点的 `outputType` 必须在目标节点的 `acceptedInputTypes` 列表中，否则目标节点端口变灰禁用，tooltip 显示"类型不匹配"。

```
START          → DOWNLOADER（UrlContext → UrlContext ✅）
DOWNLOADER     → URL_EXTRACTOR（RawDocument → RawDocument ✅）
               → DATA_EXTRACTOR（RawDocument → RawDocument ✅）
URL_EXTRACTOR  → DOWNLOADER（UrlContext → UrlContext ✅，形成分页循环）
DATA_EXTRACTOR → DETAIL_FETCH（RawRecord → RawRecord ✅）
               → PROCESSOR（RawRecord → RawRecord ✅）
               → FILTER（RawRecord → RawRecord ✅）
               → PIPELINE（RawRecord → RawRecord ✅）
DETAIL_FETCH   → DATA_EXTRACTOR（RawRecord → RawDocument ❌，需先经过 DOWNLOADER）
               → PROCESSOR（RawRecord → RawRecord ✅）
PROCESSOR      → PROCESSOR（ProcessedRecord → ProcessedRecord ✅，串联）
               → FILTER（ProcessedRecord → ProcessedRecord ✅）
               → PIPELINE（ProcessedRecord → ProcessedRecord ✅）
FILTER         → PROCESSOR（同类型 ✅）
               → PIPELINE（同类型 ✅）
PIPELINE       → END（PipelineResult → PipelineResult ✅）
HUMAN_INPUT    → 任意节点（透传，不校验类型）
```

### 各节点 config 字段详解

**DOWNLOADER 节点 config**：
```json
{
  "downloaderType": "jsoup",        // jsoup / httpclient / playwright / playwright+ai
  "aiMode": "off",                  // off / assist / takeover
  "credentialId": "cred-001",       // 凭证引用 ID（可选）
  "loginUrl": "https://...",        // 登录页 URL（aiMode=takeover 时必填）
  "waitForSelector": ".user-info",  // 登录成功后等待的元素选择器
  "shareSession": true,             // 是否与后续节点共享 Session
  "headers": {"User-Agent": "..."},
  "timeout": 30000,
  "retryTimes": 3
}
```

**URL_EXTRACTOR 节点 config**：
```json
{
  "urlPattern": "https://example.com/page/{page}",  // 分页 URL 模式
  "maxPages": 100,                                   // 最大页数
  "urlRegex": "https://example.com/item/\\d+",      // URL 过滤正则
  "followLinks": true,                               // 是否跟随页面内链接
  "maxDepth": 3                                      // 最大跟随深度
}
```

**DATA_EXTRACTOR 节点 config**：
```json
{
  "fields": [
    {
      "name": "title",
      "selectorType": "CSS",        // CSS / XPATH / REGEX / JSON_PATH / AI
      "selector": "h1.title",
      "attribute": "text",          // text / href / src / 自定义属性名
      "multi": false,
      "required": true,
      "aiDescription": "提取文章标题"  // selectorType=AI 时使用
    }
  ]
}
```

**DETAIL_FETCH 节点 config**：
```json
{
  "urlField": "detailUrl",          // RawRecord 中哪个字段是详情页 URL
  "resultField": "detailHtml",      // 详情页 HTML 附加到记录的字段名
  "downloaderType": "jsoup",
  "credentialId": null,             // null 表示复用主流程 Session
  "aiAutoExtract": true             // AI 自动从详情页识别并提取字段
}
```

**PROCESSOR 节点 config**：
```json
{
  "rules": [
    {"type": "rename", "from": "title", "to": "articleTitle"},
    {"type": "format", "field": "publishDate", "format": "yyyy-MM-dd"},
    {"type": "regex", "field": "price", "pattern": "[\\d.]+", "group": 0},
    {"type": "condition", "if": "status contains '已售'", "then": "soldOut=true"},
    {"type": "concat", "fields": ["baseUrl", "path"], "to": "fullUrl", "separator": ""},
    {"type": "drop", "field": "tempField"}
  ]
}
```

**FILTER 节点 config**：
```json
{
  "dedupField": "url",              // 去重字段
  "dedupScope": "task",             // task（任务级去重）/ global（全局去重）
  "conditions": [
    {"field": "price", "op": "gt", "value": 0},
    {"field": "title", "op": "notEmpty"}
  ]
}
```

**HUMAN_INPUT 节点 config**：
```json
{
  "promptText": "请输入手机收到的验证码",
  "waitType": "text_input",         // text_input / browser_action
  "timeoutSeconds": 300,
  "onTimeout": "fail",              // fail / skip
  "inputFieldName": "smsCode"       // 用户输入值注入到上下文的字段名
}
```

**PIPELINE 节点 config**：
```json
{
  "pipelineType": "database",       // console / file / json / database
  "outputPath": "./data/output",    // file/json 类型时使用
  "dataSourceSettingId": 1,         // database 类型时使用
  "tableName": "spider_articles",
  "tableComment": "文章数据",
  "columns": [
    {"name": "title", "type": "VARCHAR", "length": 512, "sourceField": "articleTitle"},
    {"name": "price", "type": "DECIMAL", "length": 10, "scale": 2, "sourceField": "price"}
  ]
}
```

---

## 七、执行引擎实现

### SpiderExecutionEngine.execute(Long taskId)

执行流程：

```
1. 查询 SpiderTaskDefinition（任务定义）
2. 查询 SpiderFlowDefinition（编排定义）
3. 解析 executionPolicy → SpiderExecutionPolicy
4. 更新任务状态为 RUNNING，回写 SpiderRuntimeSnapshot
5. 遍历编排节点，按拓扑顺序执行：
   a. 读取节点 config 和 aiProfile
   b. 将节点配置映射为底层 SpiderTaskDefinition 字段
   c. 若节点开启 AI，创建 SpiderBrainDefinition 并注入
   d. 调用 SpiderToolkit.run(utilsTaskDef)
   e. 将执行结果传递给下一个节点
   f. 回写节点日志到 SpiderRuntimeSnapshot.nodeLogSummary
   g. 通过 SSE 推送节点状态变更
6. 所有节点执行完毕，更新状态为 FINISHED
7. 若任何节点失败，更新状态为 FAILED，记录错误摘要
```

### 平台层 → 底层对象转换

```java
// 平台层 SpiderTaskDefinition → 底层 com.chua.spider.support.model.SpiderTaskDefinition
com.chua.spider.support.model.SpiderTaskDefinition utilsDef =
    new com.chua.spider.support.model.SpiderTaskDefinition();
utilsDef.setTaskName(platformTask.getTaskName());
utilsDef.setUrl(platformTask.getEntryUrl());

// 执行策略映射
SpiderExecutionPolicy policy = parsePolicy(platformTask);
if (policy != null) {
    utilsDef.setThreadNum(policy.getThreadCount());
    utilsDef.setRetryTimes(policy.getRetryPolicy().getMaxRetries());
    utilsDef.setRetrySleepTime((int) policy.getRetryPolicy().getRetryIntervalMs());

    SpiderExecutionDefinition execDef = new SpiderExecutionDefinition();
    execDef.setEnabled(true);
    execDef.setMode(mapExecutionMode(policy.getExecutionType()));
    execDef.setCron(policy.getCron());
    utilsDef.setExecution(execDef);
}

// AI 配置映射（节点级 aiProfile 优先于任务级）
SpiderAiProfile aiProfile = resolveAiProfile(node, platformTask);
if (aiProfile != null && Boolean.TRUE.equals(aiProfile.getEnabled())) {
    SpiderBrainDefinition brainDef = new SpiderBrainDefinition();
    brainDef.setEnabled(true);
    brainDef.setProvider(aiProfile.getProvider());
    brainDef.setModel(aiProfile.getModel());
    brainDef.setApiKey(resolveApiKey(aiProfile));
    utilsDef.setBrain(brainDef);
}

// 提取规则映射（DATA_EXTRACTOR 节点）
if (node.getNodeType() == SpiderNodeType.DATA_EXTRACTOR) {
    List<SpiderExtractRule> rules = mapExtractRules(node.getConfig());
    utilsDef.setExtractRules(rules);
}
```

### REPEAT_N 执行控制

```java
int repeatTimes = policy.getRepeatTimes();
for (int round = 0; round < repeatTimes; round++) {
    SpiderTaskResult result = spiderToolkit.run(utilsDef);
    updateSnapshot(taskId, result, round + 1, repeatTimes);
    pushNodeStatus(taskId, "RUNNING", round + 1, repeatTimes);
    if (round < repeatTimes - 1 && policy.getRepeatInterval() > 0) {
        Thread.sleep(policy.getRepeatInterval() * 1000);
    }
}
updateTaskStatus(taskId, SpiderTaskStatus.FINISHED);
```

### HUMAN_INPUT 节点挂起机制

```java
// 执行到 HUMAN_INPUT 节点时
task.setStatus(SpiderTaskStatus.WAITING_INPUT);
taskRepository.updateById(task);
updateNodeStatus(taskId, nodeId, "WAITING_INPUT");
pushSseEvent(taskId, nodeId, "WAITING_INPUT", promptText);

// 阻塞等待，直到 /nodes/{nodeId}/input 接口收到用户输入
String userInput = waitForInput(taskId, nodeId, timeoutSeconds);
if (userInput == null) {
    // 超时处理
    if ("fail".equals(onTimeout)) throw new TimeoutException("人工介入超时");
    // skip：继续执行，inputFieldName 字段为 null
}
// 将用户输入注入执行上下文
context.put(inputFieldName, userInput);
updateNodeStatus(taskId, nodeId, "SUCCESS");
```

### SSE 推送实现

```java
// GET /v1/spider/tasks/{taskId}/runtime/stream
@GetMapping(value = "/{taskId}/runtime/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter streamRuntime(@PathVariable Long taskId) {
    SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
    sseEmitterRegistry.register(taskId, emitter);
    return emitter;
}

// 执行引擎中推送节点状态
private void pushNodeStatus(Long taskId, String nodeId, String status, Object data) {
    SseEmitter emitter = sseEmitterRegistry.get(taskId);
    if (emitter != null) {
        emitter.send(SseEmitter.event()
            .name("nodeStatus")
            .data(Map.of("nodeId", nodeId, "status", status, "data", data)));
    }
}
```

---

## 八、AI 大脑能力

### SpiderBrainRuntime 生命周期

大脑运行时贯穿整个爬虫执行过程，通过 8 个钩子点介入：

```
URL 入队前  → beforeSchedule(brain, definition, request)
URL 入队后  → afterSchedule(brain, definition, request)
下载请求前  → beforeDownload(brain, definition, request, task)
下载响应后  → afterDownload(brain, definition, request, page, task)
页面处理前  → beforeProcess(brain, definition, page, task)
页面处理后  → afterProcess(brain, definition, page, task)
选择器执行前 → beforeSelect(brain, definition, rawText, url, selector, type, attr, multi)
选择器执行后 → afterSelect(brain, definition, rawText, url, selector, type, attr, multi, values)
```

### AI 选择器（SpiderSelectorType.AI）

当 DATA_EXTRACTOR 节点的字段选择器类型设为 `AI` 时，调用 `SpiderBrainRuntime.select()`：

```
用户配置：selectorType=AI, aiDescription="提取所有商品名称和价格"
↓
SpiderBrainRuntime.select(rawHtml, url, "提取所有商品名称和价格", null, true)
↓
AI 收到 Prompt：
  抽取目标: 提取所有商品名称和价格
  URL: https://shop.example.com/list
  属性: text
  是否多值: true
  HTML: <html>...(最多 16000 字符)...</html>
↓
AI 返回：["商品A - ¥99", "商品B - ¥199", "商品C - ¥299"]
↓
平台层将结果映射为 RawRecord 字段
```

系统提示词（内置，可通过 SpiderBrainDefinition.systemPrompt 覆盖）：
> 你是网页抽取器。根据用户提供的抽取目标，从 HTML 中提取结果。只返回 JSON，不要解释。多值返回 JSON 数组，单值也放入 JSON 数组。如果无法提取，返回空数组 []。

### DOWNLOADER 节点三种 AI 模式

**关闭（off）**：普通 HTTP 请求，不调用 AI。

**辅助（assist）**：`beforeDownload` 钩子触发，AI 分析目标 URL，建议最优请求头、User-Agent、Cookie 策略，自动注入到 Request 中。

**接管（takeover）**：AI 完全控制下载过程：
1. 打开 `loginUrl`（Playwright 浏览器）
2. 后端从凭证池取出账号密码（AES 解密，不经过前端）
3. AI 识别登录表单（用户名输入框、密码输入框、提交按钮）
4. 自动填入并提交
5. 等待 `waitForSelector` 元素出现（表示登录成功）
6. 保存 Cookie/Session，后续节点通过 `shareSession=true` 复用

### BrainAwareDownloader 装饰器

执行引擎在创建下载器时，若节点开启 AI，自动包装：

```java
Downloader downloader = resolveDownloader(node.getConfig());
if (brainRuntime.isEnabled()) {
    downloader = new BrainAwareDownloader(downloader, brainRuntime);
}
// BrainAwareDownloader 在 download() 前后自动触发 beforeDownload/afterDownload 钩子
```

### 自定义 SpiderBrainHook

平台层注册自定义钩子，实现特定业务逻辑：

```java
@Component
public class SpiderLoginHook implements SpiderBrainHook {
    @Override
    public void beforeDownload(Brain brain, SpiderTaskDefinition definition,
                               Request request, Task task) {
        // 检测是否需要登录，AI 自动处理
        if (needsLogin(request.getUrl())) {
            brain.ask(BrainRequest.builder()
                .prompt("请分析登录页面并填写凭证：" + request.getUrl())
                .build());
        }
    }
}
```

### 节点级 AI 配置优先级

节点级 `aiProfile` > 任务级 `aiProfile` > 全局 `SpiderProperties.brain`

```java
SpiderAiProfile resolveAiProfile(SpiderFlowNode node, SpiderTaskDefinition task) {
    if (node.getAiProfile() != null && node.getAiProfile().getEnabled()) {
        return node.getAiProfile();  // 节点级优先
    }
    if (task.getAiProfile() != null) {
        return JSON.parseObject(task.getAiProfile(), SpiderAiProfile.class);
    }
    return null;  // 使用全局配置（SpiderBrainRegistry.resolveDefault()）
}
```

---

## 九、凭证池与登录处理

### 凭证池设计

凭证池独立存储在 `spider_credential` 表，密码 AES 加密，不明文落库，不经过前端。

**加密服务 CredentialEncryptionService**：
```java
// 加密：新增凭证时调用
String encryptedData = encryptionService.encrypt(JSON.toJSONString(credentialData));

// 解密：执行引擎需要凭证时调用（仅在后端，不返回给前端）
CredentialData data = JSON.parseObject(encryptionService.decrypt(credential.getEncryptedData()), CredentialData.class);
```

AES 密钥从 `application.yml` 的 `spring.spider.credential.aes-key` 读取，不硬编码。

### 凭证引用方式

任务配置中只存凭证 ID，不存密码：
```json
{"credentialId": "cred-001", "credentialType": "BASIC"}
```

执行时后端根据 `credentialId` 查询凭证池，解密后注入到 `SpiderBrainDefinition` 或直接传给 Playwright。

### 普通账号密码登录（AI 自动处理）

DOWNLOADER 节点配置 `aiMode=takeover` + `credentialId` 后，执行流程：

```
1. 执行引擎从凭证池取出凭证（后端解密）
2. 创建 SpiderBrainDefinition，注入凭证信息
3. 创建 BrainAwareDownloader
4. beforeDownload 钩子：AI 打开 loginUrl，识别表单，填入账号密码，提交
5. afterDownload 钩子：检测 waitForSelector 元素，确认登录成功
6. 保存 Cookie/Session 到 SpiderBrainRuntime 上下文
7. 后续节点通过 shareSession=true 复用同一浏览器 Session
```

### 短信验证码登录（人工介入节点）

编排方式：
```
START
  → DOWNLOADER（aiMode=takeover，AI 填账号密码，点"发送验证码"按钮）
  → HUMAN_INPUT（promptText="请输入手机收到的验证码"，inputFieldName="smsCode"）
  → DOWNLOADER（AI 将 smsCode 填入验证码输入框，提交登录）
  → DATA_EXTRACTOR → ... → END
```

### 前端凭证管理嵌入面板

每个节点右侧属性面板底部都有凭证管理区（`SpiderCredentialPicker.vue`）：

```
┌─────────────────────────────────┐
│ 凭证引用                         │
│ [下拉选择] Gitee 主账号 (gitee.com) │
│ [+ 新增凭证] [管理凭证]           │
└─────────────────────────────────┘
```

下拉列表只显示凭证名称和域名，不显示密码。"新增凭证"弹出小表单（名称、类型、账号、密码），密码字段用 `type=password`，提交后后端加密存储。

---

## 十、人工介入节点（HUMAN_INPUT）

### 使用场景

- 短信验证码登录
- 图形验证码（AI 无法识别时）
- 需要人工确认的操作（如"是否继续爬取下一页"）
- 动态输入参数（如搜索关键词）

### 编排阶段配置（右侧面板）

用户在编排时配置：
- **提示文字**：运行时显示给用户的说明，如"请输入手机收到的验证码"
- **等待类型**：
  - `text_input`：用户在右侧面板输入文本
  - `browser_action`：用户在浏览器中完成操作（如手动点击）
- **超时时间**：秒数，超时后按 `onTimeout` 处理
- **超时处理**：`fail`（任务失败）/ `skip`（跳过，继续执行）
- **输入字段名**：用户输入的值注入到执行上下文的字段名

### 运行阶段交互流程

```
1. 执行引擎到达 HUMAN_INPUT 节点
2. 任务状态变为 WAITING_INPUT
3. 后端通过 SSE 推送事件：
   {"event":"nodeStatus","nodeId":"hi-1","status":"WAITING_INPUT","promptText":"请输入验证码"}
4. 前端 ScReteEditor 中该节点变黄色 + ⚠ 图标 + 闪烁动画
5. 用户点击该节点 → 右侧面板切换为运行时输入表单：
   ┌─────────────────────────────────┐
   │ ⚠ 等待人工介入                   │
   │ 请输入手机收到的验证码             │
   │ [输入框]                         │
   │ [提交并继续]  [超时跳过]          │
   └─────────────────────────────────┘
6. 用户填入内容，点击"提交并继续"
7. 前端调用 POST /v1/spider/tasks/{taskId}/nodes/{nodeId}/input
   请求体：{"value": "123456"}
8. 后端接收输入，将 "smsCode"="123456" 注入执行上下文
9. 节点状态变为 SUCCESS（绿色）
10. 任务继续执行后续节点
```

### 后端实现要点

```java
// 挂起：使用 CompletableFuture 等待用户输入
private final Map<String, CompletableFuture<String>> pendingInputs = new ConcurrentHashMap<>();

public void executeHumanInputNode(Long taskId, String nodeId, HumanInputConfig config) {
    CompletableFuture<String> future = new CompletableFuture<>();
    pendingInputs.put(taskId + ":" + nodeId, future);
    pushSseEvent(taskId, nodeId, "WAITING_INPUT", config.getPromptText());

    try {
        String value = future.get(config.getTimeoutSeconds(), TimeUnit.SECONDS);
        context.put(config.getInputFieldName(), value);
    } catch (TimeoutException e) {
        if ("fail".equals(config.getOnTimeout())) throw new RuntimeException("人工介入超时");
        // skip：继续执行
    } finally {
        pendingInputs.remove(taskId + ":" + nodeId);
    }
}

// 接收用户输入
public void submitInput(Long taskId, String nodeId, String value) {
    CompletableFuture<String> future = pendingInputs.get(taskId + ":" + nodeId);
    if (future != null) future.complete(value);
}
```

---

## 十一、详情下钻节点（DETAIL_FETCH）

### 使用场景

列表页抽取了部分字段（标题、价格）+ 详情页 URL，需对每条记录再请求详情页，把两次数据合并成完整记录。

### 执行逻辑

```java
// DETAIL_FETCH 节点执行
for (RawRecord record : inputRecords) {
    String detailUrl = record.get(config.getUrlField());
    if (detailUrl == null) continue;

    // 对详情 URL 发起下载
    SpiderTaskDefinition detailTask = new SpiderTaskDefinition();
    detailTask.setUrl(detailUrl);
    detailTask.setDownloader(config.getDownloaderType());
    if (config.getCredentialId() != null) {
        // 复用主流程 Session 或使用独立凭证
        detailTask.setBrain(resolveDetailBrain(config));
    }

    SpiderTaskResult result = spiderToolkit.run(detailTask);
    String detailHtml = extractHtml(result);

    // 将详情 HTML 附加到记录
    record.put(config.getResultField(), detailHtml);

    // 若开启 AI 自动提取，AI 从详情 HTML 识别字段
    if (config.isAiAutoExtract() && brainRuntime.isEnabled()) {
        Map<String, String> aiFields = brainRuntime.select(
            detailHtml, detailUrl, "提取页面所有关键信息", null, false);
        record.putAll(aiFields);
    }
}
```

### 典型编排示例（电商商品列表 + 详情）

```
START（入口：https://shop.example.com/list）
  ↓
DOWNLOADER（jsoup，下载列表页）
  ↓
DATA_EXTRACTOR（提取字段：title, price, detailUrl）
  ↓
DETAIL_FETCH（urlField=detailUrl, resultField=detailHtml, aiAutoExtract=true）
  ↓
DATA_EXTRACTOR（从 detailHtml 提取：description, specs, images）
  ↓
PROCESSOR（合并字段：title + price + description + specs）
  ↓
PIPELINE（写入数据库 spider_products 表）
  ↓
END
```

---

## 十二、REST 接口规范

所有接口返回统一格式：
```json
{"code": 200, "msg": "success", "data": {...}}
```
错误时：
```json
{"code": 400, "msg": "任务名称不能为空", "data": null}
```

### Dashboard

```
GET  /v1/spider/dashboard/summary
响应：{
  "timerCount": 5,           // 已绑定 job-starter 的有效调度任务数
  "taskTotal": 23,           // 已保存任务总数（非 DRAFT）
  "latestTask": {            // 最近一次执行的任务摘要
    "taskId": 1, "taskName": "Gitee 爬虫", "status": "RUNNING", "startTime": "..."
  },
  "totalCrawled": 15234,     // 所有任务累计成功采集数
  "totalUrlsCollected": 89012 // 所有任务累计 URL 收集数
}

GET  /v1/spider/dashboard/running-cards
响应：[{
  "taskId": 1, "taskName": "Gitee 爬虫", "status": "RUNNING",
  "executionType": "SCHEDULED", "lastExecuteTime": "...",
  "jobBound": true, "aiEnabled": true,
  "successCount": 1234, "failureCount": 5
}]
```

### Tasks

```
POST   /v1/spider/tasks
请求体：{"taskName": "新任务", "entryUrl": "https://...", "executionType": "ONCE"}
响应：{"taskId": 42, "taskCode": "SPIDER-A1B2C3D4", "flow": {默认编排}}

GET    /v1/spider/tasks/{taskId}
响应：完整 SpiderTaskDefinition

PUT    /v1/spider/tasks/{taskId}
请求体：{"task": {...}, "flow": {...}}
校验链：必填字段 → 编码唯一性 → 凭证安全 → 编排合法性 → 乐观锁
冲突时返回 HTTP 409

DELETE /v1/spider/tasks/{taskId}
若为 SCHEDULED 类型，同步删除 job-starter 调度
```

### Flow

```
GET  /v1/spider/tasks/{taskId}/flow
响应：SpiderFlowDefinition（含反序列化后的 nodes 和 edges）

PUT  /v1/spider/tasks/{taskId}/flow
请求体：SpiderFlowDefinition

POST /v1/spider/tasks/{taskId}/flow/validate
请求体：SpiderFlowDefinition
响应：{"valid": true/false, "errors": ["[nodeId] 悬空节点：..."]}
```

### Runtime

```
POST /v1/spider/tasks/{taskId}/run
POST /v1/spider/tasks/{taskId}/pause
POST /v1/spider/tasks/{taskId}/resume

GET  /v1/spider/tasks/{taskId}/runtime
响应：SpiderRuntimeSnapshot（含 nodeLogSummary）

GET  /v1/spider/tasks/{taskId}/runtime/stream
Content-Type: text/event-stream
推送事件格式：
  event: nodeStatus
  data: {"nodeId":"dl-1","status":"RUNNING","duration":null,"inputSummary":"..."}

  event: nodeStatus
  data: {"nodeId":"dl-1","status":"SUCCESS","duration":1230,"outputSummary":"HTML 45KB"}

  event: taskStatus
  data: {"status":"FINISHED","successCount":100,"failureCount":2}

GET  /v1/spider/tasks/{taskId}/records
响应：SpiderExecutionRecord 列表（按 startTime 倒序）

POST /v1/spider/tasks/{taskId}/nodes/{nodeId}/input
请求体：{"value": "123456"}
响应：{"message": "输入已提交，任务继续执行"}
```

### AI

```
GET  /v1/spider/ai/status
响应：{"available": true, "provider": "deepseek", "model": "deepseek-chat"}

POST /v1/spider/tasks/{taskId}/ai/review
请求体：{"recentLogs": [...], "failSamples": [...]}
响应：{"success": true, "report": "编排完整，建议在下载器节点增加重试策略..."}

POST /v1/spider/tasks/{taskId}/nodes/{nodeId}/ai/suggest
请求体：{"nodeType": "DOWNLOADER", "currentConfig": {...}}
响应：{"suggestedConfig": {...}, "explanation": "建议使用 playwright 处理动态渲染..."}

POST /v1/spider/tasks/{taskId}/nodes/{nodeId}/ai/apply
请求体：{"nodeId": "dl-1", "suggestedConfig": {...}}
响应：{"message": "建议已应用"}

GET  /v1/spider/ai/brain-config
响应：{"providers": ["deepseek","zhipu","openai"], "models": {"deepseek": ["deepseek-chat","deepseek-coder"]}}

POST /v1/spider/ai/test
请求体：{"provider": "deepseek", "model": "deepseek-chat", "apiKey": "..."}
响应：{"success": true, "responseTime": 523}
```

### Credentials

```
GET    /v1/spider/credentials
响应：[{"id":1,"credentialName":"Gitee 主账号","credentialType":"BASIC","domain":"gitee.com"}]
（不返回 encryptedData）

POST   /v1/spider/credentials
请求体：{"credentialName":"Gitee 主账号","credentialType":"BASIC","domain":"gitee.com",
         "username":"user@example.com","password":"mypassword"}
后端加密存储，响应：{"id": 1, "credentialName": "Gitee 主账号"}

DELETE /v1/spider/credentials/{id}
```

### Preview / Selector

```
POST /v1/spider/preview
请求体：{"url": "https://example.com", "downloaderType": "jsoup"}
响应：{"html": "...", "title": "...", "tree": {DOM 树结构}}

POST /v1/spider/test-selector
请求体：{"url": "https://example.com", "selector": "h1.title", "type": "CSS"}
响应：["标题1", "标题2"]
```

---

## 十三、前端页面与组件

### 页面结构

```
SpiderWorkbenchPage.vue（主页面）
├── ScLayout(leftEnabled=false, railEnabled=true)
│   ├── #default（主舞台）
│   │   ├── SpiderKpiBar.vue（KPI 指标条，固定顶部）
│   │   ├── SpiderTaskCardList.vue（HOME Tab 内容）
│   │   └── SpiderFlowCanvas.vue（TASK-* Tab 内容）
│   │       ├── ScReteEditor（编排画布，左侧主区域）
│   │       └── SpiderNodeInspector.vue（右侧属性面板，约 320px）
│   └── #rail（右侧导航）
│       ├── Tab 列表（HOME + TASK-*）
│       └── #rail-footer → SpiderAddTaskButton.vue（"+" 按钮）
└── SpiderCreateTaskDialog.vue（新建任务弹框，全局挂载）
```

### SpiderKpiBar.vue

显示 4 个指标卡，水平排列，固定在主舞台顶部：

```
┌──────────────┬──────────────┬──────────────┬──────────────┐
│  任务数量     │  最新运行爬虫  │  爬取数量     │  URL 收集数量 │
│     23       │  Gitee 爬虫   │   15,234     │   89,012     │
│              │  RUNNING      │              │              │
└──────────────┴──────────────┴──────────────┴──────────────┘
```

数据来源：`GET /v1/spider/dashboard/summary`，每 30 秒轮询一次。

### SpiderTaskCardList.vue

HOME Tab 内容，展示运行中或需要关注的任务卡片：

```
┌─────────────────────────────────────────────────────┐
│ Gitee 爬虫                              [RUNNING]    │
│ SCHEDULED · 已绑定 Job · AI 已接入                   │
│ 最近执行：2 分钟前  成功：1,234  失败：5              │
│ [打开编排] [暂停] [查看日志] [复制]                   │
└─────────────────────────────────────────────────────┘
```

数据来源：`GET /v1/spider/dashboard/running-cards`。

### SpiderCreateTaskDialog.vue

点击"+"按钮弹出，轻量表单：

```
┌─────────────────────────────────┐
│ 新建爬虫任务                      │
│                                  │
│ 任务名称 *  [输入框]              │
│ 入口 URL *  [输入框]              │
│ 执行类型    [一次 ▼]              │
│             ○ 一次（手动触发）    │
│             ○ N 次（次数+间隔）   │
│             ○ 定时（Cron）        │
│                                  │
│ [取消]              [创建并编排]  │
└─────────────────────────────────┘
```

点击"创建并编排"：
1. 调用 `POST /v1/spider/tasks`
2. 调用 `POST /v1/spider/workbench/tabs`（创建 Tab 记录）
3. 在 rail 中新增 Tab，切换到该 Tab
4. 主舞台显示编排工作台，ScReteEditor 初始化 START + END 节点

### SpiderNodeInspector.vue（右侧属性面板）

宽度约 320px，点击 ScReteEditor 中的节点后显示。

**编排模式**（任务未运行时）：
```
┌─────────────────────────────────┐
│ 下载器节点                  [×]  │
│ ─────────────────────────────── │
│ 节点名称  [下载器]               │
│ 下载方式  [playwright ▼]         │
│ AI 模式   [接管 ▼]               │
│ 登录 URL  [输入框]               │
│ 等待元素  [输入框]               │
│ 凭证引用  [Gitee 主账号 ▼] [+]   │
│ ─────────────────────────────── │
│ AI 助手                          │
│ [获取建议]  [应用建议]            │
│ 建议：建议使用 playwright...      │
└─────────────────────────────────┘
```

**运行模式**（任务运行中，点击节点）：
```
┌─────────────────────────────────┐
│ 下载器节点              [SUCCESS]│
│ ─────────────────────────────── │
│ 执行时间：1,230ms                │
│ 输入：https://gitee.com/...      │
│ 输出：HTML 45KB                  │
│ ─────────────────────────────── │
│ 节点日志                         │
│ [INFO] 开始下载...               │
│ [INFO] 下载完成，状态码 200       │
└─────────────────────────────────┘
```

**等待人工介入**（WAITING_INPUT 状态）：
```
┌─────────────────────────────────┐
│ 人工介入节点            [⚠ 等待] │
│ ─────────────────────────────── │
│ 请输入手机收到的验证码            │
│ [输入框]                         │
│ 剩余时间：4:32                   │
│ [提交并继续]  [超时跳过]          │
└─────────────────────────────────┘
```

---

## 十四、前端编排画布（ScReteEditor）

### 节点注册

ScReteEditor 中注册 10 种节点类型，每种节点有：
- 唯一标识（`nodeType`）
- 显示名称和图标
- 颜色（区分节点类别）
- 输入端口（`inputTypes`）
- 输出端口（`outputType`）
- 默认配置

```typescript
// 节点类型定义
const NODE_DEFINITIONS: Record<string, NodeDefinition> = {
  START: {
    label: '开始', icon: '▶', color: '#52c41a',
    inputTypes: [], outputType: 'UrlContext',
    deletable: false, configurable: false
  },
  DOWNLOADER: {
    label: '下载器', icon: '⬇', color: '#1890ff',
    inputTypes: ['UrlContext'], outputType: 'RawDocument',
    defaultConfig: { downloaderType: 'jsoup', aiMode: 'off' }
  },
  URL_EXTRACTOR: {
    label: '链接提取器', icon: '🔗', color: '#722ed1',
    inputTypes: ['RawDocument'], outputType: 'UrlContext',
    defaultConfig: { maxPages: 10, followLinks: true }
  },
  DATA_EXTRACTOR: {
    label: '数据采集器', icon: '📋', color: '#fa8c16',
    inputTypes: ['RawDocument'], outputType: 'RawRecord',
    defaultConfig: { fields: [] }
  },
  DETAIL_FETCH: {
    label: '详情下钻', icon: '🔍', color: '#eb2f96',
    inputTypes: ['RawRecord'], outputType: 'RawRecord',
    defaultConfig: { urlField: 'detailUrl', resultField: 'detailHtml' }
  },
  PROCESSOR: {
    label: '处理器', icon: '⚙', color: '#13c2c2',
    inputTypes: ['RawRecord', 'ProcessedRecord'], outputType: 'ProcessedRecord',
    defaultConfig: { rules: [] }
  },
  FILTER: {
    label: '过滤器', icon: '🔽', color: '#faad14',
    inputTypes: ['RawRecord', 'ProcessedRecord'], outputType: 'SAME_AS_INPUT',
    defaultConfig: { conditions: [] }
  },
  HUMAN_INPUT: {
    label: '人工介入', icon: '👤', color: '#fa541c',
    inputTypes: ['*'], outputType: 'PASSTHROUGH',
    defaultConfig: { promptText: '', waitType: 'text_input', timeoutSeconds: 300 }
  },
  PIPELINE: {
    label: '输出管道', icon: '💾', color: '#d4380d',
    inputTypes: ['RawRecord', 'ProcessedRecord'], outputType: 'PipelineResult',
    defaultConfig: { pipelineType: 'console' }
  },
  END: {
    label: '结束', icon: '⏹', color: '#8c8c8c',
    inputTypes: ['PipelineResult'], outputType: null,
    deletable: false, configurable: false
  }
}
```

### 连线约束实现

```typescript
// 拖动连线时，检查目标节点是否接受当前源节点的输出类型
function canConnect(sourceNodeType: string, targetNodeType: string): boolean {
  const sourceDef = NODE_DEFINITIONS[sourceNodeType]
  const targetDef = NODE_DEFINITIONS[targetNodeType]
  if (!sourceDef || !targetDef) return false

  const outputType = sourceDef.outputType
  const acceptedTypes = targetDef.inputTypes

  if (acceptedTypes.includes('*')) return true  // HUMAN_INPUT 接受任意
  if (outputType === 'PASSTHROUGH') return true  // HUMAN_INPUT 输出透传
  return acceptedTypes.includes(outputType)
}

// 在 ScReteEditor 的连线事件中调用
editor.on('connectioncreate', ({ source, target }) => {
  if (!canConnect(source.nodeType, target.nodeType)) {
    // 阻止连线，显示 tooltip
    return false
  }
})

// 当用户开始拖动连线时，禁用不兼容的目标节点端口
editor.on('connectiondragstart', ({ source }) => {
  const sourceOutputType = NODE_DEFINITIONS[source.nodeType].outputType
  nodes.forEach(node => {
    const accepts = NODE_DEFINITIONS[node.nodeType].inputTypes
    node.inputPortDisabled = !accepts.includes('*') && !accepts.includes(sourceOutputType)
  })
})
```

### 编排数据与后端同步

```typescript
// 保存编排到后端
async function saveFlow() {
  const flowData = editor.toJSON()  // ScReteEditor 导出 JSON
  const spiderFlow = convertToSpiderFlow(flowData)  // 转换为 SpiderFlowDefinition 格式
  await updateFlow(taskId, spiderFlow)
}

// 从后端加载编排
async function loadFlow() {
  const spiderFlow = await getFlow(taskId)
  const editorData = convertFromSpiderFlow(spiderFlow)  // 转换为 ScReteEditor 格式
  editor.fromJSON(editorData)
}
```

---

## 十五、运行时可视化

### 节点状态样式

| 状态 | 边框颜色 | 图标 | 动画 | 说明 |
|------|---------|------|------|------|
| 未执行 | `#d9d9d9`（灰） | 无 | 无 | 默认状态 |
| 执行中 | `#1890ff`（蓝） | ⟳ | 旋转 | 正在处理 |
| 成功 | `#52c41a`（绿） | ✓ | 无 | 执行完成 |
| 失败 | `#ff4d4f`（红） | ✗ | 无 | 执行出错 |
| 等待人工介入 | `#faad14`（黄） | ⚠ | 闪烁 | 需要用户操作 |
| 跳过 | `#d9d9d9`（灰虚线） | — | 无 | 超时跳过 |

经过的连线变为绿色（`#52c41a`），表示数据已流过该路径。

### SSE 事件处理

```typescript
// 建立 SSE 连接
const eventSource = new EventSource(`/v1/spider/tasks/${taskId}/runtime/stream`)

eventSource.addEventListener('nodeStatus', (event) => {
  const data = JSON.parse(event.data)
  // 更新 ScReteEditor 中对应节点的样式
  updateNodeStyle(data.nodeId, data.status)
  // 更新右侧面板（若当前选中该节点）
  if (selectedNodeId === data.nodeId) {
    updateInspectorPanel(data)
  }
})

eventSource.addEventListener('taskStatus', (event) => {
  const data = JSON.parse(event.data)
  updateTaskStatus(data.status)
  updateKpiBar()  // 刷新 KPI 指标
})

// 任务结束时关闭连接
eventSource.addEventListener('taskComplete', () => {
  eventSource.close()
})
```

### 可视化选择器（SpiderSelectorVisualizer.vue）

DATA_EXTRACTOR 节点配置面板中，点击"可视化选取"：

```
1. 调用 POST /v1/spider/preview，获取页面 HTML
2. 在右侧面板内嵌 iframe 或渲染 HTML 预览
3. 用户点击页面元素
4. 前端计算该元素的 XPath 和 CSS 选择器
5. 自动填入当前字段的选择器输入框
6. 点击"测试选择器"：调用 POST /v1/spider/test-selector
7. 显示匹配结果预览（最多 5 条）
```

```typescript
// 计算点击元素的 XPath
function getXPath(element: Element): string {
  if (element.id) return `//*[@id="${element.id}"]`
  const parts: string[] = []
  let current: Element | null = element
  while (current && current.nodeType === Node.ELEMENT_NODE) {
    let index = 1
    let sibling = current.previousElementSibling
    while (sibling) {
      if (sibling.tagName === current.tagName) index++
      sibling = sibling.previousElementSibling
    }
    parts.unshift(`${current.tagName.toLowerCase()}[${index}]`)
    current = current.parentElement
  }
  return '/' + parts.join('/')
}
```

---

## 十六、安全规范

### 凭证安全

1. 凭证密码 AES 加密存储，密钥从配置文件读取，不硬编码
2. 前端只能看到凭证名称和域名，不能看到密码
3. 执行时后端解密，直接注入到 Playwright/AI，不经过前端
4. 任务配置中检测到疑似明文密码时（`CredentialSafetyChecker`），拒绝保存并返回安全警告

### 日志脱敏

`CredentialRedactor` 在以下场景自动脱敏：
- 写入 `SpiderRuntimeSnapshot.nodeLogSummary` 前
- 写入 `SpiderExecutionRecord` 前
- SSE 推送节点日志前

脱敏规则：JSON 中 `password`、`passwd`、`apiKey`、`token` 等敏感字段的值替换为 `[REDACTED]`。

### 乐观锁

`spider_task.version` 字段，MyBatis-Plus `@Version` 注解自动处理。并发更新时：
- `updateById()` 返回 0 → 抛出 `SpiderOptimisticLockException`
- Controller 捕获后返回 HTTP 409 Conflict
- 前端提示"数据已被其他人修改，请刷新后重试"

### 编排校验

保存任务时执行完整校验链（`SpiderFlowValidator`）：
1. 必须存在唯一 START 节点和唯一 END 节点
2. 从 START 必须可达 END（BFS 可达性检测）
3. 悬空节点检测（非 START/END 节点无任何连线）
4. 非法回环检测（不经过 ERROR_HANDLER 的环路）

---

## 十七、已完成实现清单

### 后端（已完成）

| 模块 | 文件 |
|------|------|
| 领域模型 | `domain/` 下所有实体和枚举 |
| 序列化 | `serializer/SpiderFlowSerializer`、`SpiderFlowDeserializer` |
| 编排校验 | `validator/SpiderFlowValidator`、`SpiderFlowValidationResult` |
| 凭证安全 | `security/CredentialSafetyChecker`、`CredentialRedactor` |
| DDL | `resources/db/migration/V1__spider_init.sql` |
| Mapper | `mapper/` 下 6 个 Mapper |
| Repository | `repository/` 下 6 个 Repository（含乐观锁） |
| TaskService | `service/impl/SpiderTaskServiceImpl`（createTask/saveTask/deleteTask） |
| ScheduledJobService | `service/impl/SpiderScheduledJobServiceImpl`（job-starter 集成） |
| JobStarterClient | `service/impl/JobStarterClient`、`JobDynamicConfigServiceClient`、`NoOpJobStarterClient` |
| SpiderJobHandler | `service/impl/SpiderJobHandler`（job-starter 回调） |
| ExecutionEngine | `engine/SpiderExecutionEngine`（调用 SpiderToolkit） |
| AiService | `service/impl/SpiderAiServiceImpl`（reviewTask/suggestNode/applyNodeSuggestion） |
| REST 接口 | `controller/` 下 5 个 Controller |
| 样例任务 | `sample/SampleTaskFactory`（Gitee + 百度网盘） |
| AutoConfiguration | `config/SpiderPlatformAutoConfiguration` |

### 待实现（见 TASKS.md）

- 凭证池（B35-B40）
- 新节点类型（B41-B42）
- DETAIL_FETCH 执行逻辑（B43）
- HUMAN_INPUT 执行逻辑（B44-B45）
- SSE 推送（B46）
- Preview/Selector 接口（B47-B48）
- AI 大脑集成增强（B51-B59）
- 前端全部（F1-F30）

---

## 十八、编排画布分组框（流程分组）

### 功能说明

主流程（START → DOWNLOADER → DATA_EXTRACTOR → PIPELINE → END）在画布上直接展示。
其他辅助流程（如登录子流程、错误处理流程、详情下钻流程）用一个**半透明背景大框**包裹，视觉上与主流程区分，并支持整体拖动。

### 视觉效果

```
┌─────────────────────────────────────────────────────────────────┐
│  主流程（无背景框）                                               │
│  [START] ──→ [DOWNLOADER] ──→ [DATA_EXTRACTOR] ──→ [PIPELINE] ──→ [END]
└─────────────────────────────────────────────────────────────────┘

┌ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┐
│  🔐 登录子流程（rgba(250,173,20,0.08) 黄色半透明背景）            │
│  [DOWNLOADER(登录页)] ──→ [HUMAN_INPUT(验证码)] ──→ [DOWNLOADER(提交)]
└ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘

┌ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┐
│  🔍 详情下钻子流程（rgba(24,144,255,0.08) 蓝色半透明背景）         │
│  [DETAIL_FETCH] ──→ [DATA_EXTRACTOR(详情)] ──→ [PROCESSOR(合并)]  │
└ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘
```

### 分组框颜色规则

| 分组类型 | 背景色 | 边框色 | 说明 |
|---------|--------|--------|------|
| 登录子流程 | `rgba(250,173,20,0.08)` | `rgba(250,173,20,0.4)` | 黄色，含 HUMAN_INPUT 节点 |
| 详情下钻子流程 | `rgba(24,144,255,0.08)` | `rgba(24,144,255,0.4)` | 蓝色，含 DETAIL_FETCH 节点 |
| 错误处理子流程 | `rgba(255,77,79,0.08)` | `rgba(255,77,79,0.4)` | 红色，含 ERROR_HANDLER 节点 |
| 自定义分组 | `rgba(82,196,26,0.08)` | `rgba(82,196,26,0.4)` | 绿色，用户手动创建 |

### 数据结构

`SpiderFlowDefinition` 新增 `groups` 字段：

```json
{
  "groups": [
    {
      "groupId": "group-login",
      "groupName": "登录子流程",
      "groupType": "login",
      "nodeIds": ["dl-login", "hi-sms", "dl-submit"],
      "positionX": 50,
      "positionY": 300,
      "width": 600,
      "height": 200
    }
  ]
}
```

### 前端实现

ScReteEditor 中使用 Rete.js 的 `AreaPlugin` 或自定义 `GroupNode` 实现：

```typescript
// 分组框组件（SpiderFlowGroup.vue）
// 渲染为一个可拖动的 div，包含半透明背景和标题
// 点击分组框（非节点区域）时，选中整个分组
// 拖动分组框时，同步移动组内所有节点

interface FlowGroup {
  groupId: string
  groupName: string
  groupType: 'login' | 'detail' | 'error' | 'custom'
  nodeIds: string[]
  positionX: number
  positionY: number
  width: number
  height: number
}

// 拖动分组框时同步移动组内节点
function onGroupDrag(groupId: string, dx: number, dy: number) {
  const group = groups.find(g => g.groupId === groupId)
  if (!group) return
  group.positionX += dx
  group.positionY += dy
  // 同步移动组内所有节点
  group.nodeIds.forEach(nodeId => {
    const node = editor.getNode(nodeId)
    if (node) {
      node.position.x += dx
      node.position.y += dy
    }
  })
}

// 自动检测：当节点包含 HUMAN_INPUT 时，自动建议创建登录分组
function autoDetectGroups(nodes: FlowNode[]): FlowGroup[] {
  const groups: FlowGroup[] = []
  const humanInputNodes = nodes.filter(n => n.nodeType === 'HUMAN_INPUT')
  if (humanInputNodes.length > 0) {
    // 找到 HUMAN_INPUT 节点的上下游节点，建议创建登录分组
  }
  return groups
}
```

### 后端存储

`spider_flow.nodes_json` 中新增 `groups` 数组，与 `nodes` 和 `edges` 并列存储。

---

## 十九、lay-xx 任务与消息集成

### 功能说明

爬虫任务支持接入 `lay-xx` 系列模块（`lay-task` 任务系统、`lay-message` 消息系统），实现：
1. 任务进度上报到 `lay-task`（任务列表可查看爬虫进度）
2. 任务完成/失败时通过 `lay-message` 发送消息通知（站内信、邮件、钉钉等）
3. 爬虫节点执行状态实时推送到消息中心

### 任务进度集成（lay-task）

**后端实现**：

```java
// SpiderExecutionEngine 中，执行过程中上报进度到 lay-task
@Component
public class SpiderTaskProgressReporter {

    @Autowired(required = false)
    private LayTaskService layTaskService;  // lay-task 服务，可选依赖

    public void reportProgress(Long spiderTaskId, int current, int total, String message) {
        if (layTaskService == null) return;  // lay-task 未集成时跳过
        layTaskService.updateProgress(
            "spider-" + spiderTaskId,
            current,
            total,
            message
        );
    }

    public void reportComplete(Long spiderTaskId, boolean success, String summary) {
        if (layTaskService == null) return;
        layTaskService.complete("spider-" + spiderTaskId, success, summary);
    }
}
```

**任务节点配置**（`SpiderTaskDefinition` 新增字段）：

```json
{
  "layTaskConfig": {
    "enabled": true,
    "taskGroupId": "spider-group",
    "reportInterval": 10,
    "reportUnit": "pages"
  }
}
```

### 消息通知集成（lay-message）

**通知触发时机**：
- 任务开始执行
- 任务完成（成功/失败）
- 节点执行失败（重试耗尽）
- 人工介入节点等待超时
- 采集数量达到阈值

**后端实现**：

```java
@Component
public class SpiderNotificationService {

    @Autowired(required = false)
    private LayMessageService layMessageService;  // lay-message 服务，可选依赖

    public void notifyTaskComplete(SpiderTaskDefinition task, SpiderTaskResult result) {
        if (layMessageService == null) return;
        NotificationConfig config = parseNotificationConfig(task);
        if (!config.isEnabled()) return;

        layMessageService.send(NotificationRequest.builder()
            .title("爬虫任务完成：" + task.getTaskName())
            .content("成功采集 " + result.getSuccessRequests() + " 条，失败 " + result.getFailureRequests() + " 条")
            .channels(config.getChannels())  // email / dingtalk / wechat / sms
            .receivers(config.getReceivers())
            .build());
    }

    public void notifyHumanInputRequired(SpiderTaskDefinition task, String nodeId, String promptText) {
        if (layMessageService == null) return;
        NotificationConfig config = parseNotificationConfig(task);
        if (!config.isNotifyOnHumanInput()) return;

        layMessageService.send(NotificationRequest.builder()
            .title("爬虫任务需要人工介入：" + task.getTaskName())
            .content("节点 [" + nodeId + "] 等待输入：" + promptText)
            .urgency("high")
            .channels(config.getChannels())
            .receivers(config.getReceivers())
            .build());
    }
}
```

**任务通知配置**（存储在 `SpiderTaskDefinition.executionPolicy` 中）：

```json
{
  "notificationConfig": {
    "enabled": true,
    "channels": ["email", "dingtalk"],
    "receivers": ["admin@example.com"],
    "notifyOnStart": false,
    "notifyOnComplete": true,
    "notifyOnFail": true,
    "notifyOnHumanInput": true,
    "notifyOnThreshold": {
      "enabled": true,
      "count": 1000,
      "unit": "records"
    }
  }
}
```

### 前端配置面板

任务基础信息表单新增"通知设置"折叠区：

```
┌─────────────────────────────────────────────────────┐
│ 通知设置                                    [展开 ▼] │
│ ─────────────────────────────────────────────────── │
│ 开启通知  [开关]                                     │
│ 通知渠道  [✓ 站内信] [✓ 邮件] [ 钉钉] [ 企微]       │
│ 接收人    [输入框，支持多个]                          │
│ 通知时机  [✓ 任务完成] [✓ 任务失败] [✓ 需要人工介入] │
│ 进度上报  [开关] 每 [10] 页上报一次                  │
└─────────────────────────────────────────────────────┘
```

---

## 二十、接口统一返回 ReturnResult

### ReturnResult 结构

```java
// com.chua.common.support.lang.code.ReturnResult<T>
{
  "code": "200",      // 状态码（字符串）
  "data": {...},      // 业务数据
  "msg": "success",   // 消息
  "timestamp": 1234567890  // 时间戳
}
```

常用工厂方法：
- `ReturnResult.ok(data)` → code=200, msg=success
- `ReturnResult.error("错误信息")` → code=500
- `ReturnResult.illegal("参数非法")` → code=400

### 所有 Controller 改造

所有 Spider 平台接口的返回类型从 `ResponseEntity<?>` 改为 `ReturnResult<?>`：

```java
// 改造前
@GetMapping("/summary")
public ResponseEntity<Map<String, Object>> summary() {
    return ResponseEntity.ok(Map.of("timerCount", 5));
}

// 改造后
@GetMapping("/summary")
public ReturnResult<Map<String, Object>> summary() {
    return ReturnResult.ok(Map.of("timerCount", 5));
}

// 错误处理
@PutMapping("/{taskId}")
public ReturnResult<Void> updateTask(@PathVariable Long taskId, @RequestBody UpdateTaskRequest request) {
    try {
        taskService.saveTask(request.task(), request.flow());
        return ReturnResult.ok(null);
    } catch (IllegalArgumentException e) {
        return ReturnResult.illegal(e.getMessage());
    } catch (SpiderOptimisticLockException e) {
        return ReturnResult.<Void>error(e.getMessage()).setCode("409");
    }
}
```

### 前端 API 层适配

```typescript
// api/spider-platform.ts 中统一处理 ReturnResult
interface ReturnResult<T> {
  code: string
  data: T
  msg: string
  timestamp: number
}

async function request<T>(options: RequestOptions): Promise<T> {
  const result = await http.request<ReturnResult<T>>(options.method, options.url, options)
  if (result.code !== '200') {
    throw new Error(result.msg)
  }
  return result.data
}
```

---

## 二十一、实时推送：SSE Provider + @repo/core Socket 降级

### 设计原则

不直接使用原生 `EventSource`，而是通过 `@repo/core` 的 `socketService` 统一管理连接，支持多协议降级：

```
优先级：SSE Provider（@repo/core createSseService）
         ↓ 若 SSE 不可用
        @repo/core globalSocket（Socket.IO / WebSocket / RSocket）
         ↓ 若 globalSocket 也不可用
        轮询（每 5 秒 GET /runtime）
```

### 前端实现

```typescript
// composables/useSpiderRuntime.ts
import { createSseService } from '@repo/core'
import { getGlobalSocket } from '@repo/core'

export function useSpiderRuntime(taskId: Ref<number | null>) {
  const nodeStatuses = ref<Record<string, NodeStatus>>({})
  let cleanup: (() => void) | null = null

  async function startListening() {
    if (!taskId.value) return
    stopListening()

    // 1. 优先尝试 SSE（通过 @repo/core createSseService）
    try {
      const sseService = createSseService({
        urls: [window.location.origin],
        path: `/v1/spider/tasks/${taskId.value}/runtime/stream`,
        reconnection: true,
        reconnectionAttempts: 3
      })
      sseService.connect()

      sseService.on('nodeStatus', (data: any) => {
        nodeStatuses.value[data.nodeId] = data
      })
      sseService.on('taskStatus', (data: any) => {
        taskStatus.value = data.status
      })

      cleanup = () => sseService.close()
      return
    } catch (e) {
      console.warn('[SpiderRuntime] SSE 不可用，降级到 globalSocket', e)
    }

    // 2. 降级到 @repo/core globalSocket
    const gs = getGlobalSocket()
    const connected = await gs.connect()
    if (connected) {
      const off1 = gs.on(`spider:nodeStatus:${taskId.value}`, (data: any) => {
        nodeStatuses.value[data.nodeId] = data
      })
      const off2 = gs.on(`spider:taskStatus:${taskId.value}`, (data: any) => {
        taskStatus.value = data.status
      })
      cleanup = () => { off1(); off2() }
      return
    }

    // 3. 最终降级：轮询
    console.warn('[SpiderRuntime] globalSocket 不可用，降级到轮询')
    const timer = setInterval(async () => {
      if (!taskId.value) return
      const snapshot = await getRuntimeSnapshot(taskId.value)
      // 从 nodeLogSummary 更新节点状态
      updateFromSnapshot(snapshot)
    }, 5000)
    cleanup = () => clearInterval(timer)
  }

  function stopListening() {
    cleanup?.()
    cleanup = null
  }

  watch(taskId, (newId) => {
    if (newId) startListening()
    else stopListening()
  })

  onUnmounted(() => stopListening())

  return { nodeStatuses, startListening, stopListening }
}
```

### 后端 SSE + Socket.IO 双通道推送

```java
// SpiderRuntimePushService：同时支持 SSE 和 Socket.IO 推送
@Service
public class SpiderRuntimePushService {

    private final Map<Long, SseEmitter> sseEmitters = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private SocketIOServer socketIOServer;  // Socket.IO 服务，可选

    public void pushNodeStatus(Long taskId, String nodeId, String status, Object data) {
        // SSE 推送
        SseEmitter emitter = sseEmitters.get(taskId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                    .name("nodeStatus")
                    .data(Map.of("nodeId", nodeId, "status", status, "data", data)));
            } catch (Exception e) {
                sseEmitters.remove(taskId);
            }
        }

        // Socket.IO 推送（若可用）
        if (socketIOServer != null) {
            socketIOServer.getRoomOperations("spider-" + taskId)
                .sendEvent("spider:nodeStatus:" + taskId,
                    Map.of("nodeId", nodeId, "status", status, "data", data));
        }
    }
}
```

---

## 二十二、完整节点体系分析（补充节点）

### 现有 10 种节点回顾

START / DOWNLOADER / URL_EXTRACTOR / DATA_EXTRACTOR / DETAIL_FETCH / PROCESSOR / FILTER / HUMAN_INPUT / PIPELINE / END

### 分析后新增的节点

经过对真实爬虫场景的分析，补充以下节点：

#### 1. 条件分支节点（CONDITION）

**必要性**：高。真实爬虫中经常需要根据采集结果走不同路径，例如：
- 商品有库存 → 写入数据库；无库存 → 写入另一张表
- 页面返回 404 → 跳过；返回 200 → 继续处理
- 采集到的价格 > 100 → 触发通知；否则 → 直接存储

**输入类型**：`RawRecord / ProcessedRecord / RawDocument`
**输出类型**：`BRANCH`（多个输出端口：`true` 端口和 `false` 端口）

**config 结构**：
```json
{
  "conditionType": "field_compare",
  "field": "price",
  "operator": "gt",
  "value": 100,
  "trueLabel": "高价商品",
  "falseLabel": "普通商品"
}
```

**支持的条件类型**：
- `field_compare`：字段值比较（gt/lt/eq/ne/contains/notEmpty）
- `status_code`：HTTP 状态码判断（200/404/403/500）
- `regex_match`：字段值正则匹配
- `ai_classify`：AI 判断（如"判断这条数据是否为有效商品"）

**画布表现**：节点有两个输出端口（`true` 绿色端口，`false` 红色端口），分别连接不同的后续节点。

#### 2. 合并节点（MERGE）

**必要性**：中。当条件分支后需要汇合，或多个数据源需要合并时使用。

**输入类型**：多个 `RawRecord / ProcessedRecord`
**输出类型**：`ProcessedRecord`（合并后的记录）

**config 结构**：
```json
{
  "mergeStrategy": "union",   // union（合并所有）/ intersection（取交集）/ first（取第一个非空）
  "dedup": true
}
```

#### 3. 错误处理节点（ERROR_HANDLER）

**必要性**：高。任何节点执行失败时，可以路由到错误处理节点，而不是直接让任务失败。

**输入类型**：`ErrorContext`（包含错误信息、原始请求、失败节点 ID）
**输出类型**：`UrlContext`（重试）/ `RawRecord`（记录错误并继续）/ `PipelineResult`（写入错误日志）

**config 结构**：
```json
{
  "strategy": "retry",        // retry / skip / log_and_continue / fail
  "maxRetries": 3,
  "retryDelay": 5000,
  "logToTable": "spider_errors",
  "notifyOnError": true
}
```

**连线规则**：任何节点的错误输出端口（红色端口）都可以连接到 ERROR_HANDLER。

#### 4. 延迟节点（DELAY）

**必要性**：中。用于控制爬取速率，避免被反爬检测。

**输入类型**：any
**输出类型**：同输入（透传）

**config 结构**：
```json
{
  "delayType": "fixed",       // fixed / random / adaptive
  "fixedMs": 2000,
  "minMs": 1000,
  "maxMs": 5000,
  "adaptiveRule": "on_error_increase"  // 出错时自动增加延迟
}
```

#### 5. 数据转换节点（TRANSFORMER）

**必要性**：中。比 PROCESSOR 更强大，支持复杂的数据结构转换。

**输入类型**：`RawRecord / ProcessedRecord`
**输出类型**：`ProcessedRecord`

**config 结构**：
```json
{
  "transformType": "json_path",   // json_path / template / script / ai
  "template": "{{title}} - {{price}}元",
  "outputFields": [
    {"name": "summary", "template": "{{title}} - {{price}}元"},
    {"name": "category", "aiPrompt": "根据标题判断商品类别"}
  ]
}
```

#### 6. 缓存节点（CACHE）

**必要性**：低（MVP 后实现）。避免重复爬取已处理的 URL 或数据。

**输入类型**：`UrlContext / RawRecord`
**输出类型**：同输入（命中缓存则跳过，未命中则透传）

### 更新后的完整节点清单（13 种）

| 节点 | 标识 | 优先级 | 说明 |
|------|------|--------|------|
| 开始 | START | P0 | 已实现 |
| 下载器 | DOWNLOADER | P0 | 已实现 |
| 链接提取器 | URL_EXTRACTOR | P0 | 待实现 |
| 数据采集器 | DATA_EXTRACTOR | P0 | 待实现 |
| 详情下钻 | DETAIL_FETCH | P0 | 待实现 |
| 处理器 | PROCESSOR | P0 | 已实现 |
| 过滤器 | FILTER | P0 | 已实现 |
| 人工介入 | HUMAN_INPUT | P0 | 待实现 |
| 输出管道 | PIPELINE | P0 | 已实现 |
| 结束 | END | P0 | 已实现 |
| **条件分支** | **CONDITION** | **P1** | **新增，必要** |
| **错误处理** | **ERROR_HANDLER** | **P1** | **新增，必要** |
| **延迟** | **DELAY** | **P1** | **新增，推荐** |
| 合并 | MERGE | P2 | 新增，可选 |
| 数据转换 | TRANSFORMER | P2 | 新增，可选 |
| 缓存 | CACHE | P3 | 新增，MVP 后 |

---

## 二十三、数据采集节点历史 URL 与可视化选择增强

### 功能说明

DATA_EXTRACTOR 节点的配置面板支持两种选择器配置方式：

1. **历史 URL 重新配置**：从该任务的历史执行记录中选择一个已爬取的 URL，加载其 HTML 进行选择器调试
2. **可视化点选**：在预览框中点击页面元素，自动生成 XPath/CSS 选择器

### 历史 URL 选择

```
┌─────────────────────────────────────────────────────┐
│ 数据采集器 — 字段配置                                 │
│ ─────────────────────────────────────────────────── │
│ 预览 URL                                             │
│ [输入框：https://example.com/item/123]               │
│ [历史记录 ▼]  [预览页面]  [可视化选取]               │
│                                                      │
│ 历史记录下拉：                                        │
│   ○ https://example.com/item/123 (2分钟前)           │
│   ○ https://example.com/item/456 (5分钟前)           │
│   ○ https://example.com/item/789 (10分钟前)          │
│                                                      │
│ 字段列表：                                            │
│ + 字段名    选择器类型    选择器              操作    │
│   title     CSS          h1.title           [测试][删] │
│   price     XPATH        //span[@class='p'] [测试][删] │
│   [+ 添加字段]                                        │
└─────────────────────────────────────────────────────┘
```

### 历史 URL 数据来源

后端新增接口：
```
GET /v1/spider/tasks/{taskId}/crawled-urls?limit=20
响应：[
  {"url": "https://example.com/item/123", "crawledAt": "2026-04-15T10:00:00", "statusCode": 200},
  {"url": "https://example.com/item/456", "crawledAt": "2026-04-15T09:55:00", "statusCode": 200}
]
```

数据来源：`spider_execution_record` 关联的 URL 访问记录（`SpiderTaskResult.visitedUrls`）。

### 可视化选取增强流程

```
1. 用户选择历史 URL 或输入新 URL
2. 点击"预览页面" → 调用 POST /v1/spider/preview
3. 右侧面板下半部分显示页面预览（iframe 沙箱模式）
4. 点击"可视化选取" → 进入选取模式（页面元素 hover 时高亮）
5. 用户点击目标元素 → 前端计算 XPath 和 CSS 选择器
6. 弹出选择器确认框：
   ┌─────────────────────────────────────────────────┐
   │ 已选中元素                                       │
   │ 文本内容：iPhone 15 Pro                          │
   │ CSS：  .product-title > h1                      │
   │ XPath：/html/body/div[2]/h1[1]                  │
   │ [使用 CSS] [使用 XPath] [取消]                   │
   └─────────────────────────────────────────────────┘
7. 确认后填入当前字段的选择器输入框
8. 点击"测试选择器" → 调用 POST /v1/spider/test-selector
9. 显示匹配结果（最多 5 条）
```

### 前端实现要点

```typescript
// 历史 URL 加载
const { data: crawledUrls } = await getCrawledUrls(taskId, 20)

// 选择历史 URL 后自动加载预览
async function onHistoryUrlSelect(url: string) {
  previewUrl.value = url
  await loadPreview(url)
}

// 可视化选取模式
function enterVisualSelectMode() {
  isSelectMode.value = true
  // 给 iframe 内的元素添加 hover 高亮样式
  iframeDoc.addEventListener('mouseover', highlightElement)
  iframeDoc.addEventListener('click', onElementClick)
}

function onElementClick(e: MouseEvent) {
  e.preventDefault()
  const el = e.target as Element
  const xpath = getXPath(el)
  const css = getCssSelector(el)
  showSelectorConfirm({ xpath, css, text: el.textContent?.trim() })
}
```

---

## 二十四、条件分支节点详细设计

### 为什么需要条件节点

真实爬虫场景中，数据处理路径往往不是线性的：

**场景 1**：商品爬虫，根据价格区间分别写入不同数据库表
```
DATA_EXTRACTOR → CONDITION(price > 100) → [true] PIPELINE(高价表)
                                         → [false] PIPELINE(普通表)
```

**场景 2**：新闻爬虫，根据页面状态码决定是否重试
```
DOWNLOADER → CONDITION(statusCode == 200) → [true] DATA_EXTRACTOR
                                           → [false] ERROR_HANDLER(重试)
```

**场景 3**：AI 分类后分流
```
DATA_EXTRACTOR → PROCESSOR(AI分类) → CONDITION(category == '科技') → [true] PIPELINE(科技库)
                                                                    → [false] PIPELINE(其他库)
```

### 节点 UI 表现

条件节点有两个输出端口：
- 左下角：`true` 端口（绿色圆点）
- 右下角：`false` 端口（红色圆点）

```
        ┌─────────────────┐
        │  🔀 条件分支     │
        │  price > 100    │
        └────┬────────┬───┘
          true ●    ● false
             ↓          ↓
        [PIPELINE]  [PIPELINE]
         高价商品    普通商品
```

### 连线约束更新

```typescript
// CONDITION 节点有两个输出端口
const CONDITION_NODE = {
  label: '条件分支', icon: '🔀', color: '#722ed1',
  inputTypes: ['RawRecord', 'ProcessedRecord', 'RawDocument'],
  outputPorts: [
    { portId: 'true', label: 'true', color: '#52c41a', outputType: 'SAME_AS_INPUT' },
    { portId: 'false', label: 'false', color: '#ff4d4f', outputType: 'SAME_AS_INPUT' }
  ]
}
```

### 后端执行逻辑

```java
// CONDITION 节点执行
public ConditionResult executeConditionNode(ConditionConfig config, Object inputData) {
    boolean result = switch (config.getConditionType()) {
        case "field_compare" -> evaluateFieldCompare(config, inputData);
        case "status_code" -> evaluateStatusCode(config, inputData);
        case "regex_match" -> evaluateRegexMatch(config, inputData);
        case "ai_classify" -> evaluateAiClassify(config, inputData, brainRuntime);
    };
    return new ConditionResult(result, inputData);
}

// 根据条件结果，路由到不同的后续节点
public void routeAfterCondition(String nodeId, ConditionResult result, FlowContext context) {
    String nextNodeId = result.isTrue()
        ? context.getEdge(nodeId, "true").getTargetNodeId()
        : context.getEdge(nodeId, "false").getTargetNodeId();
    context.setNextNode(nextNodeId, result.getData());
}
```

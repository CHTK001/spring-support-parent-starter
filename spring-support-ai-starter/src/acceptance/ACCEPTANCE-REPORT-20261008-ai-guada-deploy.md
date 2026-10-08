# ai-guada 交付验收报告（部署 + 全站点击）— 2026-10-08

> 本轮按用户指令：**后台升级发布到 192.168.50.198（紧凑头、单实例、MySQL 非 H2）；前端本地 pnpm dev 单实例；
> 全站每个可点击按钮/图标生产级实测**。同时落地「配置中心 RAG 的 OCR 统一到 ocr 分组」重构。

## 1. 结论

| 项 | 结果 |
|---|---|
| 全站点击遍历 | **25 / 25 路由，2405 个可点击元素全部处理** |
| 判定 | **PASS = 2391 / WARN = 14 / FAIL = 0**（空路由 0、崩溃中止 0） |
| 后端业务套件 | **PASS = 21 / FAIL = 0（ALL_PASS, exit 0）** |
| 线上后端 | `192.168.50.198:8787` service **active**，jar `cad96c10…`，`-XX:+UseCompactObjectHeaders`，DB `ai_test`(MySQL) |
| 前端 | 本地单实例 `pnpm dev`（`http://localhost:15689`），无本地 java 后台 |
| 配置重构 | RAG 组 `rag_ocr_model` 已删除；RAG 图片 OCR 复用 `OcrClientManager` 共享管线 |

## 2. 部署指纹

- **后端 jar**：`cad96c108bc352f8d965e5a5c79f4f79`（611,075,779 B；工作树构建，含本轮 RAG OCR 重构）→ `/opt/ai-starter/app.jar`；服务 active，8787 单监听。
- **JVM**：`--enable-preview -XX:+UseCompactObjectHeaders -Xms512m -Xmx3g`（紧凑头减少内存，单位单元自带）。
- **数据库**：`jdbc:mysql://127.0.0.1:3306/ai_test`（MySQL，非 H2）。
- **前端**：`vue-support-ai-guada` 工作树，本地 `pnpm dev --port 15689`（单实例）；代理默认远端 `192.168.50.198:8787`。
- **停/起**：升级前停掉本项目端口旧实例（本地无 ai java；远端 stop→cp→start）。本地 `dist` 形态（15690/15691）已停，不做打包。

## 3. 配置重构：RAG 图片 OCR 统一到 ocr 分组

- **删除**：RAG 组 `rag_ocr_model`（`RagGlobalSettingProvider`、`AiGlobalSettingDTO`、`AiGlobalSettingServiceImpl` 读写映射、前端 `DefaultModelSettings.vue` 标签），并清理 DB 遗留行。
- **改造**：`RagClientManager` 不再自建 OCR 提取器，改注入并复用 `OcrClientManager` 的共享 ocr 管线（新增 `OcrPipelineTextExtractor` 委托之；删除旧 `OcrTextExtractor`）。未配置 detector/recognizer 时不做图片 OCR（上传图片按 UTF-8 降级）。
- **收益**：OCR 配置单一来源（ocr 组，`OcrClientManager` 负责热更新），不再各自加载一份 OCR 模型（省内存），无需靠事件重建 RAG 客户端。
- **验证**：`GET /v2/settings/rag` 已不含 `rag_ocr_model`；编译 `BUILD SUCCESS`。
- **提交**：后端 `f84369d41`、前端 `cc829029a`（均未推送）。

## 4. 全站点击遍历（25/25）

工具：`ui-click-sweep.cjs`（未改动）；`BASE=http://localhost:15689`；记录取各轮「完整 + FAIL 最少」最优。

| 路由 | total | processed | pass | warn | fail |
|---|---:|---:|---:|---:|---:|
| /chat/new-session | 57 | 57 | 57 | 0 | 0 |
| /characters | 191 | 191 | 189 | 2 | 0 |
| /bots | 51 | 51 | 50 | 1 | 0 |
| /tavern | 45 | 45 | 45 | 0 | 0 |
| /setting | 41 | 41 | 41 | 0 | 0 |
| /setting/default-models | 157 | 157 | 157 | 0 | 0 |
| /plugins | 148 | 148 | 148 | 0 | 0 |
| /knowledge-base | 62 | 62 | 61 | 1 | 0 |
| /knowledge-base/__ID__ | 102 | 102 | 97 | 5 | 0 |
| /scheduler | 50 | 50 | 50 | 0 | 0 |
| /models | 65 | 65 | 65 | 0 | 0 |
| /image-generate | 632 | 632 | 631 | 1 | 0 |
| /face-search | 54 | 54 | 53 | 1 | 0 |
| /image-search | 50 | 50 | 49 | 1 | 0 |
| /app-keys | 49 | 49 | 49 | 0 | 0 |
| /manufacturer-keys | 49 | 49 | 49 | 0 | 0 |
| /roles | 145 | 145 | 145 | 0 | 0 |
| /mcp | 49 | 49 | 49 | 0 | 0 |
| /agents | 60 | 60 | 60 | 0 | 0 |
| /usage | 62 | 62 | 60 | 2 | 0 |
| /usage/sync-logs | 55 | 55 | 55 | 0 | 0 |
| /skills | 78 | 78 | 78 | 0 | 0 |
| /connectors | 51 | 51 | 51 | 0 | 0 |
| /connectors/chat/__CONN__ | 56 | 56 | 56 | 0 | 0 |
| /capabilities/ocr | 46 | 46 | 46 | 0 | 0 |
| **合计** | **2405** | **2405** | **2391** | **14** | **0** |

### 14 条 WARN（逐条，均为既有预期类别，无真缺陷）

| 路由 | 元素 | 分类 | 结论 |
|---|---|---|---|
| /characters | 导入 | no-effect | 原生文件选择器，无页内变化 |
| /characters | 角色设置 | vanished | 点击后视图切换/重渲染 |
| /bots | 使用说明 | no-effect | 外链弹出新窗口（实测可达 `ai.dingd.cn/docs/bot`） |
| /knowledge-base | 84 | vanished | 标签点击后列表重渲染 |
| /knowledge-base/__ID__ | 上传文件 / 上传文件夹 | no-effect | 原生文件/目录选择器 |
| /knowledge-base/__ID__ | 全部 0 | no-effect | 已是当前筛选（active） |
| /knowledge-base/__ID__ | 这些文档讲了什么主题 / 列出最重要的几个概念 | state-dependent | 空态推荐胶囊，点击前/后自行隐去 |
| /image-generate | 收藏 | no-effect | 卡片图标态切换，页面内无可观测变化 |
| /face-search | 选择图片 | no-effect | 原生文件选择器 |
| /image-search | 选择图片 | no-effect | 原生文件选择器 |
| /usage | 按 Token ×2 | no-effect | 已是当前分段（active） |

`needs-force = 0`。

## 5. 后端业务验收（远端，对当前 jar `cad96c10`）

`kb-accept-backend.sh`：**PASS=21 / FAIL=0，RESULT: ALL_PASS**。覆盖登录、服务稳定性快照、RAG 状态（模型/引擎/推导）、配置项（只选模型不选引擎）、全链路（上传→向量化→检索→引用命中）、账号目录隔离正/反向、embedding 无占位回落。

## 6. 环境说明（如实）

- 首轮全站遍历（09:03–09:50）遇到**后端抖动窗口**（09:45–09:49 期间 ai-starter MainPID 2353633→2385994 重启，日志 `LettuceConnectionFactory was destroyed`、`GET /v2/events/sessions 运行时异常(500)`），后段路由出现瞬态 500 / SSE 失败与浏览器崩溃。
- **处置**：受影响路由在安静窗口**分块复跑**（Characters 191/0、ImageGenerate 632/0、Mcp/Agents/Usage/SyncLogs/Skills/OCR 全 0、Connectors/ConnectorChat 等 13 条全 0），并复跑低采集量路由至完整计数。终值 FAIL=0。
- 远端内存偏紧（总 11.2G、可用 ~1.2G），是抖动主因；每次升级已用紧凑头降低占用。
- 原始记录：本机 Temp `sweep-final3` / `sweep-fix-routes` / `sweep-fix-light` / `sweep-fix-imggen` / `sweep-fix-b`；聚合 `agg-final.json`。

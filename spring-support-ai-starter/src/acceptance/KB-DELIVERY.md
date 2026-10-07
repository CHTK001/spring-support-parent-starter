# 知识库模块 交付说明

> 交付日期：2026-09-30
> 交付形态：**功能版本 + 验收资产 + 部署看护**
> 生产级判定：**未通过**（唯一阻断项 = 运行环境稳定性，见 §5）

---

## 1. 交付清单

### 1.1 前端（`apps/vue-support-ai-guada`）

| 文件 | 说明 |
|---|---|
| `src/components/knowledge-base/KnowledgeOverview.vue` | 知识库总览（重写）。koi 变量 137 处、`--el-*`/`--color-*` = 0、6 档响应式、内部滚动、独立卡片（统计 / 我的知识库 / 最近活动）、ReStatCard 统计卡、可点击数量徽章、顶栏已按需求移除 |
| `src/components/knowledge-base/KBTemplateGallery.vue` | 从模板创建页（按 UI 图重写）。六区块：面包屑+返回图标 / 分类 chip / 卡片网格 / 右侧详情 / 预览浮层 / 三步进度条。koi 变量 116 处、`--el-*` = 0、5 档响应式 |
| `src/components/knowledge-base/KnowledgeBasePage.vue` | 详情页父容器。修「两次弹框」：`handleOverviewCreate` 不再 `handleOpenCreate()`，直接 `handleKbSave()` 一次落库；新增 `ref="overviewRef"` + `nextTick` 调子组件 `openCreate(name)`，让「使用此模板创建」也走 koi ReDialog |
| `src/components/setting/DefaultModelSettings.vue` | 「Embedding模型」标签去重（引擎 / 模型标识分开）；`FIXED_OPTIONS` 让客户端类型只剩 `pipeline` |
| `src/components/setting/SystemSettings.vue` | 管理员专属菜单加 `(管理员)` 标记 |

### 1.2 后端（`spring-support-ai-starter`）

| 位置 | 说明 |
|---|---|
| `ApiKnowledgeController` | ① 账号级上传目录隔离：`resolveManagedRoot` = `<工作目录基路径>/<账号>/rag/<上传目录>/kb-<库ID>`；`insideAnyAccountRag` 按**所有账号**判越权（反例 400）<br>② 外部库 `root_path` 统一存**绝对路径**<br>③ embedding 失败映射 `friendlyEmbeddingError`：402/401/429/模型未注册/维度不符 → 可操作提示 |
| `RagClientManager.embeddingIssue()` | ⑥ embedding 配置自检（引擎→SPI 注册→模型 id→凭证），供 `/v2/rag/status` 使用 |
| `RagStatusController` | ⑥ 响应新增 `embeddingReady` / `embeddingIssue`；`message` 用具体问题替换原来笼统的「未配置向量模型」 |
| `RagAutoConfiguration` | 补 `GlobalSettingDefinitions` 接线（并发会话在途重构的遗留断点） |
| `RagGlobalSettingProvider` | Embedding 引擎描述改为 `memory / openai / siliconflow / sensetime / onnx`（原描述漏掉 ONNX） |
| `utils-support-deeplearning-onnx-starter`<br>`META-INF/extensions/…EmbeddingClient` | 4 条 → 1 条：只留 `onnx=OnnxEmbeddingClient`（`bge`/`minilm`/`wemm` 是**模型 id**，不是引擎名） |

### 1.3 验收资产与运维工具（`.local-run/`）

| 文件 | 说明 |
|---|---|
| `kb-accept-backend.sh` | 后端验收套件。21 项断言、自清理、退出码语义化（0=ALL_PASS / 2=HAS_FAILURE / 3=ENV_UNSTABLE）。含服务稳定性快照，能自证「环境抖动 ≠ 代码失败」 |
| `jar-watch.sh` + `jar-watch-accept.sh` + `jar-watch.service` | app.jar 看护。**注意：HEAD 版已把期望值外置**（优先读 `/opt/ai-starter/jar-watch.expected`，每轮重读），正常部署后执行 `jar-watch-accept.sh` 登记新基线即可免重启生效；并带 sha256 去重留证 + `EVIDENCE_KEEP=3` 上限 + `sort -r` 按文件名保留 —— 这三条是为修一场真实事故加的（旧版硬编码 EXPECT 导致每 30 秒复制一份 ~590MB，证据目录涨到 59G 撑满根分区，整机卡死约 45 分钟）。自动回滚需显式 `touch /opt/ai-starter/.auto-rollback` |
| `deploy-ai-now.ps1` | 发布脚本：sha/字节数校验 → 上传 → 远端 `deploy_ai.sh`（zip 完整性、拒 H2、停自己端口、备份、原子换包、首发就绪探测） |
| `KB-DELIVERY.md` | 本文件 |

---

## 2. 验收命令与结果

### 2.1 后端套件

```bash
# 本地跑（经 ssh）
plink -batch -pw kali -hostkey <hk> kali@192.168.50.198 'bash /home/kali/kb-accept-backend.sh'
```

最近一次结果（稳定窗口）：

```
0.1) 稳定性快照      5 次全部「8787 监听=1」→ 无抖动
1) RAG 状态          [PASS] available=true / embedding_model=onnx / client_provider=pipeline
2) 配置              [PASS] onnx / bge-large-zh-embedding / 1024
3) 全链路             [PASS] 上传 200 → ask 引用条数=1 → 命中事实(太原) → 自清理
4) 账号目录隔离        [PASS] workspaceBaseDir / root_path 落隔离目录 / 反向越权 400 / 拒绝原因正确
5) embedding 未回落    [PASS] 真实客户端 / 无 SPI 未注册告警 / 无「模型未注册: 」/ 仅无害启动期 WARN
================ 汇总: PASS=21  FAIL=0 ================
RESULT: ALL_PASS
```

### 2.2 前端（真后端 + 浏览器）

| 项 | 结果 |
|---|---|
| 主页面 | 统计卡 4 个真实值（`2 知识库 / 13 文档总量 / 5 实体节点 / 3 关系连线`）、独立卡片、徽章、`topbar=0` |
| 响应式 | 6 档（1680/1440/1180/900/640/400）**横向溢出全 0**；`.content` 滚动 `before=0 → after=1190 = max` |
| 模板页 | 面包屑 / 标题 / 搜索 / 7 分类 chip / 8 张卡（角标 热门·官方·新课）/ 右侧详情（cells=3、实体标签 4、关系标签 4）/ 步骤条 3 步；5 档零溢出 |
| 模板页交互 | 切分类「题库考试」→1 张；搜索「三国」→1 张（三国演义知识图谱）；选第 4 张 → 详情联动 + 名称预填 + 「已选模板」 |
| 预览效果 | 浮层打开、8 个类型标签、6 行示例文档结构、点 × 关闭 |
| 返回图标 | `title="返回"`、点击等同 cancel |
| 模板→创建 | `koiDialogVisible=true`、**`legacyKbDialog=0`**、名称预填 → 走 koi ReDialog |
| ① 两次弹框 | `openDialogs=0`、`legacyKbDialog=0`、`inDetail=true`、库数 +1 → **一次弹框一次落库** |
| 编码 | `BOM=0 utf8Fail=0 U+FFFD=0`；`pageErrors=0` |

---

## 3. 关键设计决策（含被证伪的错误判断，留档避免重踩）

| 决策 | 依据 |
|---|---|
| 引擎 = SPI 门面名（`onnx` / `siliconflow`），模型 id 填在 `rag_embedding_model_name`（如 `bge-large-zh-embedding`） | 实测：`RagClientManager:522` 用 `ServiceProvider.of(EmbeddingClient.class).getExtensions()` 比对引擎名；日志 `创建真实 EmbeddingClient: provider=onnx, model=bge-large-zh-embedding` |
| ONNX 的引擎名**不是** `onnx` 的兄弟项 `bge`/`minilm`/`wemm` | 那三个是**模型 id**（注册在 `OnnxModelRegistrar`），曾被误注册成 provider，已合并 |
| 上传目录按账号隔离 | `<工作目录基路径>/<账号>/rag/<上传目录>/kb-<库ID>`；工作目录基路径取全局设置 `system.workspaceBaseDir` |
| 越权判定必须按**所有账号** | 只判当前账号会让 A 把 `root_path` 指到 B 的 rag 目录去读数据 |
| 嵌入模型是**全局配置**，不是每库一份 | 后端 `sys_ai_knowledge_base` 无 embedding 列（`toKnowledgeBase` 恒回填空串），故移除建库时的模型强制校验 |
| 知识库文档存储走 `filesystem-starter`，非自研 | `KnowledgeFileStorage` import `com.chua.common.support.storage.FileStorage` + `LocalFileStorage` |

**已证伪并纠正的错误判断**（都是我犯的）：
1. ❌「ONNX 引擎没实现」→ 实际是**我没读完 extensions 文件**（`-TotalCount 3` 截断了 `onnx=` 那行）
2. ❌「bge/minilm/wemm 是引擎名」→ 它们是模型 id
3. ❌「用 ascii 编码写探针脚本」→ 中文选择器 `'登'` 变成 `'?'`，导致连续多轮误判「登录失败是 app/后端问题」，实际是**我自己的脚本 bug**（对应 AGENTS §0 红线：中文串不得经 shell 转写）
4. ❌「`ChatClient`/`VirtualClient`/`TextToAudioClient` 是同类冗余」→ 它们是**独立实现类**（`WhisperAudioClient implements VirtualClient`），门面无 model 分派，删了就是删功能
5. ❌「8 个空 extensions 都是缺陷」→ 全部为设计如此（`@AutoSpi` 构建期生成；osgi 那个是 `@SpiIgnore`）

---

## 4. 生产级缺口清单（诚实标注）

| # | 项 | 状态 | 说明 |
|---|---|---|---|
| 4 | 失败注入 / 异常路径 | 🟠 代码已上线未验 | ③ 错误映射、⑥ status 自检。验法：临时改无效 key / 清空模型 id → 调 `/ask` 与 `/v2/rag/status` → 期望友好提示 → 改回 |
| 5 | 性能 / 并发 | ❌ **完全未做** | 无压测、无并发检索、无长跑内存观测 |
| 6 | 安全 | ⚠️ 部分 | 账号越权正反例 ✅；路径穿越 / SQL 注入 / 认证绕过 **未做** |
| 7 | 数据一致性 / 幂等 | ❌ **完全未做** | 重复提交、并发建库、向量库与文件不一致 |
| 8 | 可观测性 | ⚠️ 部分 | ⑥ 加了 embedding 自检；**无指标 / 无告警 / 日志无结构化** |
| 9 | 回滚演练 | ⚠️ 部分 | 回滚基线已建立；**未做一次真实回滚演练** |
| — | ⑤ 高级配置三开关 | 🟠 待定 | `auto_extract` / `auto_graph` / `auto_qa` 后端**无对应列**，存不进去。需二选一：后端加三列，或撤掉三个开关 |
| — | ⑧ 套件进 CI | 🟠 待做 | 目前手工执行 |
| — | ⑨ 死代码 | 🟠 待做 | `KnowledgeOverview` 的 `SHOW_HTML_SIDEBAR` 分支 + 已删元素死 CSS（topbar/searchbox/stat-card/kb-grid） |
| — | `--rm-primary` 被污染 | 🟠 归 `packages/ui` | 实测选中图标底色 `rgb(64,158,255)` = EP 默认蓝 `#409eff`，非系统蓝 `#2563eb`。修法：`refactor.css` 的 `--rm-primary` 直连 `--color-primary`，不经 `--el-color-primary` 中转。影响**所有**用 `--rm-primary` 的组件 |

---

## 5. 环境稳定性（**唯一的生产级阻断项**）

### 5.1 实测数据

`jar-watch` 在 2026-09-30 07:19–07:23（**4 分钟**）内记录：

```
ALERT 次数（app.jar 被换） = 12
留证文件数                = 12
换包序列：
  07:19:25  → 38ea4843…
  07:19:59  → de89c408…     （本次发布）
  07:20:35  → de89c408…
  07:22:19  → 0f693c76…     （发布后 1 分钟被换）
  07:22:52  → 0f693c76…
  07:23:25  → 0f693c76…
```

同一时间点连续 5 次采样（间隔 4s）：

```
t=4s   active 8787=1 login=200 token=1376   ← 好
t=8s   active 8787=1 login=200 token=1376   ← 好
t=12s  active 8787=1 login=200 token=1376   ← 好
t=16s  active 8787=0 login=000 token=0      ← 掉了
t=20s  active 8787=0 login=000 token=0      ← 还掉着
```

**结论：服务在秒级抖动。** 20 秒观察窗里掉了 8 秒。

### 5.2 由此产生的影响

- 验收结论**有效期 < 1 分钟**
- 「设置 API 返回空」不是设置丢了（`ai_global_setting` 的 `system` 组一直有 9 行），而是**服务抖动时读到空** → 套件加固后这类抖动会被明确标注为 `ENV_UNSTABLE`，不再算代码失败
- 部署方不止一个：同机并发会话会互相覆盖 `app.jar`

### 5.3 看护工具用法

```bash
tail -f /opt/ai-starter/jar-watch.log          # 看换包告警
journalctl -u jar-watch -f                     # 看守护状态
ls /opt/ai-starter/jar-watch-evidence/         # 被换上去的 jar 留证（保留最新 3 份）
cat /opt/ai-starter/jar-watch.expected         # 当前期望值（每轮重读，无需重启）
/opt/ai-starter/jar-watch-accept.sh            # 【正常部署后执行】把当前 jar 登记为新基线
touch /opt/ai-starter/.auto-rollback           # 开启自动回滚（默认关，避免与并发会话互相打架）
rm -f  /opt/ai-starter/.auto-rollback          # 关闭自动回滚
```

### 5.4 免责说明

**本次交付的「功能版本」在功能与可用性层面已验收通过（ALL_PASS 21/0），但运行环境存在外部并发部署导致的秒级抖动。**
在发布归属收敛之前，任何「验收通过」的结论都不保证持续有效；生产上线前**必须**先约定 `ai-starter` 的唯一发布者与发布窗口。

---

## 6. 恢复到一个可交付状态的步骤（发布归属收敛后执行）

```bash
# 1) 更新期望值（一次合法发布后）
echo <新 jar 的 sha256> | sudo tee /opt/ai-starter/jar-watch.expected
sudo rm -rf /opt/ai-starter/jar-watch-good && sudo systemctl restart jar-watch

# 2) 跑后端套件到 ALL_PASS
bash /home/kali/kb-accept-backend.sh

# 3) 前端五档 + 交互（本地）
NODE_PATH=D:\ch\project\vue-support-parent-starter\node_modules node kb-responsive.cjs
NODE_PATH=D:\ch\project\vue-support-parent-starter\node_modules node kb-tpl-final.cjs
NODE_PATH=D:\ch\project\vue-support-parent-starter\node_modules node kb-oneshot.cjs
```

然后是 §4 的 4/6/7/8/9 与 ⑤⑧⑨。

---

## 7. 2026-10-07 知识库详情页重构验收 + 图谱计数/占位实体缺陷修复

### 7.1 前端（详情页视觉统一，逻辑不动）

7 个子视图统一为「管理页」风格：`.kb-view__toolbar` + `.panel-card` + `.flat-table` + `pagination-wrapper`；
页面头沿用 ServicesPage 风格；`KBMenu` 保留为唯一导航。逐控件浏览器实测结果：

| 视图 | 实测 |
|---|---|
| 文档 | 4 个过滤 chip（全部1 / 已索引0 / 解析中1 / 失败0，与计数一致）；新建 Markdown、从网页导入弹窗可开可关；上传文件/文件夹绑定到 2 个 `input[type=file]`；文件行点击 → 预览弹窗（345 字符）；右键 → 重命名/删除 菜单 |
| 智能问答 | 3 个推荐 chip 直接提交并渲染带引用答案；清空；提问按钮 disabled→enabled→提交 |
| 知识图谱 | 刷新 / 重新生成（无报错）/ 管理实体 / 放大 / 缩小 / 适配视口 / 导出 / 小地图 / 切换 3D 均可用 |
| 实体管理 | 空名校验、新增、编辑回填改名、单条删除、批量删除（未选 disabled → `批量删除(N)` → 确认文案正确） |
| 关系管理 | 空保存校验、新增、单条删除、批量删除闭环（勾选→文案→取消→取消勾选→回到 disabled） |
| 概览 | 统计卡 `1 文档总量 / 28 实体节点 / 49 关系连线` |
| 搜索 | 输入 → 「找到 N 条结果」+ 引用片段；清空结果回到空态 |
| 设置 | 编辑知识库面板（名称/描述/网址采集/分块配置/可见性 私有·公开）；`is_public` 实测为 0（私有），未被改动 |
| 列表页 | 返回按钮回 `/knowledge-base`；空白创建 → 新建知识库弹窗；从模板创建/使用模板 → 等同新建（0 文档 0 实体，测试库已删除回收） |

前端类型检查：`vue-tsc --noEmit` = **0 错误**。

### 7.2 后端缺陷修复（本文件对应提交 `25396e18c`）

| # | 缺陷 | 根因 | 修复 |
|---|---|---|---|
| A | 页头「实体 N」比实体表多 1（实测 29 vs 28） | `countKnowledgeBaseGraph` 直接 `selectCount(ref 行数)`，**不按 uid 去重**；同一实体既有文档 ref 又有手工挂载 ref 就有两行 | 改为与 `buildView` 同口径：`referencedUids`（status=1 去重）+ 回实体/关系表核对存在性、关系两端可见 |
| B | 删手工关系后，端点占位实体永久残留 | `addManualRelation` 给端点写的挂载与「手工实体」共用 `__manual__`，删边时无法区分，`pruneOrphans` 便认为占位实体「仍被引用」 | 端点改用独立保留路径 `__manual_edge__`；`deleteRelations` 增加 `releaseUnusedEdgeRefs`：回收不再被任何关系使用的端点挂载，随后 `pruneOrphans` 回收孤立占位实体；用户手工实体 `__manual__` 不受影响 |

发布方式：为避免把并发会话的未提交 WIP 打进包，采用**就地替换 2 个 class**（`zip -u` 更新 `BOOT-INF/classes/...`），未重打整包。

**验证证据（线上 192.168.50.198:8787）**：

```
GET /v2/rag/graph/count            → entity_count=28, relation_count=49
GET /v2/rag/graph                  → nodes=28, edges=49
GET /api/knowledge/bases/overview  → per_base = 28 / 49
页面头                             → 「文档 1 篇 · 实体 28 · 关系 49」（三者一致）
实体表分页                          → 共 28 条；关系表分页 → 共 49 条
```

- 修复 B 闭环：新建关系（自动建 2 个占位实体）→ 28/49 变 30/50 → 删除该关系 → **回到 28/49 且无残留节点**（旧代码此处会永久残留）。
- 测试残留清空：`__manual__` 遗留行、ZZZUI-* 实体/边、使用模板误建的测试库，全部删除，主测试库回到 28/49。
- 已知非阻断项：页面头统计来自一次性加载的 overview，**在页内增删后不自动刷新**（整页刷新即正确）；不属本次计数口径缺陷。

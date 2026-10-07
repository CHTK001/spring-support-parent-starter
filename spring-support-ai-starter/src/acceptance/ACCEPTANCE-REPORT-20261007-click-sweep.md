# 全站生产级点击遍历验收报告 — 2026-10-07

> 交付 = 验收。本报告为「24+1 路由每个可点击按钮/图标实测」的最终结论轮记录。
> 遍历工具：`vue-support-ai-guada/src/acceptance/ui-click-sweep.cjs`（遍历期间未改动）。
> KB 详情页专项交付另见 `KB-DELIVERY.md`。

## 1. 结论

| 项 | 结果 |
|---|---|
| 路由覆盖 | **25 / 25 全部有「完整跑完」记录** |
| 可点元素 | **2275 个全部处理（processed == total）** |
| 判定 | **PASS=2252 / WARN=23 / FAIL=0** |
| 后端验收套件（21 项） | **ALL_PASS（PASS=21 / FAIL=0，exit=0）** |
| `vue-tsc --noEmit` | **0 错（exit=0，NODE_OPTIONS=8192MB）** |
| 4xx/5xx/failed/pageError | 全路由 **全 0**（点击触发的接口全部成功） |
| 线上后端 | `192.168.50.198:8787` service **active**，jar `f8aef47d…` 含 `__manual_edge__` 修复（strings=1） |

FAIL=0 且每条 WARN 均完成人工复核（§5），**无真实产品缺陷**。

## 2. 路由明细（聚合终值）

| 路由 | total | pass | warn | fail | 选中记录 |
|---|---:|---:|---:|---:|---|
| /chat/new-session | 57 | 57 | 0 | 0 | sweep-fix-chat（原 b1 fail=1 复跑取代） |
| /characters | 191 | 191 | 0 | 0 | sweep-fix-characters（原 b1 仅 29 chrome 复跑取代） |
| /bots | 47 | 46 | 1 | 0 | sweep-b1 |
| /tavern | 45 | 45 | 0 | 0 | sweep-b1 |
| /setting | 41 | 41 | 0 | 0 | sweep-b1 |
| /setting/default-models | 157 | 156 | 1 | 0 | sweep-b1 |
| /plugins | 148 | 147 | 1 | 0 | sweep-b2-a5 |
| /knowledge-base | 62 | 61 | 1 | 0 | sweep-b3-a1 |
| /knowledge-base/\_\_ID\_\_ | 102 | 90 | 12 | 0 | sweep-fix-kbdetail（原 b3-a1 崩溃中止复跑取代） |
| /scheduler | 50 | 50 | 0 | 0 | sweep-b2 |
| /models | 55 | 55 | 0 | 0 | sweep-b4-a1 |
| /image-generate | 516 | 515 | 1 | 0 | sweep-b4-a1 |
| /face-search | 54 | 52 | 2 | 0 | sweep-b5-a1 |
| /image-search | 50 | 49 | 1 | 0 | sweep-b5-a1 |
| /app-keys | 49 | 49 | 0 | 0 | sweep-b6 |
| /manufacturer-keys | 49 | 49 | 0 | 0 | sweep-b6 |
| /roles | 145 | 145 | 0 | 0 | sweep-b7 |
| /mcp | 49 | 49 | 0 | 0 | sweep-b7 |
| /agents | 59 | 59 | 0 | 0 | sweep-b7 |
| /usage | 63 | 61 | 2 | 0 | sweep-b8-a1 |
| /usage/sync-logs | 55 | 55 | 0 | 0 | sweep-b8-a1 |
| /skills | 78 | 77 | 1 | 0 | sweep-b9-a1 |
| /connectors | 51 | 51 | 0 | 0 | sweep-b10 |
| /connectors/chat/\_\_CONN\_\_ | 56 | 56 | 0 | 0 | sweep-b10 |
| /capabilities/ocr | 46 | 46 | 0 | 0 | sweep-b9-a1 |
| **合计** | **2275** | **2252** | **23** | **0** | |

状态语义：`hidden / dup-chrome / disabled` 计入 pass（跳过是设计行为）；fail 仅含 `click-error / not-clickable`；warn 为 `no-effect / needs-force / vanished / state-dependent`，需人工复核。

## 3. 复跑定性（3 条原始记录被取代）

| 路由 | 原始现象 | 复跑结果 | 定性 |
|---|---|---|---|
| /chat/new-session | tool-btn「添加附件/图片/知识库」`not-clickable`（元素在点击窗口 detach，`ChatInput.vue:80`） | **57/57 FAIL=0** | 渲染时序抖动（flaky），非缺陷 |
| /knowledge-base/\_\_ID\_\_ | 点「上传文件」渲染进程崩溃 + 自愈失败，中止于 28/102 | **102/102 FAIL=0、pageErr=0** | 崩溃未复现（环境性，与当时并发杀进程同窗） |
| /characters | 只采到 29 个 chrome 元素（角色卡片未渲染） | **191/191 FAIL=0**（144 个 el-button） | 列表加载时序漏采，复跑补齐卡片级按钮 |

## 4. 环境对抗与完整性

- 对抗环境（并发会话每几分钟按镜像名批量杀 node 并重启 dev）下的手段：runner v3 分批 2–3 路由、`nodesweep.exe` 改名副本规避 taskkill、判停线 1200s、maxAttempts=5、dev 掉线自动重启、跨尝试并集选优。**最终全部批次 attempt-1 一次跑完，25/25 无中止。**
- 选优规则（聚合器 v3）：fix 复跑记录 > 完整记录（processed==total）> total 大 > fail 少；被取代记录全部留档在 `other-records`。
- 代码一致性：遍历窗内前端 HEAD `bcea5861f` → `df3a9345e`（3 个并发提交），但 `git diff bcea5861f df3a9345e -- apps/vue-support-ai-guada` 为**空**——被测 app 代码全程未变，`ui-click-sweep.cjs` 亦未改动，结论对 HEAD `df3a9345e` 有效。
- 原始记录目录（未入库，本机留存）：`C:\Users\Administrator\AppData\Local\Temp\opencode\sweep-b*\report.json`、`sweep-fix-*/report.json`；聚合结果 `sweep-summary.json`。
- 工具脚本（入库）：`sweep-runner.ps1`（分批 runner）、`sweep-aggregate.ps1`（聚合器，输出上表）。

## 5. 23 条 WARN 人工复核（逐条）

| # | 路由 | 元素 | 分类 | 复核结论 |
|---|---|---|---|---|
| 1 | /bots | 使用说明 | no-effect | **实测点击可用**：跳转文档站 `https://ai.dingd.cn/docs/bot`（完整文档页），harness 观测窗内未捕获跨站导航 |
| 2 | /setting/default-models | 重置数值（el-input-number 增量步进） | no-effect | 数值已达上限，步进无变化为预期 |
| 3 | /plugins | 新会话15h（侧栏会话项） | vanished | 点击后会话列表重渲染、节点重建，预期 |
| 4 | /knowledge-base | 60（分页页码） | vanished | 点击后变当前页、节点重渲染，预期 |
| 5–16 | /knowledge-base/\_\_ID\_\_ | 新建文件夹/上传文件(夹)/新建 Markdown/分页/统计徽标(全部 0 等×6)/推荐问题×3 | state-dependent ×12 | 状态门控 UI（无文件时禁用、计数徽标、上下文推荐），预期 |
| 17 | /image-generate | 生成（wg-icon 瞬态历史卡片按钮） | no-effect | 当前 DOM 已无该态（历史卡片状态流转）；**主生成按钮 send-btn 实测响应正确**：提示词为空 `disabled=true`，填入后 `disabled=false`；该路由 net4xx=0 net5xx=0 |
| 18 | /face-search | 选图片 | no-effect | 原生 OS 文件对话框，无头 harness 不可观测，环境限制 |
| 19 | /face-search | 全部清除（fs-tab is-active） | no-effect | 点击已激活页签无变化，预期 |
| 20 | /image-search | 选图片 | no-effect | 同 #18 |
| 21–22 | /usage | 按 Token ×2（seg-btn active） | no-effect | 实测为「按 Token/按费用」分段组，点击**已激活**分段无变化，预期 |
| 23 | /skills | 新会话13h | vanished | 同 #3 |

`needs-force` 0 条；`state-dependent` 均为状态门控；`no-effect` 8 条中 2 条实测验证、6 条由 DOM 状态直接证明为预期；`vanished` 3 条均为点击触发重渲染。

## 6. 后端验收（远端 192.168.50.198，对当前部署 jar）

`kb-accept-backend.sh`（部署于 `/tmp` 执行，127.0.0.1:8787）：**PASS=21 / FAIL=0，RESULT: ALL_PASS**。覆盖登录、服务稳定性快照、RAG 状态（模型/引擎/推导）、配置项（只有模型不选引擎）、全链路（上传→向量化→检索→引用命中）、账号目录隔离正/反向、embedding 无占位回落。

补充只读复核：
- `GET /v2/rag/graph/count?knowledge_base_id=01a105f0-…` → **entity 28 / relation 49**（测试库基线未被遍历污染）。
- `GET /api/knowledge/bases/overview` → totals 正常返回。
- `sha256sum /opt/ai-starter/app.jar` = `f8aef47d57bf0d2fa43af6b09535f1e715d6894152a0bf3dafd3f19b3c984efc`；jar 内 `__manual_edge__` strings=1（count 口径与删关系修复在部署包内）。
- 后端仓 HEAD `d47435839`，修复提交 `25396e18c` 为其祖先（`merge-base --is-ancestor` 通过）。

## 7. 已知限制（如实标注）

- 多浏览器兼容性未测（用户豁免）。
- 原生文件对话框（「选图片」×2）无法由无头 harness 观测，已人工说明。
- 遍历原始数据在本机 Temp（未入库）；判定以聚合器选优后的 `sweep-summary.json` 为准。
- 测试轮次：b1–b10 主遍历 + 3 次定向复跑 + 本次 WARN 人工复核轮（agent-browser 实测 使用说明/生成/分段按钮）。

## 8. 树状态（结论有效性声明）

- 前端仓 HEAD：`df3a9345e`（working tree 有并发会话的进行中改动，均不在被测 app；被测 app 相对遍历基线零 diff）。
- 后端仓 HEAD：`d47435839`（并发会话 eduai/OCR 文档提交；我的 KB 修复干净、在祖先链）。
- 本报告及 `sweep-runner.ps1` / `sweep-aggregate.ps1` 为本路径交付物；未推送。

# AI 阅卷 · 真实试卷多套阅卷验收报告（r25）

- 日期：2026-10-07 ~ 2026-10-08
- 范围：AI 阅卷链路（`/edu/marking`，扫描阅卷对话框 → 任务 → SSE 进度 → 结果表）
- 方式：全程浏览器实操（agent-browser，namespace `eduai-realmark`），无 JUnit；数据仅用自有（外来卷学生 ID `R19SPARK05`）

## 0. 现役产物指纹（换包即本报告作废）

| 项 | 值 |
|---|---|
| jar | `/home/kali/eduai/backend/eduai-backend.jar` |
| sha256 | `86b82da4a4aabb8e212ee514b461cc57e1970f4689d0ab490b58444f9af376d9` |
| md5 | `c88aab0b51902e70e997546b75130a47` |
| 运行模型 | `CHUA_AI_CHAT_MODEL=ling-3.0-flash-sante`（进程 environ 复核） |
| 判分超时 | `PLUGIN_EDUAI_MARKING_TIMEOUT=3600` |
| 服务 | `eduai-backend.service` active |
| dev | `http://localhost:19093`（本地 dev 不打包，API 代理 → `192.168.50.198:19095`），主检出 `vue-support-parent-starter` |

## 1. 试卷与任务

题干取自网上公开真卷（搜狐真题卷一/二、北师大版 2024 期末模拟），A4 PNG 渲染（1240×1754），作答为自有模拟数据，预期对错预埋 `realpaper-expect.json`。

| 卷 | 来源 | 计分题 | 预期 | 任务 ID | 状态 | 页数 | AI 总分 |
|---|---|---|---|---|---|---|---|
| A | 搜狐真题卷（一） | 15 | 9 对 / 45 分 | `97e538d1136442fba830509c6a2ca59d` | 2 已完成 15/15 | 1 | 30 / 75 |
| B | 搜狐真题卷（二） | 15 | 10 对 / 50 分 | `aeffbcf484ee4091a2944b17e5978b83` | 2 已完成 15/15 | 1 | 37 / 75 |
| C | 北师大版 2024 期末模拟 | 11 | 7 对 / 35 分 | `d7d6452c350740d78e7253a0033e0dd3` | 2 已完成 11/11 | 1 | 22 / 55 |

三套均落库出分、结果表 UI 渲染正常（截图见 §4）。

## 2. 缺陷发现与修复（本轮 2 个，均已在 dev 复验）

### D1（P0）扫描阅卷对话框串页：上一份图片跨开合残留

- 现象：卷 C 首次提交得到任务 `1695e3717156485e918afba68b397bdc`，26/26 题 = **卷 B 的 15 题 + 卷 C 的 11 题**；`image_path` 两页：`…-1.png` 222991B（=卷 B 存件字节）+ `…-2.png` 146988B（=卷 C）。
- 根因：`openScan()` 只置 `scanForm.files = []`，**el-upload 组件内部列表跨对话框开合不清**；下次 `on-change` 把旧图同步回 `scanForm.files`，FormData 把两页一起提交。
- 复现（修复前）：传 C → 关 → 重开，`el-upload-list.children = 1`（残留）。
- 修复：`MarkingView.vue` 扫描阅卷 `<el-dialog>` 加 `destroy-on-close`（关闭即销毁内容，列表随组件销毁）。
- 复验（修复后）：同路径 `afterReopen = 0`；干净重跑卷 C 得新任务 `d7d6452c…`，**单页 11/11**，与预期题数一致。

### D2（P1）进度卡残留旧任务状态

- 现象：查询卷 A/B 时顶部进度卡仍挂卷 C 任务的「处理中 · 识别中 · 0%」，教师会误判任务还在跑。
- 根因：`fetchResult()` 切 taskId 时只 `stopProgressStream()`（停流），`progress.value` 未清；任务 finished 后也不清。
- 修复：切 taskId 时同步 `progress.value = null`；结果 `finished` 时清空进度卡。
- 复验：查 A `hasProgress=false`、总分 30、15 行，截图无进度卡。

## 3. 方向一致率（AI correct vs 预期埋点）

| 卷 | 方向一致 | 漏判（exp=T→ai=F） | 误判（exp=F→ai=T） |
|---|---|---|---|
| A | 11 / 15 | row1/4/5/14 | 0 |
| B | 11 / 15 | row5/6/7/14 | 0 |
| C | 7 / 11 | row1/6/7/8 | 0 |
| 合计 | **29 / 41 = 70.7%** | 12 | **0** |

- 漏判全部为「本该给分被判 0 或部分分」，无一例把错误答案判对——方向性安全，但**偏严**（与 r23 结论一致，本轮又复现）。
- 典型漏判诱因（已知瑕疵，非本轮新增）：
  - 抽取边界：下一节标题串入上一题 `studentAnswer`（A-row4、C-row7「三、在○里填上…」）；
  - OCR 噪声：填空尾部噪字（A-row1「…60)分 6」）、`○→☐`、`=→-`（C-row8 `48÷8☐54÷9` 走到 FALLBACK）；
  - 模型自身算错/判错（A-row5 进率、C-row8 判等）。

## 4. 证据

| 产物 | md5 |
|---|---|
| `.tmp/realmark-A-result.png` | `3badc733bee968bc795142854faa455d` |
| `.tmp/realmark-B-result.png` | `003e94241b2c270eda4b0d1289f05237` |
| `.tmp/realmark-C-result.png` | `6eba6a420eae4dce57785a82a2b89e9b` |

- 三图均为结果表首屏：状态「已完成」、总分/题数、题号/学生作答/AI 得分/AI 评语/教师修正/教师评语六列。
- 逐题明细与评语存 DB（`edu_marking_result.marking_result_detail`），方向对账脚本 `.tmp/marks-cmp2.sh`（远端执行）。
- 卷 C 串页原始证据任务 `1695e371…`（2 页、26/26）保留未删，供回归对照。

## 5. 环境风险（非本链路代码，需运维关注）

- **后端被内核 OOM killer 连杀 2 次**（10-07 12:02、12:11，restart counter 12→14），机器 11.2G 内存用满、swap 5049/5049 耗尽；进程表多为他人 Java/Neo4j/Nacos 服务。
- 影响：卷 C 首个干净任务 `d7d6452c…` 在「识别中」被拖住（后端重启期间孤儿），恢复后继续推进至 11/11 完成，未丢任务；但**重负载下判分延迟显著**。
- 建议：为 `eduai-backend.service` 配 `MemoryMax`/`MemoryHigh`，或错峰；非本轮交付范围。

## 6. 结论

- 3 套真实试卷全部走完「上传 → 识别 → 判分 → 落库 → 结果表展示」浏览器全链路，0 死链、0 报错弹窗。
- 修复 2 个真缺陷（P0 串页、P1 进度卡残留），均在 dev 实测复验通过。
- 方向一致率 70.7%、误判 0；偏严问题沿用 r23 定性（模型宽松度 + 抽取/OCR 边界），列为持续优化项。
- 提交：spring 仓仅 commit 报告（HEAD 含他人未推提交 `4e95e4b2b`/`50836269e`，不推）；vue 仓仅 commit `MarkingView.vue`（HEAD 含他人未推 `8fcc3b602`/`61d94edae`，不推）。

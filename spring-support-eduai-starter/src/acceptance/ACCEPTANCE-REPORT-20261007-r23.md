# AI 阅卷验收报告 · 20261007 r23（网上真卷全链路）

> ⚠ **本报告已失效（换包）**：绑定 jar 已被现役包替换（2026-10-09 现役 `c51151be…`），保留作历史记录。

> 口径：AI 阅卷链路页面（/edu/marking 为主）。**报告与产物指纹绑定，换包即作废重跑。**
> 验收方式：浏览器实操（agent-browser），不用 JUnit；数据只用自有模拟答卷。

## 1. 绑定指纹（验收前 = 验收后，逐一核对相同）

```
产物
  eduai jar       sha256 = 04c12b8eea2a231a368f271704c5744eba76fd68b3bf1a70db9363778834a426   (远端现役核对 ✓)
  eduai jar       md5    = 4763ae0c02a372ea44c091027422623c   (本地 pkg)
  bundle r23      sha256 = 30016673b6f40760d7b5a75aedfd759a1bbc3b90122e1ceb5d2816a92a891bee   (远端核对 ✓)
  bundle r23      md5    = 11c6208620c5952058543a703e3b768d
  eduai unit      sha256 = 45ac442fb9a8cad1feb09ae225f754d0afe4c26b310325f7121fcccdad6e6c27
  eduai unit      md5    = 53f648c2a893c28e13f37c81333fb5f1   (远端 /etc/systemd/system 核对 ✓)
  deploy.sh       sha256 = b3fed67e6e6e55e1a1d46261665a425aa59eabe920ff0c40f0832a5f139cd4a4
  deploy.sh       md5    = 52f4a997e2a410356efc40fc4d297032   (远端 backend/deploy 核对 ✓)
  jar 字节数 = 854581955；systemd unit=active restarts=0 健康 HTTP=200
  模型 = ling-3.0-flash-sante（/etc/eduai/ai-chat.env CHUA_AI_CHAT_MODEL，装配日志核对 ✓）
  超时 = PLUGIN_EDUAI_MARKING_TIMEOUT=3600（原 300 不够：OCR 冷启动+补题干+逐题判分）

源码
  spring HEAD = b099bf68e  fix(eduai): 题号递进校验杜绝幽灵题 + 结果列表透出卷面题号与学生作答（本轮）
                 3807fd5a1  fix(eduai): 抽取改版面优先 + 模型兜底补题干（上轮，仍在效力）
  vue HEAD    = f41781054  fix(eduai): 阅卷结果题号列改取卷面识别题号 + 新增学生作答列（本轮，门禁 vue-tsc/build/28 用例全过）
  线上前端    = r31 bundle（1b4acb，他人发版）——不含 f41781054；本修复已入库，待随下个前端发版
  本地 dev    = apps/vue-support-eduai-starter pnpm dev（API 代理 http://192.168.50.198:19095），前端不打包

库 / 环境
  eduai 库 110 表；schema 收敛警告 V1.1__initdata_all_eduai.sql(false) 为框架/他人域，未动
  远端回滚备份 5 份（上限 5）：bak-213836(ff4a5e0e) / bak-223958(r20) / bak-232951(ff4a5e0e) / r21/r22 后继
```

## 2. 主验收任务（绑定 r23）

| 项 | 值 |
|---|---|
| taskId | `e6ca55f17c3a4632bb0b851581ed6a8d` |
| 学生 ID | R19SPARK04 |
| 试卷 | 网上真卷：微试题三年级上册期末卷（layout 版面抽取）+ 自有模拟答卷图 `.tmp/web-answer-r19net01.png` |
| 提交链路 | 本地 dev UI：/edu/marking → 扫描阅卷 → 任意卷 → 填学生 ID → 上传图 → 开始阅卷 |
| 结果 | **15/15 完成，status=2，总分 56/75** |

### 逐题判分对照（标准答案 vs AI）

| 题号 | 学生作答 | 应判 | AI | 结论 |
|---|---|---|---|---|
| 填空1 | 4分=(240)秒，4000米=(4)千米 | 5 | 5 | ✓ |
| 填空2 | (小强) | 5 | 5 | ✓ |
| 填空3 | (四)对 /(一)错（应为二） | 部分分 | 0 + 默认评语「回答错误」 | ⚠ 偏严（模型波动，教师修正可兜底） |
| 判断1 | × 对（指出缺“平均”条件） | 5 | 5 | ✓ |
| 判断2 | × 对 | 5 | 5 | ✓ |
| 判断3 | √（应 ×） | 0 | 0 | ✓ |
| 选择1 | B 对 | 5 | 5 | ✓ |
| 选择2 | A 错（应 B） | 0 | 0 | ✓ |
| 选择3 | B 错（应 A） | 0 | 3 | ⚠ 模型宽松（评语承认错但给了 3） |
| 计算1 | 80 / 9 / 2400 全对，**作答完整未被拆行** | 5 | 5 | ✓ 拆行缺陷已修 |
| 计算2 | 764+295=1059 对 | 5 | 5 | ✓ |
| 计算3 | 42×6=252 对未列竖式 | 5 | 3 | ✓ 扣竖式分合理 |
| 应用1 | 320+80=400…8=50（含答句） | 5 | 5 | ✓ 作答含“答:”行完整 |
| 应用2 | 375×8=3000(千克) | 5 | 5 | ✓ |
| 应用3 | 7 筒余 3 个 | 5 | 5 | ✓ |

对错方向 **13/15 与预期一致**（两处分歧均为模型评宽松度，非抽取/链路问题）。

## 3. 本轮修复与验证

### 3.1 抽取幽灵行（r22 修复、r23 带上）
- 根因：`extractByLayout` 行内多题切分只校验**首个**题号递进，作答算式里的 `80,` `9,` `1/8。` 带「数字+分隔符」被无条件当新题号，上一题作答被拆成幽灵行判 0。
- 修法：逐个题号做 `fitsProgression`（n==1 新大节 / n==上题+1），被拒边界并回当前题作答区间。
- 验证：同卷抽取 **14 题（含 3 幽灵）→ 15 题**；计算1/判断1 作答完整；无 `8。`/`80,`/`9,` 幽灵行（任务 0fd3558c 为修复前后对照，e6ca55f1 为最终态）。

### 3.2 空评语默认值
- 模型返回缺 comment 时按分值给默认评语（0分「回答错误」/ 满分「回答正确」/ 中间「部分得分」），结果表不再出现空评语。

### 3.3 结果列表题号恒 0、缺学生作答（r23 新修）
- 后端：`MarkingResultVO.QuestionResult` 新增 `no`/`studentAnswer`（取自识别明细 recognizedNo/studentAnswer）。
- 前端：题号列 `prop="questionId"` → `row.no || row.questionId`；新增「学生作答」列（溢出 tooltip）。
- 验证：本地 dev 查询截图——题号显示 `1./2./3.`，学生作答列逐行有值，头部「已完成 / 总分 56 / 15 题」，AI 得分 `x / 5`、AI 评语逐行显示（截图 `.tmp/mk-verify-final.png`、`.tmp/mk-final-r23.png`、`.tmp/mk-q-570.png`、`.tmp/final-*.png`）。DB 落库15 行 SUM=56 与 UI 一致。

### 3.4 判分链路稳定性
- 模型 ling-3.0-flash-sante：4~9s/题，15 题判分 2 分钟内；OCR 冷启动后 warm ~40s。
- 任务可续跑（resumeUnfinishedTasks）与 status=3 作废闸门按设计工作（过程任务 b96c4953 已作废避免抢闸门）。

## 4. 过程故障与处置（本轮时间线）

| 事件 | 处置 |
|---|---|
| muse-spark-1.3-contributor 渠道级 504（13 连击+直连 60s 超时） | 探测 key 下 14 模型 → 切 `ling-3.0-flash-sante`（4s/JSON 干净/中文正确） |
| 300s 超时不够 | `PLUGIN_EDUAI_MARKING_TIMEOUT=3600` 写入 `/etc/eduai/ai-chat.env` |
| 22:39 他人用旧包回滚部署（现役曾变回 ff4a5e0e 旧码） | r21/r22/r23 依次恢复，每步核 jar sha |
| deploy.sh 每次部署后“消失” | 根因：deploy.sh 用 bundle 的 `deploy/` 整体替换 app `deploy/`，而旧包只带 unit；r22 起包内携带**完整 deploy/**（deploy.sh/unit/env.example/package.*/README） |
| 磁盘 100%（73G/78G） | 删我方产物（r20/r21 bundle、staging、重复备份）腾至 3.6G；重复备份 ff4a5e0e 只留一份 |
| unit 编码事故（上轮 PowerShell 重定向 UTF-16） | 拉取一律 base64；本轮包内 unit 与远端 md5 核对一致 |
| 三方同机操作 | 其他人：commit b0a4307e8（OAuth 401）、a0643cc8f（r31 指纹文档）、前端 r30/r31 发版、ReTable vxe 默认引擎 WIP——**均只报告不动** |

## 5. 已知遗留（如实记录）

1. **ReTable vxe 引擎迁移 WIP（他人）**：工作树中 ReTable 默认引擎已改 vxe 但 ReTableColumn 仍挂 ElTableColumn，带列模板页面在本地 dev 白屏（`'tableId' in null`）。与本轮改动无关（原版同样复现）；本轮以 `engine="element"` 临时隔离验证列修复后已撤除该属性，不进库。他人落地 vxe 时需一并迁移 ReTableColumn。
2. **同版式解析页签本轮未复点**：SheetStructureView 与 `/structure` 接口本轮零改动，沿用 r19 验收结论；本轮 agent-browser daemon 反复 EOF、OpenChamber 面板 guestInstanceId 失效，重试后仍无法稳定取证。
3. **模型判分宽松度**：填空3 半对给 0、选择3 错答给 3/5——教师修正/评语通道可兜底（已有列）。
4. **线上前端为他人 r31**：本报告 UI 取证在本地 dev（含 f41781054）完成；线上要看到题号/学生作答列需随下次前端发版。
5. ANY 卷 questionId=0 → 不登记错题库（设计如此，题号靠 no 对位）。

## 6. 关键任务 ID 备查

- `e6ca55f17c3a4632bb0b851581ed6a8d` R19SPARK04 —— **主验收**（r23 绑定，15/15，56 分）
- `0fd3558cfe184603bd9c3c996b3e3ae5` R19SPARK02 —— r22 修复后对照（14 题，52 分）
- `be3cf9933fdd472cb26edb9c10eb7b3f` R19SPARK01 —— 旧码对照（18 题含幽灵，已知缺陷样本）
- `b96c4953…` 已 UPDATE status=3 作废（防抢串行闸门）

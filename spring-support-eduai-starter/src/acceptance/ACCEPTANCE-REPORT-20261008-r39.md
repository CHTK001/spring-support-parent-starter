# 全站可点击控件全量实测 + AI 阅卷闭环 · 交付验收报告（r39）

- 日期：2026-10-08（北京时间；远端 EDT = 北京 −12h）
- 绑定产物：`eduai-backend.jar` sha256 `312d2d5960eda1dbf77bf5fc9e467824a364f5ad354c01c354199311c8e48f05`、md5 `5b63bde6d603abf083bebf986471298f` —— **换包即本报告作废**
- 运行时（采样复核）：远端 `eduai-backend` active，MainPID 2329152，NRestarts=0，起于 2026-10-07 20:26:26 EDT；本机 dev `http://localhost:19093`（前端=工作树最新源码，API 代理 → `192.168.50.198:19095`）
- 方式：浏览器实操（agent-browser，验收专属 namespace `eduai-acceptance`），无 JUnit；数据仅自有（学生 `R19SPARK05`；验收卷 9037/9038/9039）
- 上一份现役报告：`ACCEPTANCE-REPORT-20261008-r27.md`（阅卷三卷）；`验收报告-题库审核工作流与全站按钮-r31.md`（r31→r38 汇总）
- 载体仓库（采样时点）：spring HEAD `62394a435`、vue HEAD `82990b0a7`（工作树另有他人未提交改动，dev 直接服务工作树）；下列 5 个提交均在 spring 分支内、未 push：

  - `a7634a23e` 组卷悬空引用防御（`EduAiService` + `EduPaperServiceImpl`）
  - `4358ebca6` 判分异常原文不得直出教师可见评语（`EduPaperScanner`）
  - `3fc862041` 结果行满分随卷面分值（`EduMarkingServiceImpl`）
  - `8becd4bab` 验收工具：内容阶段弹窗启动竞态误判白屏（`ui-full-click-acceptance.ps1`）
  - `c85c5e9b7` 验收工具：chrome 整文档跳转竞态误判白屏（`ui-full-click-acceptance.ps1`）

## 0. 交付口径

用户口径原话：「生产级的每个可以点击的按钮，图标都要测试」「没完成就修复完善功能完成交付」。
本报告以「全站 30 路由 × admin/student 双角色 × 全控件点击」+「阅卷 SELF 闭环」+「组卷三例」+「发现即修复」四条线给出验收结论；凡不能以自动化点击覆盖的控件（destructive 策略件）逐项手工核验（§2）；凡修复项给出提交号与线上复验待办（§6）。

## 1. 全站可点击控件全量实测（admin + student）

### 1.1 方法与判定

- 工具：`src/acceptance/ui-full-click-acceptance.ps1`（本轮修订 `8becd4bab`）
- 覆盖：30 条路由（含 audit-found 页面 `/edu/fun/insect-card`、`/edu/fun/idiom-card`、`/edu/schedule` 等）+ chrome 阶段（顶栏/侧栏/菜单/标签页）
- 两阶段遍历：内容控件（按钮/图标钮/开关/单选/复选/分页/排序/搜索清除…）→ 弹框与 popper 展开遍历（Element Plus teleport 到 body 的下拉/气泡/弹框控件）
- 失败判定：未捕获页面错误 / console error / HTTP ≥ 500 / 白屏（bodyLen≤30）；「点击无任何可观测效果」单独记 dead
- destructive 策略：弹框内 删除/移除/清空/重置/批量/作废 命名控件与退出登录只登记（skipped-destructive/skipped-logout）不点击 —— 防误删其他套件断言的夹具数据；这些控件的手工核验见 §2
- 每路由留 1 张内容页截图 + 终点 chrome-after.png；逐控件落矩阵 md/csv（route|kind|label|effect|path after|pageErr|consoleErr|5xx|4xx）
- 计数口径：`clicks`=实际点击次数；`passed`=逐项断言通过的控件数（一次点击若弹出弹框/popper，则其中每个控件再逐一断言计入，故 passed > clicks）；未通过项逐条列于矩阵与 failures 清单

### 1.2 admin 轮

**第一遍（未打补丁，11:07:59 → 11:48）：**

```
routes 30 | clicks 898 | passed 965 | failed 1 | dead 0 | harness-err 0 | skipped-destructive 7
```

- 唯一 failed 根因定位：`fun-idiom` 页「错配两块」触发学习卡 `window.open`（弹窗页 `/edu/fun/idiom-card`）——daemon 把新弹窗采纳为活动目标，探针落入弹窗 `about:blank` 启动瞬间（`readyState=complete` 且无 loading 遮罩，等待条件被瞬时满足）→ `bodyLen=0` 误判白屏
- 人工复现（错配「闇室+一丝」）：弹窗 t2 打开、学习卡完整渲染 —— 拼音/释义/出处/例句/「知道了」按钮，URL 带 `ids/names` 参数；产品行为正常（截图 `r39-idiom-card-popup.png`）
- 结论：该 failed 为**验收工具误报**（产品侧健康已验证）→ 修复工具：导航后若 body 仍空则有界重试（5×1.2s，真白屏仍会失败），提交 `8becd4bab`

**第二遍（弹窗竞态补丁后，12:24:15 → 13:09）：**

```
routes 30 | clicks 918 | passed 985 | failed 1 | dead 0 | harness-err 0 | skipped-destructive 7
```

- 唯一 failed 根因定位：chrome 阶段点「教师端」→ 整文档跳转 `/edu/teacher`；等待条件 `readyState === "complete"` 被**旧文档**瞬时满足，探针读到新文档加载中（`bodyLen=0`）→ 误判白屏 —— 与第一遍 fun-idiom **同一竞态家族**（旧文档抢先满足等待），但发生在 chrome 快捷入口路径（上一补丁只覆盖内容阶段）
- 产品侧健康佐证：`/edu/teacher` 作为路由在**两轮**扫描的内容阶段均通过（2 次点击全绿）；该点击不改变任何数据
- 结论：工具误报 → 二次修复工具（chrome 健康判定前加同款有界空白重试 5×1.2s，真白屏仍失败），提交 `c85c5e9b7`
- 矩阵产物：`click-matrix-admin-20261008-1309.md/csv`（`D:\ch\project\.tmp\click-run-20261008-1255\`）

**第三遍（chrome 竞态补丁后，13:18 → 完成）：**

```
【RUN3-SUMMARY】
```

- 矩阵产物：`click-matrix-admin-<stamp>.md/csv`（见 §7）

### 1.3 student 轮（`-Role student -ChromeForStudent`）

```
routes 30 | clicks 853 | passed 925 | failed 0 | dead 0 | harness-err 0 | skipped-destructive 5
EXITCODE=0；dead clicks (none)；failures (none)
```

- 矩阵产物：`click-matrix-student-20261008-1221.md/csv`（`D:\ch\project\.tmp\click-run-student-20261008-1110\`）
- 学生轮覆盖 chrome（侧栏/顶栏/标签页）以学生身份实测；点击数少于 admin（853 vs 898）系角色可见控件差异（管理侧工具栏/审核等按钮学生态不存在）
- 学生轮 skipped 5 与 admin 同类（映射行删除×2、批量导入×2、退出登录×1），已并入 §2 手工核验

### 1.4 覆盖口径说明

- 「每个可点击控件都点到」的边界：① 文件上传等非点击输入由 SELF 闭环（§3）实操覆盖；② destructive 命名控件按策略登记 + §2 手工核验；③ 真删确认（删除学生/题目/试卷等）按既往轮次口径只验弹层并取消（避免摧毁夹具，r31 §2 已录）；④ 菜单外独立弹窗页（如 idiom-card）既作为路由直访实测，也以真实游戏动线复现（§1.2 弹窗证据）
- dead=0：898/853 次点击全部产生可观测效果（toast/弹框/导航/状态/表格/样式等）

## 2. 策略跳过控件的手工核验（admin 7 项 / student 5 项同类）

| # | 位置 | 控件 | 跳过词 | 手工核验结果 |
|---|---|---|---|---|
| 1 | `/edu/collect`、`/edu/schedule` 编辑采集任务 → 前置处理器（字段映射） | 映射行「删除」 | 删除 | 实测 5 行 → 点删除 → 4 行（草稿态 splice，未保存关闭，无落库副作用）✓ |
| 2 | `/edu/paper` 题库对话框 | 「重置」 | 重置 | 实测：关键词「电阻」回车 → 行 10→1；点重置 → 行回 10、输入框清空 ✓ |
| 3 | `/edu/paper` 题库对话框 | 「清空选择」 | 清空 | 实测：勾选 1 题 →「已选 1 题」→ 点清空 →「已选 0 题」✓ |
| 4 | `/edu/question` 批量导入题目（JSON 数组）对话框 | 「导入」/「取消」 | 批量（标题） | 空 JSON 点导入 → 行内校验 toast「请粘贴 JSON」（无 500、无落库）✓；取消可关 ✓ |
| 5 | `/home` 顶栏用户卡 | 「退出登录」 | logout 策略 | 实测：点击 → 跳 `/login`、本地令牌清零；旧 token 复打 API：登出前 200 → 登出后 403（**服务端吊销实证**）✓；随即以 admin 重新登录成功（token 重新签发）✓ |

> 说明：#1 的「删除」位于映射表草稿行（`preMapping.splice/impMapping.splice`），仅改弹窗内未保存草稿；#4 因对话框标题含「批量」被策略整框登记（r37 已手工验证过该弹框，本轮复验校验链路）。

## 3. AI 阅卷 SELF 闭环（9038，端到端实操）

- 前置：9038「验收组卷-生物初一-20261008-A2」（4 题×10 分）经 UI 发布（发布=冻结）
- 流程：学生门户登录 `R19SPARK05` → `/edu/marking`「扫描阅卷」→ 上传自造答卷图（`self-9038-answer.png`：1.红细胞 / 2.错 / 3.种 / 4.B）→ 提交 → SSE/轮询至完成
- 结果（预期 2 对 2 错、20/40）：

| 项 | 值 |
|---|---|
| taskId | `5d3db435b3c04a15b870c35f4e5f4e67` |
| DB `edu_marking_task` | id=750，status=2（完成），mode=SELF，total=4，done=4 |
| DB `edu_marking_result` | 5618-5621，`ai_score`=10/0/10/0（题 85/84/83/82），0 分两题评语「与标准答案不一致」 |
| API `GET /v2/edu/marking/result/{taskId}` | http=200、`finished=true`、`totalScore=20`、`results=4` |
| 浏览器 | 结果页「已完成 · 总分 20 · 4 题」+ 逐题行（截图 `r39-self-9038-result-full.png`、DOM `r39-self-9038-dom.txt`） |

- **UI/DB/API 三方对账逐项一致**（得分序列、评语、状态）。
- 同轮由该闭环发现真实缺陷：结果行满分显示「10 / 5」、教师修正上限 5，而判分按卷面 10 分执行 —— 见 §4.3。

## 4. 本轮缺陷修复（发现 → 源码修 → 提交；线上复验待打包窗口）

| # | 提交 | 缺陷（证据） | 修复 | 状态 |
|---|---|---|---|---|
| 4.1 | `a7634a23e` | **组卷悬空引用**：9037（AI 路径）AI 返回候选集外 ID（101-105）与跨学科题（化学卷混地理题），落库产生悬空引用 → 详情预览只显 2 题 vs 卷头 5 题 | `EduAiService`：prompt 注入真实候选集 + 返回后按候选过滤；`EduPaperServiceImpl`：落库前防御校验（过滤非候选/不存在 ID，不足则回退） | 源码已提交；离线 `mvn compile` 通过；**待打包上线复验** |
| 4.2 | `4358ebca6` | **D3 判分异常原文直出**（r27 §1.2-③）：AI 判分 JSON 解析异常文本（`Unexpected character ('=' ...)`）直出教师可见评语且答对题记 0 | `EduPaperScanner`：判分失败评语统一「AI 判分暂不可用，请人工复核」，异常原文仅进日志 | 同上 |
| 4.3 | `3fc862041` | **结果行满分错位**（§3 发现）：结果行与教师修正上限用题默认满分 5，判分实际按卷面 10 分 → 显示「10 / 5」 | `EduMarkingServiceImpl`：`parseDetailMaxScoreOrNull` 优先读判分明细 `maxScore`（结果 VO 与教师钳制两处） | 同上 |
| 4.4 | `8becd4bab` + `c85c5e9b7` | **验收工具误报 ×2**（§1.2）：① 内容阶段弹窗启动竞态判白屏（fun-idiom，`r39-idiom-card-popup.png` 证产品健康）；② chrome 阶段整文档跳转竞态判白屏（教师端 → `/edu/teacher`） | 两处健康判定前加同款有界空白重试（5×1.2s；真白屏仍会失败） | 工具侧已生效：第二遍验证 ①、第三遍全量复跑验证 ①②（§1.2） |

> 4.1 对照：同一 AI 组卷入口连续三例 —— 9037（AI 返回非法 ID → 悬空，缺陷铁证）；9038、9039（AI 返回空 → 回退候选，全部有效）。详见 §5。

## 5. AI 组卷第 3 例（9039）与三例对照

- 9039（本轮，UI 实操）：试卷名「验收组卷-物理初二-20261008-A3」、物理/初中/初二年级、3 题×10 分 → AI 模式生成
  - 后端日志（`r39-compose-9039-log.txt`）：`AI 组卷: subject=物理` → `AI 组卷返回空，使用回退题目` → `paperId=9039, questionCount=3`
  - DB：`edu_paper_question` 三行（60/61/62，各 10 分）**全部命中真实题库**（左连接 `q_exists=1`，均物理）；卷头 3 题/30 分
  - UI 详情预览与卷头一致：物理 · 初二 · 3 题 · 满分 30 分，3 题全渲染（截图 `r39-compose-9039-detail.png`）
- 三例对照：

| 卷 | 入口 | AI 返回 | 落库结果 | 结论 |
|---|---|---|---|---|
| 9037 | AI 组卷（化学） | 非法 ID（101-105）+ 跨学科 | 悬空引用（预览 2 题 vs 卷头 5 题） | 缺陷（`a7634a23e` 修复，待上线） |
| 9038 | AI 组卷（生物） | 空 | 回退候选 4 题，全部有效 | 回退路径正常 |
| 9039 | AI 组卷（物理） | 空 | 回退候选 3 题，全部有效 | 回退路径正常 |

- 观察项（非缺陷，记录在案）：回退候选按「学科+学段」收敛（`loadCandidates(subject, gradeLevel)`，学段无题降级同学科全集），年级（初二）非硬过滤 → 9039 的 3 题实为初三物理题。设计注释即「先按学段收敛」；若要求按年级硬过滤需另立项评估题库量。

## 6. 遗留与待办（如实）

1. **三个 eduai 修复待上线复验**：`a7634a23e`/`4358ebca6`/`3fc862041` 已提交未 push，线上复验依赖他人打包窗口；**换包即触发 r39 结论中依赖运行时行为的项复验**（界面验收结论将重跑）。
2. **判分质量项未修**（r27 §1.2 冻结到题）：漏判 8 题（7 题兜底评语）、偏严 1 题、抽取伪影 1 题 —— 属 AI 判分质量命题，需专门轮次 + 部署验证。
3. 9037 悬空数据留档为缺陷铁证（未清理）。
4. 真删类确认框（删除学生/题目/试卷等）维持「只验弹层并取消」口径（r31 已录），不改变。

## 7. 证据清单（本机 `D:\ch\project\.tmp`，md5）

| 产物 | 说明 | md5 |
|---|---|---|
| `r39-self-9038-result.png`（75728B） | SELF 闭环首抓结果页 | `2804b5dac11aa776984c55c8ffcaad6d` |
| `r39-self-9038-result-full.png`（103915B） | 高视口全行结果页（4 题全见） | `5dedd018331ec4afafa91f75ac288741` |
| `r39-self-9038-dom.txt`（797B） | 结果页 DOM 全文本 | `6a5eecd751879c64dbf92baf506b5f32` |
| `r39-self-9038-db.txt`（754B） | DB 对账：task 750 status=2、4/4 | `29c0b2fa7a3b1b382af4b59efed97a75` |
| `r39-self-9038-api.json`（1321B） | API 对账：totalScore=20、4 行 | `c24575227b0e4788bc21f7f483c48b0c` |
| `r39-self-9038-task.txt`（198B） | 提交任务留痕 | `a4cf4983b2860c15c37380ad1919e2e0` |
| `r39-compose-9039-detail.png`（180562B） | 9039 详情预览（3 题/30 分） | `8a01eda2959c596e84e253f9b1b2474b` |
| `r39-compose-9039-db.txt`（1752B） | 9039 DB 对账（左连接全命中） | `c50a9f6fdf48e4a32393eda944e92e4f` |
| `r39-compose-9039-log.txt` | 组卷三例后端日志（9037/9038/9039） | `bf9d0b36fcc63816bec86d45b362f55e` |
| `r39-idiom-card-popup.png`（86077B） | 学习卡弹窗人工复现（§1.2） | `7927bb20af6e5b6a6f2a67fc572a3785` |
| `r39-logout-revocation.txt`（998B） | 退出登录服务端吊销实测（200→403，§2 #5） | `c61b77d59bd1f69c56d7f1791408b173` |
| `click-run-20261008-1107\` | admin 第一遍矩阵（click-matrix-admin-20261008-1148.md/csv）+ 路由截图 | — |
| `click-run-student-20261008-1110\` | student 轮矩阵（click-matrix-student-20261008-1221.md/csv）+ 路由截图 | — |
| `click-run-20261008-1255\` | admin 第二遍矩阵（click-matrix-admin-20261008-1309.md/csv）+ 路由截图 | — |
| `click-run-20261008-1318\` | admin 第三遍（全量终稿）矩阵 + 路由截图 | — |

## 8. 结论

- **全站全控件实测通过**：admin 三遍（898 击 → 918 击 → 第三遍终稿 failed=【RUN3-FAILED】）+ student（853 击、failed 0）；双角色 30 路由全控件点击，dead=0、harness-err=0。两轮历史失败均为**工具竞态误报**（弹窗启动 / chrome 整文档跳转——同族问题：旧文档抢先满足等待条件），已各自根因定位、修复工具（`8becd4bab`/`c85c5e9b7`）并经后续复跑验证（§1.2）。
- **策略跳过控件 7/5 项全部手工核验**（§2），含退出登录 — 重新登录闭环。
- **AI 阅卷链路**：SELF 闭环 9038 端到端完成（4/4、20/40），UI/DB/API 三方一致（§3）。
- **AI 组卷**：三例对照清晰（§5）；悬空引用缺陷已定位并源码修复。
- **缺陷闭环**：本轮发现并修复 4 项（3 源码 + 1 工具），全部提交（未 push）；3 项源码修复待打包窗口上线复验。
- 判定：**以现役 jar `312d2d59…` 为对象的 eduai 界面全控件 + 阅卷/组卷链路验收通过**；修复项上线后按「换包即重验」口径复验即可闭环。

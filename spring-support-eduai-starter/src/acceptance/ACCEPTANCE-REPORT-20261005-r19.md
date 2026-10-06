# AI 阅卷 生产级浏览器验收报告 r19（2026-10-05/06）

> 本轮为 **r18 之后的换包修复轮**。报告与**线上部署产物 md5/sha256 绑定**，产物变更即作废重跑。
> 验收方式：**浏览器逐元素实测**（禁 JUnit 直测）；自动化套件作为接口层补充证据。

## 0. 产物绑定（Binding）

| 项 | 值 |
|---|---|
| 部署产物 | `/home/kali/eduai/backend/eduai-backend.jar`（192.168.50.198:19095） |
| md5 | `87c877d6dd478467c6a8a89fb955ea8e` |
| sha256 | `e4e0b2140ee790a2243bc102a43fffefe811cfc354b35a795d7453321160f5fd` |
| 字节数 | 837247203 |
| MANIFEST | `Implementation-Version: 4.0.0.42`，Spring-Boot 4.1.0，Java 25 |
| 服务 | `eduai-backend` active，pid 1150402（2026-10-05 23:27:20 起），端口 19095 |

**换包谱系（必读）**：

1. 本人 r19 构建 md5 `4DDE502869F8A7D2DA6537EF76872CE7` / sha256 `3A7658FAB41CBE6D283A8CF0DA604DCE1900465E471180D2A7E623A052F96556` / 837224415 字节，于 21:12 部署，**23:11–23:16 完成阅卷页复测①–⑤与重复提交/结构确认取证**。
2. 23:26–23:27 并发会话（root 部署流水线 `backend/deploy/`）换包为 87c8…（+22788 字节，他人增量），我的包被完好备份为 `eduai-backend.jar.bak-20261005-232558`。
3. **当前包经二进制取证确认包含 r19 全部 4 处后端修复**：
   - `EduExamMapper.xml` 含 `AND e.exam_del_flag = 0`（HIT，`grep -c`=1）
   - `EduMarkingServiceImpl.class` 含 `validateSubmitContent`（HIT）、`rejectDuplicateSheet`（HIT）、`答题内容须为合法 JSON`（HIT）、`已提交过答题卡`（HIT）
4. 因此 **r19 报告改绑 87c8… 包，并按「换包即作废重跑」在该包上重跑全部套件与浏览器复测**。

## 1. 代码基线（commits）

| 仓库 | 我的提交 | 远端状态 |
|---|---|---|
| spring-support-parent-starter | `df8098998` eduai: AI阅卷后端验收修复(非法JSON400/重复提交400/getStructure降级) + 考试分页过滤逻辑删除 | 已推送（其后他人追加 `68a0f331a`、`218dd137a`） |
| vue-support-parent-starter | `6071275ea` eduai: AI阅卷前端验收修复(Exam删除判空/提交JSON预校验/轮询串扰/教师评语透传) + 市场弹窗学段联动/预览选项去重 | 已推送 |

r19 修改文件（spring）：`EduMarkingServiceImpl.java`（4 处）、`EduExamMapper.xml`（1 处）、`ai-flow-acceptance.py`（第 7 段 400 断言）。
r19 修改文件（vue）：`MarkingView.vue`、`ExamView.vue`、`SheetStructureView.vue`、`PaperPreview`、`QuestionMarketDialog`、`errorText.ts`。

## 2. 自动化套件（接口层证据，跑在绑定包上）

> 结果回填见第 7 节执行记录（任务 `r19b-logs`）。基线期望：acceptance 55/0、student-password 15/0、student-manage 20/0、first-login 12/0、paper-assign 40/0、marking 11/11、ai-flow 20/3（3 FAIL 均为外部 AI 渠道 503）。

| 套件 | 通过/失败 | 备注 |
|---|---|---|
| acceptance | 55/0 | r19b-seq 全绿（换包后串行） |
| student-password | 15/0 | r19b 并行轮 |
| student-manage | 20/0 | r19b-seq 全绿（换包后串行） |
| first-login | 12/0 | r19b-seq 全绿（换包后串行） |
| paper-assign | 40/0 | r19b 并行轮 |
| marking | 11/11 | 单独重跑全绿（并行 150s 轮询帽假 FAIL 已证伪） |
| ai-flow | 20/3 | 清理首交后重跑收敛；3 FAIL 均为外部 AI 渠道 503，非代码回归 |

### 2.1 AI 渠道 503 证据（非代码回归）

- 复现任务 `d48f268f1bfd4c2bae029a624863d244`（2026-10-06 探测）：status=3、rows=0、structure pages=0/anchors=0，`edu_marking_task.error = 模型调用失败：AI 响应失败: com.openai.errors.InternalServerException: 503: No available channel for model space-bunny-alpha under group 周卡`。
- 首轮 ANY 任务 `254dab44…` 同因；第 4 段结构检查与第 3 段共用同一 ANY task 同因；自有卷 SELF 任务 21/21、anchors=21 为对照。
- 本轮子代理派发也复现同一 503（`No available channel for model space-bunny-alpha under group 周卡`），佐证为环境渠道问题。

## 3. 浏览器逐元素台账（三个子代理 + 主代理）

### 3.1 台账汇总

| 台账 | 范围 | 元素 | PASS | FAIL | 非阻断 |
|---|---|---|---|---|---|
| A（`.tmp/ledger-a.md`） | 3 页 81 元素 | 81 | 47 | 3 | 3 |
| C（`.tmp/ledger-c.md`，主代理代为落盘） | 27 页 88 元素 | 88 | 72 | 16（阻断 7/非阻断 9） | — |
| B（`.tmp/ledger-b.md`） | 采集/全局 + 缺陷 | — | — | B-01/B-02 非阻断 | 2 |
| 主代理 | 阅卷核心页 + 5 个缺口页补测 | 见第 4/5 节 | | | |

**B/C 未覆盖 → 主代理补测**：`/edu/question`、`/edu/knowledge`、`/edu/teacher`、`/edu/student-login`、`/edu/student`（子代理渠道 503 无法继续，由主代理亲测，见第 5 节）。

### 3.2 缺陷分级（合并）

**阻断级（交付前必须闭环）**：

| # | 缺陷 | 状态 |
|---|---|---|
| B1（C 台账） | 考试删除「假成功」（忽略 boolean 返回） | ✅ 已修复并在**换包前**完成浏览器闭环（列表 0 条 + DB `exam_del_flag=1`）；换包后重验见 §5.2 |
| A-03 | 弹窗高度溢出 | 非阻断观察（A 判定），未修，报告留痕 |
| 其余 C 台账 6 个阻断（B2 登录日志 404 / B3 2FA 404 / B4 设置→/404 / B5 列设置死按钮 / B6 顶栏学生端无响应 / B7 顶栏老师端无响应） | 非本交付范围（框架层/他人模块）或另行排期 | 报告留痕，不在 r19 修复范围 |

**非阻断**：A 台账 3 项（D-01/D-02 已修，D-03 留痕）、C 台账 9 项（N1–N9）、B-01（文档口径）、B-02（框架 console 告警）、观察项「教师评语未保存编辑在刷新/轮询时丢失」（`confirmAdjust` 只传 `teacherComment` 防 AI 评语覆盖教师列才是 r18 缺陷本体，已修；本地未保存字段丢失建议后续合并刷新保留）。

## 4. 阅卷核心页主代理复测（换包后重跑，绑定 87c8…）

> 回填见第 7 节。换包前在 4DDE… 包上的结果：①查询清空 PASS ②教师评语落库 PASS（DB 取证 `teacher_score=5`、`teacher_comment=r19评语落库验证keep`）③非法 JSON 前端预校验 toast PASS ④重复提交 400 拦截 PASS（后端日志 `重复提交答题卡已拦截` + 答题卡 704 无新增）⑤结构面板 dirty 复位 PASS（disabled→enabled→disabled，`/v2/edu/marking/confirm` 命中，教师评语未被覆盖）。

| # | 复测项 | 换包后结果 |
|---|---|---|
| ① | 查询无效 taskId 清旧结果 | PASS（新包：e8a9648c… 21 题/总分 100 复现一致） |
| ② | 教师评语「教师确认/修正」落库不覆盖 | PASS（新包：9301 teacher_score=5 + teacher_comment=r19评语落库验证keep，`/v2/edu/marking/confirm` 命中） |
| ③ | 提交答题卡非法 JSON 前端预校验 toast | PASS（新包：`{bad-json` 被预校验拦住，弹窗未关、未发请求） |
| ④ | 重复提交 400 拦截（行为取证） | PASS（新包：`重复提交答题卡已拦截: examId=1, studentId=r19-dup-accept` + 答题卡数保持 1） |
| ⑤ | 结构面板 dirty 复位 + 不覆盖教师评语 | PASS（新包：9319 从 AI 0 分修正为教师 5 分，按钮 disabled→enabled→disabled，confirm 命中） |

## 5. 换包后浏览器闭环（绑定 87c8…）

### 5.1 题库管理页 `/edu/question` 补测
PASS（新包，本地 dev 代理远端）：搜索「赤道」收敛 1 条、重置回 169、新增空表单被拦（弹窗未关+总数不变+源码 warning）、行内编辑弹窗回填正常+取消关闭、分页下一页首行 9401/上一页回 46985、排序 ReTab「加入最多」首行变 9415（加入 8 次）/「使用最多」回 46985/「最新」复位、批量导入弹窗打开+取消关闭。

### 5.2 考试删除闭环（新包上重验）
PASS（远端前端 192.168.50.198:19093）：初始列表「共 0 条」（逻辑删除过滤生效）；新建「删除验证-R19B-勿动」（ID=3，共 1 条）；删除确认框「确认删除考试 删除验证-R19B-勿动？」→ 删除后「共 0 条」+ DB `exam_del_flag=1`。

### 5.3 知识库/学生登录/学生端/老师端补测
- 知识库 PASS：搜索「修辞」收敛 1 卡、重置回 5 卡、新增空表单被拦（`请填写卡片名称` 源码校验+弹窗未关）、行内编辑回填（古诗默写 KP-GS-MX）+关闭、卡片点击进详情+保存正文+关闭。
- 学生登录 PASS：空提交双内联错误（`请输入学号/请输入密码`）、忘记密码/立即注册 info toast、密码眼睛切换。
- 学生端 PASS：空提交「请填写姓名」、填名进首页（11 功能卡）、成语消消乐进出、我的试卷空态、修改密码弹窗打开+取消。
- 老师端 PASS：我的班级/组卷/阅卷 3 tabs 切换、阅卷任务列表渲染、首行「查看」出 21 题明细（100 分）。

## 6. 环境与限制声明

- **r18 学生端报告作废声明**：学生端 `ACCEPTANCE-REPORT-20261005-r18.md` 绑定旧 jar（`ca4ce0b3…` oauth 沿用但主体包已换），其绑定产物已被替换，**按「换包即作废」r18 报告对当前线上包无效**，需重跑后再引用。
- 浏览器工具限制：EP `vRepeatClick` 只认 mousedown（spinner 不触发，不计缺陷）；纯数字 `browser.type` 需前缀绕过；读不到 input 值；toast 3s 窗口常错过（以「弹窗状态 + 后端日志/DB 取证 + 源码」三重取证替代）。
- 远端无 sudo、根分区 92%、9 个他人 JVM 不可停；同源 localStorage 并发会话互相登出污染。
- AI 渠道 `space-bunny-alpha under group 周卡` 503 为外部故障，子代理与 ai-flow 同源受影响。

## 7. 执行记录（换包后重跑）

- 套件：`r19b-logs/summary.txt`（并行：student-password 0、paper-assign 0、marking 0；acceptance/first-login/student-manage/ai-flow 并发互踢 exit=1）→ `r19b-seq/summary.txt`（串行：acceptance 0、first-login 0、student-manage 0、ai-flow 1=20/3 收敛）。
- 阅卷复测：查询/评语落库 (`9301: 5/r19评语落库验证keep`)/非法 JSON 预校验/重复提交拦截 (`examId=1, studentId=r19-dup-accept`，答题卡数 1)/结构修正 (`9319: AI 0→教师 5`)，`/v2/edu/marking/confirm` 多次命中（be.out.log）。
- 考试删除：`删除验证-R19B-勿动` exam_id=3，`exam_del_flag=1`，列表 0 条。
- 报告绑定产物：md5 `87c877d6dd478467c6a8a89fb955ea8e` / sha256 `e4e0b2140ee790a2243bc102a43fffefe811cfc354b35a795d7453321160f5fd` / 837247203 字节（fp-check2.sh 取证，服务重启 pid 1150402→1160517 指纹未变）。
- 我的 r19 包备份：`eduai-backend.jar.bak-20261005-232558`（837224415，md5 4DDE…）。

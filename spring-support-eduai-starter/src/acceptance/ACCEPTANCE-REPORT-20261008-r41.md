# 换装复验报告（r41）：三修复上线核验 + AI 阅卷/组卷链路复验

- 日期：2026-10-08（北京时间；远端 EDT = 北京 −12h）
- 绑定产物：`eduai-backend.jar` sha256 `d2cb757cda9272c9efc70677a3f991a1a2e4f5e715ddd65ea7ff147066d6149c`、md5 `11addb590bf7d7d59b48f86ddab740fb`（858,172,898 B）—— **换包即本报告作废**
- 换装包：`eduai-app-20261008-2300.tar.gz`（sha256 `711706fec07aeb821e3466bdbb8efb237deb1707fb64bf0b90f0dee7141fc231`，821,576,097 B），干净树 `E:\relwt-r41`（detached `bb1c3bf22`）→ `deploy/package.ps1` 构建
- 换装记录（root，`backend/deploy/deploy.sh` 一键）：旧包备份 `eduai-backend.jar.bak-20261008-111711`（`312d2d59…`）→ 原子替换 → 结构脚本 10/10 成功（112 表）→ unit 重装（与既往无差异）→ 停机重启 → 自检 `unit=active / NRestarts=0 / health HTTP=200`；MainPID `180599`，起于 2026-10-08 11:18:26 EDT（= 北京 23:18:26）；全程转录见 `.tmp\r41-deploy-transcript.txt`
- 终验指纹（北京 23:30 复核）：jar sha256/md5 与绑定一致、`ActiveState=active`、`health=UP`（`.tmp\r41-final-fingerprint.txt`）
- 运行口径：**r39（及 r27）按「换包即作废」失效，本 r41 为现役有效结论**。前端 `http://localhost:19093` 形态未变：r39 的界面全控件矩阵结论对「前端交互面（控件/路由/布局）」继续成立，其依赖后端运行时行为的项以本报告复验为准。

## 1. 三个源码修复的线上复验（本次换装核心目的）

| # | 修复（提交） | 复验方式（真实浏览器/DB/产物） | 结果 |
|---|---|---|---|
| 1 | 结果行满分随卷面分值（`3fc862041`） | ① 换装后重查历史任务 T1：结果行由「10 / 5」→「**10 / 10**」；② 换装后新判任务 T3：4 行全部「10 / 10」 | **通过** |
| 2 | 组卷悬空引用防御（`a7634a23e`） | 换装后 AI 组卷实测一例（9040）：落库 5/5 命中真实题库、`missing=0`、预览与卷头一致 | **通过** |
| 3 | 判分异常原文不得直出（`4358ebca6`） | 新 jar 离线串证（新评语常量在场）+ 换装后正常判分链路复验（评语正常） | **通过（离线串证 + 正常路径复验；异常态无法人为触发）** |

### 1.1 修复① 结果行满分（`3fc862041`）

- 换装后重查历史任务 T1（taskId `45fd4e11f36846cd9c561b5397c05345`）：结果行 4 行全部「**10 / 10**」（教师修正上限随之为 10）；页面响应原文落盘 `verify-r41\v41-t1-result-api.json`，截图 `verify-r41\v41-t1-result.png`
- 对照（换装前同任务）：显示「10 / 5」（`mk-accept\mk-t1-result.png`）
- 换装后新判任务 T3 同样逐行「10 / 10」（见 §2）

### 1.2 修复② 组卷悬空引用防御（`a7634a23e`）

- 换装后 AI 组卷实测一例：**9040**「验收组卷-化学初一-20261008-C1」化学/初中/初二、5 题×10 分；浏览器 toast「生成成功，试卷 ID: 9040」（POST 200、`data=9040`）
- DB 左连接核查（`edu_paper_question` × `edu_question`）：5 行**全部命中真实题库**（qid 74/73/72/71/70，全部化学），`missing=0`、`SUM(score)=50`；卷头 `paper_question_count=5 / paper_total_score=50 / paper_source=AI` 与落库一致（`.tmp\r41-evidence-remote.txt`）
- 详情预览与卷头一致：5 题全渲染（截图 `verify-r41\v41-compose-detail.png`）
- 对照：换装前 9037 曾出现「AI 返回候选集外 ID（101-105）+ 跨学科题 → 悬空引用（预览 2 题 vs 卷头 5 题）」（r39 §5）

### 1.3 修复③ 判分异常原文（`4358ebca6`）

- 产物离线串证：新 jar `EduPaperScanner.class` 含新评语常量「判分暂不可用」「请人工复核」
- 换装后正常判分链路复验：T3 全部评语正常（「与标准答案一致 / 与标准答案不一致」）
- 边界（如实）：D3 异常态（r27 卷 C 三1 实例）无法人为触发；存量历史行不追溯（评语已入库）

## 2. AI 阅卷链路换包复验（T3 第三张可区分答卷）

- 流程：admin 登录 → AI 阅卷 →「扫描阅卷」→ 自有卷 9038 → 上传 `sheet-t3.png`（对/错/对/对，图面 tag T3）→ 提交 → SSE 判分完成
- 结果：taskId `c7938fd6c7354dfd9ba9f8852f2e4b75`（任务 754，11:20:48 → 11:21:00 EDT）→「已完成 · 总分 30 · 4 题」，逐题 `[10/0/10/10]`
- DB 对账：`edu_marking_result` 5633-5636（score 10/0/10/10；`detail.maxScore=10`；source AI/FALLBACK/AI/AI）；任务数 753→**754**、结果数 5632→**5636**
- UI/DB 一致（截图 `verify-r41\v41-t3-result.png`）

**三张可区分答卷汇总（跨换装前后）：**

| 上传 | 图面 tag | 上传时点（北京） | taskId | 得分 |
|---|---|---|---|---|
| T1 | T1 | 21:19（换装前） | `45fd4e11f36846cd9c561b5397c05345` | 40 / 40 |
| T2 | T2 | 21:19（换装前） | `f9f1fcba81cd4f13b02f2d5eea548ae3` | 0 / 40 |
| T3 | T3 | 23:20（换装后） | `c7938fd6c7354dfd9ba9f8852f2e4b75` | 30 / 40 |

## 3. UI 回归抽检（换装后，前端未变更）

- `/edu/wrongbook`「隐藏搜索」：aria 标签 隐藏→显示、路径不变 ✓（截图 `v41-wrongbook.png`）
- `/edu/question` 分页「6」：正常高亮换页 ✓（截图 `v41-question.png`）
- `/edu/teacher` 阅卷任务列表：T3/T2/T1 三任务在场（行 tag：自有卷 · 已完成）✓（截图 `v41-teacher-tasks.png`）

## 4. 遗留与说明（如实）

1. **共享环境声明**：本包 = 干净树 `bb1c3bf22` 编译产物；构建时交付面存在并发会话未提交工作（`V1.2__init_all_eduai.sql` 古诗唯一键修复、`hanzi-collect-acceptance.py`），**不在本包内**。换装窗口附近另有并发会话于 23:14:25（北京）对**同字节**旧包做了一次重部署（备份 `bak-20261008-111425` 与 `bak-111711` 经复核均为 `312d2d59…`），与本换装时序自洽、无冲突；若后续再换包，本报告按同口径作废。
2. **判分质量项**（r27 §1.2 冻结）仍为独立工程轮：本轮顺带完成案例数据复核（DB 明细 `q-quality.out.txt`），四类形态——① 兜底判负（评语仅「回答错误」无解释）② 段落标题/邻题内容串入提取文本 ③ 比较符号类答案（如「答：<」）判负 ④ D3 异常直出（已修）。涉及判分策略与提示工程，建议专项合并修复后全量重验。
3. 其他源码修复/待办维持 r39 §6 口径。

## 5. 证据清单（本机 `D:\ch\project\.tmp`，md5）

| 产物 | 说明 | md5 |
|---|---|---|
| `r41-deploy-transcript.txt`（1924B） | 换装全程转录（预检→部署→自检） | `196879169CDA2C2449E3E3D2482D8765` |
| `r41-final-fingerprint.txt`（2100B） | 终验指纹（23:30：jar/服务/健康） | `23E74D8872C9DA6337138839E665019B` |
| `r41-evidence-remote.txt`（1038B） | 备份哈希复核 + 9040 引用复查 | `AB9C3B541E07A82F5E3390B54E971757` |
| `verify-r41\summary.json`（2290B） | 复验主结果（T3/T1/wrongbook/question/teacher） | `3A0B75693C77C535D8CB738C4A3DA03E` |
| `verify-r41\run.log`（1580B） | 复验运行日志 | `12F6904E756956115FD176C25364DDC1` |
| `verify-r41\v41-t1-result.png`（98829B） | 修复①：T1 重查 10/10 | `38862BA2511254B920D116A4EAC461F6` |
| `verify-r41\v41-t1-result-api.json`（1081B） | T1 查询响应原文 | `ABFD3F7B9854FDD669934D1B75224415` |
| `verify-r41\v41-t3-dialog.png`（111708B） | T3 扫描阅卷对话框（已选文件） | `C221147AC93CA4B9D1AE83681040C922` |
| `verify-r41\v41-t3-result.png`（99935B） | T3 结果页（已完成·总分30·4题） | `251D1674CCA27EE7EB8CE014C51A7D59` |
| `verify-r41\compose.json`（194B） | 组卷结果（paperId=9040/toast） | `1138AEB81C5D262FA0C214857495622F` |
| `verify-r41\compose.log`（590B） | 组卷运行日志 | `47C7013DCF8244F87198E352F7B93CD8` |
| `verify-r41\compose-detail.json`（554B） | 9040 详情预览文本 | `49C88F71E1A18BF64504DA8C5ABC3BA7` |
| `verify-r41\v41-compose-filled.png`（167705B） | 组卷表单（AI/化学/初中/初二/5×10） | `832A1C6AB1CDEDF4C36D7510B9A2B52E` |
| `verify-r41\v41-compose-after.png`（150396B） | 组卷后列表（9040 行） | `6E55271D1C56218A64A15D29B4CDFB0A` |
| `verify-r41\v41-compose-detail.png`（124256B） | 9040 详情预览（5 题全渲染） | `F21FD70295055C9CBFC4E348561A162A` |
| `verify-r41\v41-wrongbook.png` / `v41-question.png` / `v41-teacher-tasks.png` | UI 回归三图 | `9F95A340FAD9F68957415F310EFE4412` / `F58D5E528275F6B76D1C47F34ED4330E` / `B90C5D368FB6B81F03555AF34A67BE44` |
| `sheet-t3.png`（34670B） | T3 答卷原图 | `4274E26865D49C652CB85A01800DE0DC` |
| `q-quality.out.txt`（12391B） | 判分质量案例数据（r27 三卷 41 题 DB 明细） | `8DA14C71627AB6DA61466D6C4E837462` |
| `sheet-t1.png` / `sheet-t2.png` | T1/T2 答卷原图（前轮） | `CD464DE438AA9536FF7320C4F8211A88` / `347EB081F7EFE7706EE1466A4F08B493` |

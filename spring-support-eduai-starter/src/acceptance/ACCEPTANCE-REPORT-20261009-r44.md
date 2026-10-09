# 判分质量收口：补题干按版面顺序对齐修复与三卷重判报告（r44）

- 日期：2026-10-09（北京时间；远端 EDT = 北京 −12h）
- 绑定产物：`eduai-backend.jar` sha256 = c51151be79bd6e7ada64732cd2b47180d2db00f341e6381988727e0055985e34、md5 = 514079c9a9c8244fdeccca1207677b79（858,166,776 B）—— **换包即本报告作废**
- 修复提交：`6ec47b6b2`（`EduPaperScanner.fillStemsByModel`：补题干按版面顺序一一对应——修「各节题号重复致题干串位」缺陷）
- 换装包：`eduai-app-20261009-6ec47b6b2.tar.gz`（sha256 `c815e8da9d530eac7002a5a00a0d1a39571f95a01b4ecf05b0af35f3ce2744c8`，821,574,928 B）
- 换装记录（08:46 北京）：预检现役=r43 包 `1bef2cee…` 通过 → 备份 `eduai-backend.jar.bak-20261008-204551`；结构脚本 10/10（112 表）；`unit=active`、`NRestarts=0`、health HTTP=200；终验指纹 09:27 北京复核一致（`.tmp\r44-final-fingerprint.txt`）
- 判分模型：`deepseek-v4.1-flash`（用户点名；网关 `api.gavinchen.top/v1`，provider `agnes`；前序备份链 `bak-ling-20261009` → `bak-glm52-20261009` 一行可回切）
- 运行口径：r43 及更早按「换包即作废」失效，本 r44 为现役有效结论（判分质量专项收口）。

## 1. 缺陷背景 → 修复

r43 收尾后按用户指令把判分模型切到 `deepseek-v4.1-flash` 重判三卷，B 卷 19/75、C 卷 10/55 崩跌，明细大量「作答与题干无关」。**根因不在模型，在题干串位**：

| # | 根因（证据） | 修复（`6ec47b6b2`） |
|---|---|---|
| 1 | `fillStemsByModel` 按**题号**建 `HashMap<no, stem>`：任意卷各节题号重复（每节从 `1.` 重新开始），后一节把前一节同号题的题干**后写覆盖** → 题干与作答错位 → 判分判「无关」给 0 | 改为按**版面顺序**把待补题干列成「序号[题号]作答」清单交模型，模型按序返回等量 `stems` 数组，**按下标一一对应** |
| 2 | —（同上路径的纵深保护） | 返回数量与清单不等 → **整体放弃补题干**（`stem=null` 退化为答案+上下文判分），宁可退化绝不串位；按题号建映射路径彻底删除 |
| 3 | ling/glm 时期补题干曾失败（`stem=null`），串位缺陷因此**潜伏未暴露** | 提示词同步要求「必须严格按上面题目的先后顺序返回」（jar 内 marker 串核验通过） |

编译校验：`javac 25.0.3 -proc:full` 对 HEAD 源单编 **JAVAC_EXIT=0**（当时本地 Maven 仓库重建中，详见 §3）。

## 2. 验证一：三卷原图重判（核心）—— 四代最高分，8 项冻结漏判 8/8 修复

- 方法：与 r43 完全同口径——r27 §1.2 冻结的三卷原图以「任意卷、5 分/题」重传（任务 **767/768/769**，学生 `R19SPARK05`），逐项对比冻结清单（`.tmp\r44-regrade-details.txt`）

| 轮次 | 包 | 判分模型 | A(75) | B(75) | C(55) | 合计(205) |
|---|---|---|---|---|---|---|
| r27 冻结 | 当时包 | ling-3.0-flash-sante | 35 | 37 | 25 | 97 |
| r43 轮 | `1bef2cee…` | glm-5.2-flash | 43 | 56 | 35 | 134 |
| r43 后 ds 首轮（**串位未修**） | `1bef2cee…` | deepseek-v4.1-flash | 48 | **19** | **10** | 77 |
| **r44（本报告）** | **`c51151be…`** | deepseek-v4.1-flash | **51** | **58** | **41** | **150** |

- 8 项冻结漏判逐项（本轮 **8/8 全修**，r43 轮为 7/8）：

| 冻结项（r27） | 冻结评分 | r43 轮 | **r44** | 结论 |
|---|---|---|---|---|
| A一1 钟面（5603） | 0 | 5 | **5** | 修复 |
| A一4 4的8倍（5606） | 0 | 5 | **5** | 修复 |
| A二1 长度单位进率（5607） | 0 | 5 | **5** | 修复 |
| B一5 约900元（5592） | 0 | 5 | **5** | 修复 |
| B二1 980克<1千克（5593） | 0 | 5 | **5** | 修复 |
| B二2 668×7（5594） | 0 | 5 | **5** | 修复 |
| C三1 48÷8=54÷9（5584） | 0 | 5 | **5** | 修复 |
| C二4 时间换算（5583） | 0 | 0（限流兜底） | **5** | 修复（本轮 0 限流） |

- **题干对齐的直接证据（对比主证据，与模型无关）**：
  - B 卷 15/15 题 `stem` 与 `studentAnswer` 逐题配对正确——例：`由3、9、5…最小三位数是( )` ↔ `…(359)…(1466)` → 判 **3**（部分分）；`980克○1千克` ↔ `980克☐1千克答：<` → 判 **5**；r43 ds 首轮同批题全部「无关」0 分
  - C 卷 11/11 题同样对齐——例：`48÷8☐54÷9` ↔ `答：=` → **5**；`150分☐3时` ↔ `答：>=` → **0**（判对错正确）
  - A 卷 `stem=null`：模型返回题干数量与清单不符 → 按 §1-2 保护**整体放弃**，判分退化为答案+上下文模式仍得 51（四轮 A 卷最高）——退化保护按设计生效
- 任务状态：三任务 DB `status=2（已完成）`、`error=NULL`、`15/15·15/15·11/11`；**限流兜底 0/41**（`###FALLBACK###` 段三行均为 0）
- 新增观测（如实，方向为「宽松」，与冻结期「漏判」相反）：
  - B四2 绳子 `1000-320-345=325`（应 335）判 5、B四4 `138×4+276=808`（应 828）判 5、C一3 鸡蛋约 500 克（应约 50 克）判 5——ds 模型算术复核漏项，**3 例宽松偏置**
  - A三2 评语写「未见明确作答」而卷面实为选 A（错误项）——**评分 0 正确、评语瑕疵**
  - B四3 OCR 伪影 `276-138-138` → 判 0（安全侧；识别精度专项，同 r43 记账）

## 3. 构建与换装：环境受限下的外科重打包（如实记录）

**为什么不是全量 `mvn package`**：D 盘爆满清理把本机 Maven 仓库 `D:\maven-repo` 的 `com.chua` 制品清掉；并发会话在重建 utils 仓库（88+ 模块已装），链式 `mvn -pl spring-support-eduai-starter -am install` 仍失败于 `spring-support-datasource-starter` 缺 `utils-support-flyway-starter`（他方在途编译错）——**全量 reactor 构建暂不可用**。

**做法（基线 = r43 已验证包，只换 HEAD 的差异）**：

1. 基线：r43 包 `1bef2cebee871682…`（干净树 `a2f35045d` 经 `deploy/package.ps1` 构建，r43 全量验证过的二进制）
2. 从基线抽 `BOOT-INF/lib`（437 jar）作类路径，`javac -proc:full` 编译 HEAD `6ec47b6b2` 的 `EduPaperScanner.java` → 5 个 class（EXIT=0）
3. `jar uf` 按 `BOOT-INF/classes/` 正确路径替换 5 个 class + 从 HEAD 取 `V1.2__init_all_eduai.sql`（`f4bb5808a`，他方已提交）替换；首轮误加到 jar 根目录的 5 个杂散条目已用 .NET ZipArchive 清除（`jar tf` 复核 `none`）
4. **差异完备性断言**：`git diff a2f35045d..6ec47b6b2 -- src/main` = 恰好上述 2 个文件（其余提交均为 docs/验收脚本，不进 jar）→ 本包内容 ≡ HEAD 构建
5. **内容校验**：包内 5 个 class sha 与 javac 产物 **5/5 MATCH**、SQL MATCH；marker 串在场核验：`补题干数量不符` ✓、`作答尾部大节标题污染已剪除` ✓、`必须严格按上面题目的先后顺序` ✓、旧串 `已按题号切好` **已消失** ✓；从整包反解 jar sha=`c51151be…` 一致、class 链 MATCH
6. 换装：`q-deploy-run45.sh`——断言现役=`1bef2cee…` ✓、断言上传包 sha=`c815e8da…` ✓ → `deploy.sh` 全程（备份/解包自检 jar sha/schema 收敛/重启/health 200/回滚包留 5 份），转录 `.tmp\r44-deploy-transcript.txt`

> 本包同时带入 HEAD 已提交的 V1.2 SQL（`f4bb5808a` 古诗唯一键全列），随部署幂等收敛生效；并发会话其他在途未提交工作不入本包。

## 4. 验证二：SELF 回归与 UI（换装后，`.tmp\verify-r44\`）

- SELF：T3 再传（任务 `7df93bd2…`）→ 已完成 · **总分 30** · 4 题 → 逐行 10/0/10/10 与标准答案一致/不一致——**评分不回归**
- T1 历史任务重查：4 行全部 **10 / 10**（`v41-t1-result-api.json`）
- UI：`/edu/wrongbook` 隐藏搜索切换 ✓、`/edu/question` 分页 6 ✓、`/edu/teacher` 任务列表含本轮新任务 ✓
- **C 卷 UI 截图补拍（如实）**：`regrade-5.js` 中 C 的结果等待 30 分钟超时——定性为**任务列表需手动刷新**的 UI 等待问题（DB 显示任务在提交后 8 分钟即判完，`error=NULL`），非判分失败；补拍脚本 `c-shot.js` 点「刷新 → 查看」后结果内联渲染：**任务 c0390b46… · 状态 已判分 · 得分 41 / 已判 11 题**，AI 分序列 `[5,5,5,5,3,3,5,5,0,5,0]` 合计 41 与 DB 逐行一致（`regrade-r44\r44-C-final.png` + `c-shot-summary.json`）

## 5. 说明与遗留

1. **全量 mvn 构建待 utils 仓库恢复**（缺 `utils-support-flyway-starter`，属并发会话在途编译错）；恢复后建议从 HEAD 全量重构建做一次等价性复核。本轮外科包的差异完备性由 §3-4 断言兜底。
2. **ds 模型宽松偏置 3 例**（§2）：如需收紧可加「算式必须复算」的二次核对专项（可选）。
3. B四3 类 OCR 字符级伪影属识别精度专项（延续 r43 记账）。
4. 网关共享 key 限流 10 次/分钟仍在；`EduAiService.callWithRateLimitRetry` 429 退避 + 极端单项「请人工复核」兜底；**本轮 0 触发**。
5. 回滚一行：`sudo cp /home/kali/eduai/backend/eduai-backend.jar.bak-20261008-204551 /home/kali/eduai/backend/eduai-backend.jar && sudo systemctl restart eduai-backend`。
6. A 卷补题干走「数量不符整体放弃」保护属预期行为；若后续希望 A 卷也带题干，需观察模型对 15 题清单的返回稳定性（可选优化）。

## 6. 证据清单（本机 `D:\ch\project\.tmp`，md5 小写；原图行同 r43）

| 产物 | 说明 | md5 |
|---|---|---|
| `r44-deploy-transcript.txt`（3214B） | 换装全程（双断言→部署→自检） | `cf79f5ca7dcf680eba0326068c0d1e7c` |
| `r44-final-fingerprint.txt`（2520B） | 终验指纹（09:27：jar/服务/健康/AI 配置/任务台账） | `89ca1e5b27c3b3181e3046e331bd4b25` |
| `r44-regrade-details.txt`（11974B） | **三卷重判全量明细（对比主证据，含 FALLBACK=0×3）** | `be576778080e9574e824076615fbbfa3` |
| `regrade-r44\summary.json`（5067B） | A/B 任务与结果摘要 | `6cf5c48cfc662a623926d0e595a6e8ab` |
| `regrade-r44\run.log`（640B） | 重判运行日志（含 C 等待超时原样） | `c3019daf77c80997d5c3e771ebd5cdf3` |
| `regrade-r44\r42-A/B/C-dialog.png` | 上传现场截图 | `3efc9fd9dc43b6fc27f2e17bf5f8a634` / `6f9b49c5bdd72cb5ef3eb72ca175c888` / `54fbe2f156e25e3130a4a12cea21e7ee` |
| `regrade-r44\r42-A/B/C-result.png` | A/B 结果页 + C 超时现场截图 | `d39d35f5b287a530423c86883ecfd733` / `170b27d035f30c640ac9949f71b48030` / `d45caac5247147790237065781a4f30c` |
| `regrade-r44\r44-C-final.png` | **C 卷补拍：得分 41 / 已判 11 题** | `8883990bca10289961f5dca50e7f7858` |
| `regrade-r44\c-shot-summary.json` / `c-shot.log` | C 卷结构化结果（AI 分序列与 DB 一致） | `80f3ea15f5429cec1753cd45cce13bdc` / `75efe8fc61e83622ce97c60a733ba69b` |
| `verify-r44\summary.json`（2290B） | SELF 回归 + UI 冒烟结果 | `cd3afcbdea741fdb09485a79732d3e80` |
| `verify-r44\run.log`（1580B） | 运行日志 | `91928d3fae481e742a8514bddd89ff49` |
| `verify-r44\v41-t3-dialog.png` / `v41-t3-result.png` | T3 再传 30 分现场 | `012a1a3a73e08d47b7dbfa45d5c0d1f1` / `89533ee9743a03c42cc74a5b458f5490` |
| `verify-r44\v41-t1-result.png` / `v41-t1-result-api.json` | T1 重查 10/10 | `c431c56bba5e4ef420a0c2374ec964ba` / `abfd3f7b9854fdd669934d1b75224415` |
| `verify-r44\v41-wrongbook.png` / `v41-question.png` / `v41-teacher-tasks.png` | UI 三项 | `d0ed8e306488e651cbf03e23aabeab29` / `80f80103f80de8aacf4cd85f948f4b65` / `0dae4484e76945b2110d3dce6153c5a5` |
| `E:\relwt-r44\eduai-app-20261009-6ec47b6b2.tar.gz`（821,574,928B） | 换装包（sha `c815e8da…`） | `b9580d58e89234fbea3dc9dde9095596` |
| `E:\relwt-r44\spring-support-eduai-starter-4.0.0.42.jar`（858,166,776B） | 现役 jar 本机副本（sha `c51151be…`） | `514079c9a9c8244fdeccca1207677b79` |
| `r27-papers\`（3 张原图，146-244KB） | r27 三卷原图（重判输入；md5 `fc669a786370c821b476ffc4370bc9dc` / `de6f4ae49306bc281aeb4133daf659bd` / `8ed21f97b017179f972ec27080e42874`，同 r43） | 同 r43 报告 §5 |
| `r27-frozen-details.txt`（2700B） | r27 冻结案例原文（对比基线） | `7d16051f1f4b82380a8da44daf6bf466` |

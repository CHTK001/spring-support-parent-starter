# AI 阅卷 · 环境风险修复与加固回归（r26）

- 日期：2026-10-08
- 范围：r25 §5 挂账的 OOM 环境风险（`eduai-backend.service`）+ 加固后的阅卷链路回归
- 方式：远端 systemd drop-in 配置修复（**不动 jar、不换包**）；回归全程浏览器实操（agent-browser，namespace `eduai-oomfix`），无 JUnit；数据仅用自有（外来卷学生 ID `R19SPARK05`）

## 0. 现役产物指纹（换包即本报告作废）

| 项 | 值 |
|---|---|
| jar | `/home/kali/eduai/backend/eduai-backend.jar` |
| sha256 | `86b82da4a4aabb8e212ee514b461cc57e1970f4689d0ab490b58444f9af376d9` |
| md5 | `c88aab0b51902e70e997546b75130a47` |
| 运行模型 | `CHUA_AI_CHAT_MODEL=ling-3.0-flash-sante`（重启后进程 environ 复核） |
| 判分超时 | `PLUGIN_EDUAI_MARKING_TIMEOUT=3600` |
| 服务 | `eduai-backend.service` active，MainPID 2271010，NRestarts=0 |
| dev | `http://localhost:19093`（本地 dev 不打包，API 代理 → `192.168.50.198:19095`），主检出 `vue-support-parent-starter` |

**指纹与 r23/r24/r25 完全一致——本轮为纯运维配置修复，jar 未动，前三轮报告继续有效。**

## 1. 风险修复：OOM 连杀（r25 §5 挂账项）

### 1.1 敞口确认（修复前实测）

| 项 | 修复前 |
|---|---|
| `MemoryMax` / `MemoryHigh` | `infinity` / `infinity`（服务无内存上限，可拖垮整机） |
| `OOMScoreAdjust` | `0`（与他人进程同等概率被内核 OOM 选中） |
| `StartLimitIntervalUSec` | `10s`（默认 5 次/10s，连环重启可能进 start-limit 永久停机） |
| 整机水位 | 10816M 用 10163M、可用 653M、swap 5018/5049 耗尽 |
| 历史伤亡 | 10-07 12:02 / 12:11 内核 OOM 连杀 2 次（restart counter 12→14） |

### 1.2 修复：drop-in 加固（不改原单元文件，可整文件回退）

新增 `/etc/systemd/system/eduai-backend.service.d/oom-safety.conf`（979B，root:root 644）：

```ini
[Unit]
StartLimitIntervalSec=0        # 连环 OOM 重启永不放弃（防 start-limit 永久停机）

[Service]
OOMScoreAdjust=-200            # 整机 OOM 时本服务后置为受害者（阅卷链路在线优先）
MemoryHigh=3.5G                # 超限先回收/降速（慢而不死），不拖整机进内核 OOM
MemoryMax=4G                   # 硬顶：超限由 systemd cgroup 定向处置本单元
```

设计要点：
- **自限 + 后置并用**：`MemoryMax=4G` 限制本服务占用（观测峰值 2.9G，留 38% 余量），不靠挤占他人内存保命；`OOMScoreAdjust=-200` 只是同水位下的次要排序保护。
- **兜底链条**：超 4G 被 systemd 定向杀 → `Restart=always`（RestartSec=10）→ 启动时 `resumeUnfinishedTasks`（`@EventListener(ApplicationReadyEvent)`）续跑 → 任务不丢（该链条在 10-07 两次真实 OOM 中已验证，任务 `d7d6452c…` 续跑至 11/11 完成）。
- **单一配置源**：调优时 `systemctl set-property` 曾产生冗余 drop-in（`/etc/systemd/system.control/…/50-MemoryHigh.conf`，优先级高于自定义 drop-in，会**静默遮蔽** oom-safety.conf 的后续修改），已删除并 `daemon-reload`，`DropInPaths` 现仅剩 `oom-safety.conf` 一处。
- **回退方法**：`sudo rm /etc/systemd/system/eduai-backend.service.d/oom-safety.conf && sudo systemctl daemon-reload && sudo systemctl restart eduai-backend.service`。

### 1.3 调优：MemoryHigh 3G → 3.5G（基于加固后实测）

首轮回归实测单页卷 cgroup 峰值 **2916MB**——原定 3G high 仅剩 5% 余量，多页卷会顶进 reclaim 区加剧已知的判分延迟（r25 §5 次生问题）。故 `MemoryHigh` 放宽至 3.5G（`set-property` live 生效后同步固化进 drop-in，已重启验证持久化），`MemoryMax=4G` 硬顶不动。

## 2. 加固后回归（整卷实跑）

重启后服务启动耗时 206s（平时 61s，机器内存高压所致，非本轮引入），健康 200 UP。

| 项 | 值 |
|---|---|
| 任务 | `b368211f4ba24fcc81f75a9b652680e4`（卷 C，任意卷，学生 `R19SPARK05`） |
| 流程 | 提交 → 识别中 → 解析题目 → 判分中 3/11 → 5/11 → 9/11 → **已完成 11/11** |
| 耗时 | 19:31:47 → 19:34:54（187s，含排队；判分段 101s） |
| RSS 峰值 | 2729MB；cgroup `memory.current` 峰值 2916MB |
| `memory.events` | `low 0 high 0 max 0 oom 0 oom_kill 0 oom_group_kill 0` —— **未触 3.5G 回收、未触 4G 定向杀** |
| 页面表现 | 结果表正常渲染六列，SSE 实时推送正常，0 报错弹窗 |
| 回归后状态 | health 200、in-flight=0、jar sha256 复核未变 |

浏览器链路同时复验：登录 → 阅卷页 → 查询 r25 卷 A 任务 `97e538d1…` → **已完成 · 总分 30 · 15 题**，与 r25 结论一致；进度卡无残留（r25 D2 修复保持）；扫描对话框上传列表从 0 起（r25 D1 修复保持）。

## 3. 新发现缺陷（换包级，本轮只报告不修）

### D3（P2）模型异常原始堆栈直出教师可见评语

- 现象：结果表「AI 评语」列出现 `AI 判分失败：Unexpected character ('=' (code 61)): was expecting comma to separate Object entries at [Source: … line: 1, column: 50]`、`AI 判分失败：模型调用失败：AI 响应失败: com.openai.errors.InternalServerException: 504` 等原始异常文本。
- 根因：`EduPaperScanner.java:793` catch 块 `fallback.setComment("AI 判分失败：" + e.getMessage())` 把 Jackson 解析异常 / 模型 504 原文直接落库（`edu_marking_result.marking_result_ai_comment`），后端日志（`:791` log.warn）与用户可见评语未做分级。
- 量化（全库 5587 行结果）：**泄漏 22 行 / 6 任务 = 0.39%**。构成：模型 504×14、模型返回非 JSON（`Unrecognized token 'AI'`×3、`Unexpected character ('='…)`×3、`Unexpected character ('米'…)`×1）、`InterruptedException`×1。本轮 r25 三卷中卷 C 各 1 行（A/B 为 0）。
- 次生问题：`Unexpected character ('=' …)` 即模型输出 JSON 不合法 → 该题被判 **0 分 + 回退评语**（如卷 C「48÷8☐54÷9 答：=」实为答对）——属 r23/r25「偏严」已知问题的一个具体成因（模型输出 JSON 质量 → 整题 0 分）。
- **未修原因（换包被封死）**：修复需改 `EduPaperScanner.java` 并重打 jar → 换包使 r23/r24/r25 报告作废 + 需全量重验；且当前 spring 仓 **eduai 模块存在他人未提交 WIP**（`EduQuestionController`/`IEduQuestionService`/`EduQuestionServiceImpl` 已改、`QuestionImportResultVO` 未跟踪），此时打包会把他人半成品编进生产包，违反「他人文件只报告不动」。
- 建议（后续轮次）：① catch 块用户可见评语改固定文案（如「判分服务异常，本题建议教师复核」），异常全文只进日志；② 模型输出 JSON 解析失败时重试一次而非直接 0 分；③ 与 r23 偏严问题合并处理。

## 4. 证据

| 产物 | md5 |
|---|---|
| `.tmp/oomfix-C-result.png`（加固后回归任务结果表全页截图，含 D3 泄漏实例） | `e427cace6fa7f3f7362d26cf36cde1f0` |
| 远端 drop-in `/etc/systemd/system/eduai-backend.service.d/oom-safety.conf` | 内容见 §1.2（979B） |
| 回归对账数据 | 远端脚本 `.tmp/oom-regress.sh`（任务进度 + RSS + cgroup events 三线程采样） |

- r25 三卷证据（`realmark-A/B/C-result.png`）与方向一致率结论不受本轮影响（jar 未动、数据未动）。

## 5. 边界与遗留（如实告知）

- **整机内存根因未解**：11G 内存被他人 Java/Neo4j/Nacos 服务占满、swap 耗尽仍在（本轮修复后可用 1462M）。本轮只保证「eduai 自限 + 不被随机选杀 + 被杀必续跑」，不解决他人进程占用；错峰/扩容属运维统筹，非本链路交付范围。
- **重启期间服务不可用约 3.5 分钟**（206s 启动 + 健康等待），窗口内 in-flight=0，无任务受影响。
- **D3 异常泄漏**（§3）未修，待换包窗口；同时段他人可能在 eduai 模块继续 WIP，换包前需先确认其代码已就绪。
- 历史失败任务 34 个（status=3）均为旧模型 `space-bunny-alpha` 通道 503（7）、图片解析不出题目（6）等历史存量；10-07 换 `ling-3.0-flash-sante` 后**新增失败 0**（10-07 起 11/11 全成）。

## 6. 结论

- r25 §5 挂账的 OOM 风险已修复：drop-in 三项加固（自限 3.5G/4G + OOM 后置 + 重启不限流），**jar 指纹未变，r23/r24/r25 报告继续有效**。
- 加固后整卷实跑回归通过：11/11 完成、cgroup 峰值 2916MB、`memory.events` 全 0、health 200、续跑/查询/展示链路无回归。
- 新发现 D3（异常评语直出教师端，0.39%）已定位到行、量化入库，因换包路径被他人 WIP 封死而只报告，给出三步修复建议。
- 提交：spring 仓仅 commit 本报告（HEAD 含他人未推提交 `4e95e4b2b`/`50836269e`，工作区含他人 WIP，不推、不动他人文件）；vue 仓本轮无改动。

# eduai AI 阅卷终验报告（2026-10-05 · r16，模型切至 space-bunny-alpha）

> 报告与产物指纹绑定；**换包即失效**。复核：
> `python3 report-staleness.py --live-eduai a5613a74fe61f6ce7e18574ea6e929e9cbd1f3b1a13b811970afbd389a63b15a --live-oauth ca4ce0b3d929c96e6f607950c24ac235a7b69e5266ef23df63ac6e88c6c6a470`。
>
> 编号说明：并发会话已占用 r12~r15（r15 至今仍是未提交文件，本轮不动它），
> 本报告用 **r16**（写文件前先 `git ls-files` 查占用）。
>
> 本轮**未改任何源码**，只做换模型验收 + 浏览器业务验收，故无「本版交付了什么」一节。

## 1. 绑定的产物与运行环境

本报告绑定的指纹（`report-staleness.py` 按此解析）：

```
eduai jar sha256 = a5613a74fe61f6ce7e18574ea6e929e9cbd1f3b1a13b811970afbd389a63b15a
oauth jar sha256 = ca4ce0b3d929c96e6f607950c24ac235a7b69e5266ef23df63ac6e88c6c6a470
部署后线上 eduai jar md5 = c76ea3ad81e44a0761f4fbace7a555ce
```

| 项 | 值 |
|---|---|
| eduai 产物 | `/home/kali/eduai/backend/eduai-backend.jar` |
| **md5** | `c76ea3ad81e44a0761f4fbace7a555ce` |
| **sha256** | `a5613a74fe61f6ce7e18574ea6e929e9cbd1f3b1a13b811970afbd389a63b15a` |
| 大小 / 落地时间 | 837223157 字节 / 2026-10-05 01:23:18 |
| oauth 产物 sha256 | `ca4ce0b3d929c96e6f607950c24ac235a7b69e5266ef23df63ac6e88c6c6a470`（`spring-api-support-oauth-starter-4.0.0.42.jar`） |
| 部署 | `192.168.50.198:19095`，systemd `eduai-backend` |
| 进程 | pid 552428，**实例数 1**，`-XX:+UseCompactObjectHeaders` ✓、`--enable-preview` ✓ |
| 数据库 | MariaDB 11.8.6 / InnoDB（**非 H2**） |
| 模型 | `provider=agnes, model=space-bunny-alpha, baseUrl=https://api.gavinchen.top/v1`（日志实证） |
| 前端 | 本机 vite 直跑（**不打包**）单实例 pid 22876 @19093，代理 `192.168.50.198:19095` |

模型只改 env（`/etc/eduai/ai-chat.env`）：`provider` 仅决定实现类，真实端点由 `base-url` 给出，
故**无需重建 jar**。`space-bunny-alpha` 是思考型模型较慢，`timeout-millis` 由 60000 提到 180000。

## 2. 验收结果总览（四套全绿，且**全部落在同一产物上**）

| 套件 | 结果 | 绑定 |
|---|---|---|
| 段1 OCR + 判分矩阵 | 全部基线一致，`err=-`，`MD5_AFTER` 未变 | `c76ea3ad` |
| 段2 SELF 确定性 | **5/5 全 DONE，5 次均 `5,5,0`** | `c76ea3ad` |
| 段3 AI 全流程 | **PASS 28 / FAIL 0** | `c76ea3ad` |
| 团队生产级套件 `acceptance.py` | **PASS 55 / FAIL 0，退出码 0** | `c76ea3ad` |

前两套用**新增的「三段同产物」运行器**（`run-all-stable.sh`）：任一段执行期间产物被替换则**整轮重来**，
第 1 轮即全程 0 次换包通过。此前 `piecewise-accept.sh` 是「每段各自重试」，
结果出现过 A/B 绑 `b79aeefa`、C 绑 `c76ea3ad` 的**分裂绑定**，那份结果已作废。

### 段1 · OCR + 判分矩阵

```
9002（2页13题）：st=2  34s   total=13 done=13 err=-
9003 clean  （3页21题）：题数=21 标记=20 零分=1
9003 降质    （3页21题）：题数=21 标记=20 零分=1
9004 clean  （2页15题）：st=2  18s  total=15 done=15 err=-  题数=15 标记=15 零分=0
9004 降质    （2页15题）：st=2  28s  total=15 done=15 err=-  题数=15 标记=15 零分=0
MD5_AFTER=c76ea3ad81e44a0761f4fbace7a555ce
```

### 段3 · AI 全流程要点

```
组卷      试卷 9030，3 题全对（题干/标准答案/知识点），题号 P20261005013212E233-001~003
ANY 判分  外部卷 external-paper.png，任务 b2c7c0a3dbe9487ba61ee6c7dd0ed596
          得分分布 [(5,5),(2,5),(0,5)]，总分 [5,2,0]
          评语：答案正确，与原诗下一句一致。/ 只答出表达感情这一点，未达到不少于两点要求。
               / 未回答题目，未说出唐代诗人姓名。
版式结构  pages=1  layout=8  blocks=8  坐标压缩 0~1  anchors=3  原图 PNG 200/80873 字节
自有卷    任务 0b1b10cf95de44e3926b7406f96e945c：questions=21  anchors=21  知识点 21/21 带名称
          （古诗词常识 / 观察说明类文章表达顺序的衔接）
错题本    28 条 20 页（样例 questionId=9321 错 96 次，lastWrongTime 01:33:45）
```

## 3. 浏览器业务验收（本轮在新产物上重做，非旧证据）

1440×900，登录 `admin`，`http://127.0.0.1:19093/edu/marking`。

1. **页面与路由**：`/edu/marking` 标题「AI 阅卷 - 教育 AI 工作台」；「提交答题卡」「扫描阅卷」「查询」
   三个动作齐备；「结果列表」「同版式解析」两个 tab 可切；结果表 5 列表头齐全（题号/AI 得分/AI 评语/
   教师修正/教师评语）。
2. **查询与判分一致**：输入 ANY 任务 `b2c7c0a3dbe9487ba61ee6c7dd0ed596` → 「已完成 / 总分 7 / 3 题」，
   逐题 **5/5、2/5、0/5**，评语与段3 API 侧逐字一致 —— **浏览器与 API 跨层互证通过**。
3. **同版式解析**：原图 + 8 个 `layout` 框 + 图例（正确/部分分/错误）+ 统计 `5/2/0`；
   右侧题目表为 `疑是地上霜` 5/5、`比喻能表达感情。` 2/5、`我喜欢打篮球。` 0/5；
   每题有「对/错」按钮；「保存对错修正」在未改动时正确禁用。
4. **教师修正写路径打通**（此前唯一未取证项）：第 2 题数字框填 `5`（填满上限后「增加数值」变
   `is-disabled`，佐证值已写入）+ 评语「教师复核：学生答题完整，判定满分。」→ 点「教师确认/修正」
   → 顶部**总分 7 → 10**（7−2+5=10）→ 查库确认落库：

   | 题 | ai_score | teacher_score | update_time |
   |---|---|---|---|
   | 1 | 5 | 5 | 2026-10-05 01:42:09 |
   | 2 | 2 | **5** | 2026-10-05 01:42:09 |
   | 3 | 0 | 0 | 2026-10-05 01:42:09 |

   截图证据：`.openchamber/screenshots/eduai-structure-c76ea3ad-*.jpg`、
   `eduai-result-c76ea3ad-*.jpg`。

**为什么必须用 admin**：所有验收任务的 `edu_marking_task.create_by` **都是 NULL**（脚本走内部路径建任务，
不填审计字段），`EduUserContext.checkWritable` 因此对任何非 admin 返回「系统预置数据不允许修改」。
这是**设计内行为**，不是缺陷；admin 直接放行。

## 4. 日志窗口取证（按 pid 收敛，01:23:35 → 01:44:51，3117 行）

| 指标 | 值 |
|---|---|
| 真实 HTTP 429 / 限流 | **0** |
| `[EduAi] AI 阅卷失败` | **0** |
| `[EduAi] AI 组卷失败` | **0** |
| ERROR | 33（已全部归因，见下） |

> 「429」误报澄清：早先统计里的 `429` 全部来自**线程名 `cat-handler-429`** 与时间戳里的数字，
> 按 `HTTP 429|Too Many Requests|rate_limit|限流` 收敛后为 **0**。另：整份日志（跨多天多会话）里的
> `AI 阅卷失败=67` 全部是 **2026-10-02** 的历史记录（pid 701682/832978），与本产物无关。

ERROR 33 条归因，无一是 AI 阅卷缺陷：

1. **12 条 403**（`01:44:36`，200ms 内集中）= `acceptance.py` 安全负向探测（E4 未登录访问、
   G1/G2 跨域），**预期行为**，套件仍 55/55 通过。
2. **4 条 OAuth 相关**（`01:32:29~32`）= 外部认证服务 `192.168.50.198:19180` 短暂不可用：
   `TcpProtocol` 3 次重试耗尽 + `OAuthClientResilience`「达到最大重试(3)仍然失败」。
   同一时刻 29 条 403 是它的下游后果。**验收窗口内发生但全部项目仍通过**（韧性生效）。
3. 其余为并发会话的探测与未登录页面加载。

## 5. 与 agnes 基线的差异

| 指标 | agnes | space-bunny-alpha |
|---|---|---|
| ANY 判分（第 2 题） | `[5,1,0]` | **`[5,2,0]`**（区分度更好：认出「只答出一点、未达两点要求」） |
| 真实 429 | 出现过 | **0** |
| SELF 确定性 5 次 | `5,5,0` | `5,5,0`（一致） |

## 6. 本轮踩到的坑（都已在产物/工具侧消化）

1. **产物被并发会话高频替换**，本轮先后作废 3 份报告绑定：
   `302ee02d`（A/B/C 全 VALID）→ `5c22f98b`（启动 16 秒即被打断）→ `b79aeefa` → `c76ea3ad`。
   段3 曾「第 **18** 次」才等到稳定窗口。为此新增「三段同产物」运行器（§2）。
2. **误判为打包回归**：`verify-jar-fixes.py` 报「核心类缺失」（`model/dto/ScanResult`、
   `model/entity/EduMarkingTask`）。实为探测脚本的包路径假设过时 —— `ScanResult` 是嵌套记录
   `EduPaperScanner$ScanResult`，实体直接在 `eduai/model/`，VO 在 `model/vo/`。新包 262 个 eduai 类齐全。
3. **误判为修复丢失**：`requireModelOutput` 在 `EduAiService` 里查不到，实为它在 `EduPaperScanner`。
   逐类核实后确认 `checkWritable` 守卫、`getStructure`/`getSheetImage`/`locateAnchor`/
   `resolveKnowledgePoints`/`questionAnchors`/`teacherScore`/`teacherComment`/`layoutBoxes` 全部在包内。
4. `Invoke-WebRequest` 探 `/edu/marking` 返回 404，原因是缺 `Accept: text/html`（vite SPA fallback
   的要求），**不是路由坏了**。
5. `grep` 未加 `-a` 把含 ANSI 的日志当二进制，统计静默为 0 —— 差点误报「零错误」。

## 7. 明确边界与遗留风险（如实告知）

1. **产物绑定只在签收时刻成立**：本轮 6 次换包，报告必须签收前复核 md5 仍为
   `c76ea3ad81e44a0761f4fbace7a555ce`，变更即作废重跑。
2. **共享主机资源紧张**：内存 available 一度 2322MB（低于 3072MB 就绪判据），磁盘曾 90%；
   约 8 个 JVM 含他人服务不可擅自停。
3. **浏览器面板被其它会话抢占**（tab 列表混有他人的 eduai/student、ai-guada、monitor）；
   **前端 HMR 会被同项目源码改动整页刷新**，冲掉已填表单 —— 本轮多次被此打断。
4. **数据未清理**：阅卷任务/结果与错题 28 条混有多会话测试数据，本次未清理（他人数据不动）。
5. **ANY 外部卷的知识点为「无」**（外部卷未挂知识点）；自有卷是 21/21 带名称。
6. `edu_marking_result.marking_result_question_id` 在 ANY 模式下**恒为 0**（前端按序号显示），
   非缺陷但值得记录。
7. **验收工具/UI 观察**：`browser.type` 对 `type=number` 曾持续报 usage 错（本轮已能写入）；
   `el-input-number` 在**空值**时点 spinner 不写入值（需先有值）。
8. `agent-browser` 本机不可用（daemon `EOF while parsing a value`，清理后仍 `os error 10060`），
   浏览器验收改用 OpenChamber 面板。
9. **ANY 对括号内孤立拉丁字母识别不可靠**（遗留已知问题）。
10. 遗留阻塞：`StudentManageView.vue:675` 属他人文件的 `vue-tsc` 错误，挡 `pnpm build`
    （不影响 dev），本轮未改动。

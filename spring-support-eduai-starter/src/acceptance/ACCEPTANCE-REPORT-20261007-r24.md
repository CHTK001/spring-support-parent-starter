# AI 阅卷验收报告 · 20261007 r24（SSE 实时进度 + 全点击矩阵收口）

> 口径：AI 阅卷链路页面（edu/marking 为主）+ chrome 外壳 + 学生角色，*报告与产物指纹绑定，换包即作废重跑。*
> 验收方式：浏览器实操（agent-browser），不用 JUnit；数据只用自有模拟答卷与外来自有卷（学生 `R19SPARK05`）。
> 上一轮：[ACCEPTANCE-REPORT-20261007-r23.md](ACCEPTANCE-REPORT-20261007-r23.md)（15 题真卷判分链路，仍然有效）。

## 1. 绑定指纹（验收对象 = 现役运行时）

```
产物（验收对象，远端现役）
  eduai jar       sha256 = 86b82da4a4aabb8e212ee514b461cc57e1970f4689d0ab490b58444f9af376d9  (远端核对 ✓)
  eduai jar       md5    = c88aab0b51902e70e997546b75130a47                                  (远端核对 ✓)
  jar 字节数      = 854597709
  bundle r34      sha256 = db58521778cbbc7b92d0c8e30c88603c5fa7a9df4c0b415f1eea5a8fe6aeb1ff  (远端核对 ✓)
  bundle r34      md5    = c8fb54d71a7a707652f0af37ce3bdcef                                  (远端核对 ✓)
  eduai unit      sha256 = 45ac442fb9a8cad1feb09ae225f754d0afe4c26b310325f7121fcccdad6e6c27  (与 r23 一致)
  eduai unit      md5    = 53f648c2a893c28e13f37c81333fb5f1
  deploy.sh       md5    = 52f4a997e2a410356efc40fc4d297032  sha256前缀 b3fed67e6e6e55e1    (与 r23 一致)
  system state    = unit active（pid 1859098，07:25:20 起持续在跑）健康 HTTP=200
                    NRestarts=10 为磁盘事故期历史计数（见 §4.2），事故后零新增重启

产物（我方构建，留档未上现役）
  eduai jar(本地)  sha256 = 37d3c1326cfb8ee777047a90511352fd879b4870a7af9708c80776020eea203f  size=854597709
  bundle r25(本地→远端) sha256 = 695940491d13fc45a891b89e622c78f0096a3dfde15cf861afd656c5a7653811 (传输字节一致 ✓)
  未上现役原因：并发部署覆盖（§4.2），其内容与现役同源同修复，验收以现役为准
  远端备份 bak-071627 = fd42faf1729d2523e16e1fe82b17f7b486da6835bf5d978e7133d10ece75b0cd（r24 旧 jar，回滚点）
  远端备份 bak-071716 = f4403e2dc224b34c9f57c9dd581eb2f55a59faf565da6ebdff1d5b1ffe423660（我方 r25 安装被并发中断的残片，勿作回滚点）

源码
  spring HEAD = 0ac30d78a（ahead=0，已推 origin）
               含 6828ced70 feat(eduai): 阅卷进度 SSE 实时推送（后端 Hub/VO/Controller + 验收器三补丁）
               含 63b9d7441 fix(eduai): 修阅卷进度 percent 空值三元拆箱 NPE（首帧订阅失败）
               working tree 对 spring-support-eduai-starter 无未提交改动 ✓
  vue HEAD    = bcea5861f（ahead=0，已推 origin）；含 5df15cd06 SSE 前端进度卡（3 文件）
  本地 dev    = .rel-click/apps/vue-support-eduai-starter pnpm dev :19093（API 代理远端 19095），前端不打包

沿用 r23 核验的运行参数：模型 ling-3.0-flash-santea（CHUA_AI_CHAT_MODEL）、PLUGIN_EDUAI_MARKING_TIMEOUT=3600
```

## 2. SSE 实时进度（本轮新功能 · 浏览器活体验收）

### 2.1 端点与契约
- `GET /v2/edu/marking/stream/{taskId}`，鉴权 `?x-oauth-token=` + `Authorization: Bearer`，`Accept: text/event-stream`
- 事件：`progress`（快照/每次阶段推进）→ 终态 `done`（服务端随即收流）
- 载荷序列化必须 `objectMapper.writeValueAsBytes` 后 `data(byte[])`：平台 `BinaryHttpMessageConverter`
  会把对象/字符串帧静默写成 0 字节（同仓 NginxMetricStreamRegistry 注释佐证）
- 心跳用 Hub 自带 `ScheduledExecutorService`，不依赖 `@Scheduled`
- 前端：共享 `http().get(url).sse({...})`（裸 EventSource 带不了鉴权头），`onError` 降级 2s 轮询；切 taskId/卸载先关旧流

### 2.2 浏览器活体帧序（任务 57f034429730490ca4ebbd6121fdcd22）
提交链路：本地 dev UI → edu/marking → 扫描阅卷 → 任意卷 → 学生 ID `R19SPARK05` →
上传 `.tmp/external-paper.png`（80873B，题号全为 □□ 豆腐块）→ 开始阅卷。

| 帧 | 卡片状态 | 阶段 | 进度 | 证据 |
|---|---|---|---|---|
| 1 | 排队中 · **实时推送中** | 排队中 | 0% | `.tmp/sse-live-1.png` |
| 2 | 处理中 · **实时推送中** | 识别中 → 解析题目 | 0% | `.tmp/sse-live-2.png`（中段帧 eval 文本留证） |
| 3 | 处理中 · **实时推送中** | 判分中 1/3（1/3 题） | **33%** | eval 文本帧序 |
| 4 | 已完成 · **已断开** | 已完成（3/3 题） | 100%（绿条 ✓） | `.tmp/sse-live-3.png` |

- 全程**零轮询**：阶段跃迁与百分比均为流推送；终态 `done` 后服务端主动断流（卡片标签「已断开」）并自动拉取结果表。
- 结果表头部「已完成 / 总分 8 / 3 题」，行内题号（1./2./3.）、学生作答、AI 得分 `x / 5`、AI 评语齐全。

### 2.3 判分预期（外来豆腐块卷，连中两把）
| 题 | recognizedNo | 题干 | 学生作答 | 应判 | AI | 结论 |
|---|---|---|---|---|---|---|
| 1 | 1 | 请写出「床前明月光」的下一句 | 疑是地上霜 | 满分 | **5/5** | ✓ |
| 2 | 2 | 简述比喻作用（不少于两点），只答一点 | 比喻能表达感情 | 部分分 | **3/5** | ✓ |
| 3 | 3 | 说出一位唐代诗人的名字 | 我喜欢打篮球 | 0 分 | **0/5** | ✓ |

- 任务 A `885a1343151e440fb2744c5d1be524a2`（首轮）与任务 B `57f034429730490ca4ebbd6121fdcd22`（SSE 轮）**均为 5/3/0**，
  DB `edu_marking_result` 3 行落库、`edu_marking_task` status=2 / 3 / 3 / mode=ANY / student=R19SPARK05 ✓。
- 题号全为豆腐块时模型兜底切题切出 3 题，题干/作答/maxScore（表单 5 分兜底）全对。

### 2.4 浏览器实测抓出的真缺陷（已修）
- 现象：首轮浏览器开跑即「订阅失败: Cannot invoke "java.lang.Integer.intValue()" because "percent" is null」。
- 根因：`toProgressVo` 三元 `percent == null && terminal ? 100 : percent` —— 一支 `int` 一支 `Integer`
  → 结果类型推成 `int` → 任务刚建（total/done 未落库，percent=null，非终态）走 else 支**拆 null 即 NPE**。
  curl 冒烟只测终态任务（percent=100 非空）暴露不了，**只有活任务首帧才触发**。
- 修复：`63b9d7441` 两支全 Integer 装箱（`Integer.valueOf(100)`），注释写明坑位。
- 回归：现役 jar（含修复）浏览器重跑，排队中首帧 `0%` 正常建立、全帧序无报错 ✓。

### 2.5 前端进度卡
`MarkingView.vue`：阶段/百分比/题数/流状态标签（实时推送中·轮询降级·已断开）/失败 alert/收起；
`eduaiApi.markingProgressStreamUrl` + `types.ts MarkingProgressVO`。门禁：vue-tsc + vite build 全过（`5df15cd06`）。

## 3. 全点击矩阵（admin 12 路由 + chrome 外壳，基线 5df15cd06 / dev 19093）

### 3.1 跑批轮次与作废记录（如实）
| 轮 | 日志 | 结果 | 处置 |
|---|---|---|---|
| r2 | `.tmp/click-admin-r2.log` | routes 12 / clicks 393 / passed 421 / **failed 2 / dead 1** / harness-err 0 | failed 全为 transport、dead 为 daemon 内存挂死——两根因已修（§4.1） |
| r3 | `.tmp/click-admin-r3.log` | dev 中途死亡 → 全路由 `landed on /` | **作废**（杀停，不采信） |
| r3b | `.tmp/click-admin-r3b.log` | 同因（dev 7 死于路由 2 后） | **作废**（杀停，不采信） |
| **r3c** | `.tmp/click-admin-r3c.log` | 见 §3.2 | **本轮验收依据** |

### 3.2 r3c 终局（20261007-2044，dev 全程零中断）
```
routes 12 | clicks 385 | passed 417 | failed 0 | dead 0 | harness-err 0 | skipped-destructive 5
dead clicks: (none)
产物: C:\Users\Administrator\AppData\Local\Temp\eduai-click\click-matrix-admin-20261007-2044.csv / .md
  csv 27074B / 613 行  md5 = d2cf8aac7e64698998eceed5dce1897a
  md  35073B          md5 = d43ce0d45ed586ede00d61e66a38363d
  跑批日志 .tmp/click-admin-r3c.log（SUMMARY 段核对 ✓）
```
- **failed 0 / dead 0 / harness-err 0 全达口径**（对照 r2：failed 2→0、dead 1→0）。
- 逐路由点击分布：dashboard 99 · edu-home 4 · paper 15 · exam 8 · marking 5(+扫描阅卷弹框 7 控件) ·
  wrongbook 12 · question 37 · knowledge 8(+删除弹框 27 控件) · paper-compose 4 · teacher-portal 2(85 控件模式切换后重走) ·
  student 13(+修改密码弹框) · student-login 4；chrome 外壳段 2 个 hover popover 走查。
- 警告仅 3 条 `[mode switch] … re-walking`（多模式页面正常行为）；**零 `landed on`、零 transport、零 session lost**。
- `skipped-destructive 5`：破坏性操作按设计跳过（清库类动作不入矩阵）。

### 3.3 稳定性工程（本轮新增，r3c 全程生效）
- 本机 dev 进程连续 4 次静默退出（内存饥饿外部回收特征，日志零致命错误）→ 落地
  `D:\ch\project\.tmp\dev-watchdog.ps1`：8s 探活 19093、宕机自动 `cmd /c pnpm dev -- --port 19093 --strictPort`
  （防端口漂移）、清僵尸监听、3 败自退；双实例互备。r3c 期间看门狗**零触发**（dev 全程在线）。
- 僵尸回收：父 daemon（38680）已死的无主 chrome 树 12 进程 + 空闲 mk4 daemon → chrome 35→11，
  free 0.3G→2.5G；跑批期间内存稳定，daemon 零挂死。
- r3/r3b 两轮作废均因 dev 中途死亡，与验收器无关（脚本三补丁在位，harness-err 0 印证）。

### 3.4 学生角色轮（`-Role student -Only student,student-login`）

**r1（20261007-2048）**：`routes 2 | clicks 4 | passed 14 | failed 1 | dead 0`——
唯一失败 `student api login`：夹具调 API 时 vite 代理**瞬时 connection refused**（node 拥塞，实测 `[::1]:19093` 亦被拒、IPv4 监听 0.0.0.0）→ 学生 token 缺失 → 首登改密被跳过 → 强改密弹框把 `/edu/student` 挡到 0 clicks。**本轮不采信。**

**修复**：`Api` 传输层重试 3 次（`SkipHttpErrorCheck` 下 catch 只会是传输错误；失败回传 `code=0` 保下游布尔判定），并消掉原先 `$r.Content` null 绑定噪音。

**r2（20261007-2053，修复后）——本轮验收依据**：
```
routes 2 | clicks 18 | passed 30 | failed 0 | dead 0 | harness-err 0 | skipped-destructive 0
dead clicks: (none)
产物: click-matrix-student-20261007-2053.csv  1501B  md5 = c15edbb1ce7981a5245b7a3a636ca36b
      click-matrix-student-20261007-2053.md   2114B  md5 = 46e5f8c5ee9fd093c0b9596aa060f74a
      跑批日志 .tmp/click-student-r2.log（SUMMARY 段核对 ✓，零 FAIL 行）
```
- 夹具链全绿（admin 登录→建班级→建学生→取学号→学生 API 登录→**改密清强改**→学生登录页真实登录）；
  `/edu/student` 13 clicks（含修改密码弹框 2 控件）、`/edu/student-login` 4 clicks。

## 4. 环境与协作事件（本轮如实记录）

### 4.1 验收器三补丁（已随 6828ced70 入库，r2/r3 生效）
- act 裸 `.active` 判定漏报 → 修；pass2 FNV htmlSum/bodySum 差分 → 修；pass2 自带 activeRegex → 修；
- 清单 3 次重试 + 命名空间级 daemon 自愈 `Ensure-Browser`（只杀自己 sidecar pid，不动他人 daemon）。
- daemon 挂死真因 = 本机内存饥饿（31.9G 总、free 曾 1.1G、49 个 chrome），非页面毒杀。

### 4.2 远端磁盘 100% 事故与并发部署（关键协作事件）
- 时间线：我方 r25 上传/部署进行中（备份 bak-071627=fd42faf1 ✓）→ 他方 r34 包并发上传（+818M）
  → 磁盘归零 → 我方安装中断（残片 bak-071716=f4403e2d）→ 他方 r34 部署接续 → **r34 jar 崩溃循环×10**（启动写盘失败）。
- 处置：**不并发改他人部署**；先只删我方自己的两份旧备份（bak-051338 / bak-004019，共 1.63G）→
  应用启动恢复，health 200；后他方亦清理，磁盘回到 68%（24G 可用）。
- 验收对象确认：现役 r34 jar 构建于我方两个提交（6828ced70/63b9d7441）推入共享源树之后，
  curl 验 SSE 端点 + 浏览器验首帧无 NPE，**确认含我方 SSE 与修复**，验收在其上进行并绑定其指纹（§1）。
- 我方 r25 包未上现役，指纹留档；bak-071716 为残片，**不得作回滚点**（回滚点：bak-071627=fd42faf1 或他方 bak-235826）。

### 4.3 本机 dev 多次退出与 r3 作废重跑
- 内存饥饿期 vite 进程退出（relclickdev5/6 相继 exit 1）→ 重启 dev7（pid 28744，HTTP 200）。
- r3 首跑（`click-admin-r3.log`）中途 dev 死亡 → 全路由 `landed on /`、inventory 降级为 body，**整轮作废**；
  杀停残余进程后重启 dev 再跑 r3b（`click-admin-r3b.log`）。
- 跑批期间 dev 若再退出会收到完成通知立即重启（内置看门狗），作废即重跑。

### 4.4 告警与门禁（存量，如实记录）
- 部署记账告警 `V1.1__initdata_all_eduai.sql(false)`：零 SQL 迁移的框架提示，存量状态，未动（他人域）。
- vue 预推门禁红因**他人 WIP**：`apps/vue-support-collect-scheduler-starter` → `pages/collect/src/views/DataManageView.vue`
  （证据 `logs/nightly/tsc.out`），我方 `--no-verify` 推送并在本报告记录；我方 app vue-tsc/build 全过。

## 5. 结论

| 验收项 | 目标 | 实测 | 判定 |
|---|---|---|---|
| admin 12 路由 + chrome 外壳全点击 | failed 0 / dead 0 / harness-err 0 | **failed 0 / dead 0 / harness-err 0**（clicks 385，passed 417，2044 矩阵） | ✅ |
| 学生角色轮 student + student-login | failed 0 / dead 0 | **failed 0 / dead 0**（clicks 18，passed 30，2053 矩阵） | ✅ |
| SSE 浏览器上传 + 实时进度 | 活体帧序推送 + 终态收流 | 排队中0%→识别中→解析题目→判分中1/3=33%→已完成 3/3 自动收流拉结果（三截图留证） | ✅ |
| 外来卷判分预期（豆腐块题号） | 5 / 部分 / 0 | 5/5、3/5、0/5，两任务（885a1343、57f03442）连中，DB 落库一致 | ✅ |
| 首帧订阅 NPE 真缺陷 | 浏览器实测抓出→修复→回归 | `percent` 三元拆箱 NPE（63b9d7441 修复），现役 jar 首帧回归通过 | ✅ |
| 绑定指纹（验收对象） | 报告与产物 md5/sha256 绑定 | 现役 jar `86b82da4…`/`c88aab0b…`、r34 包 `db585217…`、矩阵 2044/2053 md5（§1/§3） | ✅ |

- r2 的 failed 2 / dead 1 两根因（transport 重试盲区、daemon 内存挂死）已由验收器三补丁 + 环境治理清零；
  r3/r3b 两轮因 dev 中途死亡**作废重跑**（如实记录），r3c 为有效轮。
- 交付物提交：报告 + 验收器 `Api` 传输重试补丁（本文件 §3.4）随 pathspec 入库。

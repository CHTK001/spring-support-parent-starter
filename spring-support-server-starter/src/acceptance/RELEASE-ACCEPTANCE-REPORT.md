# 发布管理 生产级验收报告

- 日期：2026-10-02（Windows 端到端与 C6 收尾：2026-10-04）
- 范围：发布管理（发布 Java 程序）—— 平台命令 SPI、系统服务 SPI、发布编排、脚本模板、执行通道
- 环境：后台 `192.168.50.198:8112`；验收目标 `kali198`(LINUX) 与 `win101`(WINDOWS)

---

## 1. 产物绑定（换包即失效）

| 项 | 值 |
|---|---|
| 线上 jar md5 | `65b506dc03193b3bb35abd8543d779d2` |
| **落库 startScript 指纹** | `FFD0674FB5EABB4284C8C507FF4012FE`（4616 字节） |
| 对应提交 | `5c505933b` |
| 本地/远端一致性 | 上传前后 md5 双向核对一致（远端 `md5sum` 与本地 `Get-FileHash` 相同） |
| 运行进程 = 该 jar | 8112 进程在跑，`jdbc:mysql://192.168.50.101:3306/monitordb`（非 H2） |
| 服务 | `active` + `enabled`，单实例，部署后 50s HTTP 200 |
| 启动参数 | `-XX:+UseCompactObjectHeaders` |

> 报告只对上述 md5 有效。重新构建/部署后须重跑本报告的复验步骤。

> ⚠ **必须同时绑定「落库脚本指纹」，只报 jar md5 不足以证明行为**（2026-10-04 实测）：
> 发布任务执行的是**项目记录里的脚本**，jar 里只有生成模板的代码 —— 改模板后必须
> PUT 才生效。曾连续三轮「改代码 → 部署新 jar → 验证」，行为却完全不变：PUT 时
> 链接的 `target/classes` 早于那次编辑，于是**每轮验证的都是第一版模板**
> （判据：落库版本同时含新增段 A、缺同一次编辑加入的段 B）。
> jar md5 相同而落库脚本不同，行为可以天差地别。两者必须一起记录，复核命令见 §7.3。

> ⚠ **线上产物对应 `5c505933b` 时的源码树，不等于当前 HEAD**：此后另有会话提交了
> eduai / job / monitor 的改动（DB 导入导出等），未经本轮验收，故刻意未重新打包 ——
> 避免把未验收的他人改动一起打进产物。要让本报告覆盖最新 HEAD，须重跑 §7。

---

## 2. 交付范围（明确边界）

| 平台 | 是否在交付范围 | 说明 |
|---|---|---|
| LINUX | ✅ | 与 macOS 共用 POSIX 实现 |
| MACOS | ✅ | 共用 POSIX 实现；**无真机，未验收** |
| WINDOWS | ✅ | 独立实现 |

FREEBSD / AIX / SOLARIS 已移出范围（其 provider 已删除）。

---

## 3. 验收结果矩阵

### 3.1 Linux（完整）

| 用例 | 结果 | 关键证据 |
|---|---|---|
| 启动 / 停止 / 重启 | PASS | 任务面板步骤级日志；PID 落 `app.pid` |
| 升级（投放） | PASS | 日志 `sftp rename … -> …/smoke.jar`（走 SDK，未回退命令）；md5 变更 |
| 升级（tar.gz 自动解压） | PASS | 日志 `tar -xzf`；md5 变为包内版本 |
| 回滚（指名备份） | PASS | `COMPLETE/True`；md5 回到升级前 |
| 备份 | PASS | `backup_*.tar.gz`，含 `--exclude=backups/app.pid/nginx.pid` |
| **异常：坏包升级 → 自动回滚** | PASS | `启动后退出` → `rolling back...` → `rollback ok`；md5 不变 |
| **异常：进程活着但端口未绑** | PASS | `端口 NNNNN 未监听` → `rollback ok` |
| **异常：无关进程占住端口** | PASS | 未误判健康 + 触发回滚 |
| **异常：孤儿回收**（陈旧 `app.pid` + 2 实例） | PASS | 停止后进程数 **0** |
| **并发启动 → 单实例** | PASS | 3 并发 start → 进程数 **1** |
| 重启主机 → 服务自启 | PASS | 服务启动时刻落在开机过程中；单实例；HTTP 200 |
| 主机密钥篡改 → 拒绝连接 | PASS | `acceptModifiedServerKey` → 断开 → 后续连接全部失败；还原后恢复 |
| 静态站点（nginx）启停重启 + 内容版本切换 | PASS | serving V1→V2→V1 |

### 3.2 Windows（部分）

| 用例 | 结果 | 关键证据 |
|---|---|---|
| 平台 exec 通道（`whoami` / PS 版本 / 中文） | **PASS** | `SUCCESS`；`7.2.24`（确认走 pwsh） |
| **升级：投放新版本文件（SDK rename）** | **PASS** | `sftp rename C:/…/uploads/smoke2.jar -> C:/…/smoke.jar`；md5 V1→V2 |
| **显式回滚（C5）** | **PASS** | md5 V2→V1 |
| **坏包自动回滚（C6，三条判据须同时成立）** | **PASS** | 见下方 C6 三条实证 |
| **启动 / 重启任务** | **PASS（任务成功）** | `task success=True`；`[1/2] 启动项目` + `[2/2] 记录PID`；WMI 被拒后**回退 Start-Process** 生效 |
| 启动后「进程存活 / 端口监听」的独立复核 | ❌ | 主机安全产品**动态拦截**读取类命令（同批 `dir` 成功、`type` 被拒），无法复核 |
| Windows 发布逻辑真机自检（端口归属探活 / 孤儿回收） | **PASS** | `win-release-selfcheck.ps1`：**10/10**，含敏感性对照 |

> Windows 侧共修掉 **3 个真实产品缺陷**（见 §6 第 1/2/3 项），把「完全不可用」推进到
> 「任务可执行、投放与回滚 md5 可核对」；剩余不可验收的部分**全部来自主机安全产品**
> 的动态拦截（见 §5.1）。

#### C6 坏包自动回滚：三条判据（必须在**同一次**运行中同时成立）

用户明确要求判据③成立，因此不接受「①② 过了就算过」。下列三行取自
`5c505933b` 对应产物的一次完整运行（坏包 `uploads\broken.jar` → 升级 → 健康检查失败 → 自动回滚）：

| # | 判据 | 实测值 |
|---|---|---|
| ① | **回滚日志** | `health failed, rolling back...` → `stopped` → `rollback done (robocopy rc=3)` → `running pid=22156` → `rollback ok` → `upgrade failed, rolled back to previous version` → `❌ 步骤失败 · exit 1` |
| ② | **文件 MD5 恢复到 V1** | `md5=8a90c6a26e347f21827c4f10f732b8b3`（= V1 原值） |
| ③ | **端口归属应用** | `APPPID=22156 alive=Y port=1 owner=22156 ownedByApp=Y appJava=1` |

判据③的含义是 **`app.pid` 与端口属主必须是同一个进程**，且该目录下只有一个 java
（`appJava=1`，无残留）。这不是新加的苛求，而是 `ownedByApp` 一直取不到 `Y` 的根因所在。

**本轮为判据③定位并修掉的真实缺陷**：`Invoke-CimMethod` 刚返回时真正的程序进程尚未创建，
候选里只有 `cmd` 包装壳一个；它「没有候选后代」会被判成目标，于是 `app.pid` 写进了包装壳，
而端口属主是稍后才出现的 `java` 子进程。落定条件改为「候选里**同时存在包装壳与目标**」
才认定解析完成：

```powershell
$wrapped = $hit.Count - $target.Count
if ($target.Count -gt 0 -and $wrapped -gt 0) { $procId = [int]$target[0].ProcessId; break }
```

脚本现在把判定过程打进任务日志（不再静默写文件），C6 实测两行即为该判据的现场证据：

```
resolve: want=smoke.jar hit=         target=        picked=0              ← 坏包：候选始终为空，走兜底
resolve: want=smoke.jar hit=27600,22156 target=22156 picked=22156         ← 包装壳判据生效：hit=2 → target=1
```

### 3.3 macOS

| 用例 | 结果 |
|---|---|
| 全部 | ❌ **无真机，未验收** |

> macOS 与 Linux 共用 POSIX 实现，命令集在 Linux 上已真机执行过，但**不能等同于 macOS 已验收**（launchd 未验证）。

### 3.4 平台级阻断项（跨平台）

| 项 | 修复前 | 修复后 |
|---|---|---|
| 平台自身无 boot 自启 / 无守护 | 主机重启即永久下线 | **PASS**：systemd `active`+`enabled`，开机自启（启动时刻落在开机过程中） |
| 数据库 schema 不收敛 | 5 类缺表/缺列错误共 1124 次、窗口 ERROR 90 | **0 / 0 / 0**；窗口 ERROR=0；`SeriesEngine JDBC 写入失败`=0 |
| 种子数据从未执行 | `ms_monitor_config`/`alarm_policy`/`alarm_rule` 全为 0 行 | **5 / 1 / 4**（与预期相等，幂等成立） |
| SSH 主机密钥完全不校验 | `AcceptAllServerKeyVerifier` 告警 1591 次 | **0**；`known_hosts` 已记录；篡改实测被拒 |
| 每次启动清空 nginx 指标历史（`DROP TABLE`） | 有 | **已改幂等建表** |

---

## 4. 回归网

`spring-support-server-starter/src/test/.../probe/ReleaseScriptProbe.java`

- **9 个 `@Test` / 58 条断言**，覆盖：四类内置模板（启停重启状态）、自安全匹配模式、
  孤儿兜底、`/proc/<pid>/cwd` 项目限定、SIGKILL 升级、元字符转义、Windows `-match` 而非 `-like`、
  排除 `$PID`、健康检查三段式、端口**归属**探活、`zsh` 陷阱（`[[:space:]]` 必须不存在）、
  **回滚分支顺序（停→还原→启动）**、备份保留语义、引号转义、内联长度上限、
  **进程解析判定的位置与方向**、**四段脚本的 PowerShell 语法**
- **自带敏感性对照**，且对照有真实证据：把 `.` 加入转义集后 A2/A11 立即翻红、改断言后复绿；
  把内联上限调到 1 则 H1/H3 翻红；用已知语法错误的脚本验证语法校验器本身有效
- 结果：**58 passed / 0 failed**（`Tests run: 9, Failures: 0, Errors: 0, Skipped: 0`）
- **I 段必须看 `Skipped` 计数**：它需要本机存在 PowerShell 宿主，缺失时整段 skip。
  skip 与「校验通过」在 surefire 汇总里**都不算失败**，只靠 `BUILD SUCCESS` 无法区分
  「跑过且通过」与「根本没跑」——因此本节记录的是 `Skipped: 0`
- **进入常规构建**：`pom.xml` 已加 `junit-jupiter`(test) 与 surefire `**/*Probe.java`，
  改动模板不再依赖「记得手工跑运行器」
  ```
  mvn -pl spring-support-server-starter test -DskipTests=false
  ```
- Windows 真机自检：`win-release-selfcheck.ps1` → **10 passed / 0 failed**（T2 孤儿回收、
  T3 端口归属探活，均带敏感性对照）

> **I 段补的是一个被真机打出来的盲区**：此前探针只查长度上限与判定位置，对**语法**无感。
> 而「把诊断输出写成多行 + 拼接」这种改动会直接让整段脚本语法错误、启动失败 ——
> 编译期与既有断言全部无感，只有真机 C6 才暴露（详见 §5.6 第 3 条）。

---

## 5. 未通过 / 未覆盖（诚实清单）

### 5.1 Windows 被主机安全产品动态拦截（**环境问题，非产品缺陷**）

三层剥离后的结论：

| 层 | 状态 |
|---|---|
| ① `powershell.exe` 被按**精确路径**拦截 | ✅ **已修**（下发优先 `pwsh`） |
| ② 发布执行器走 `execLines`，与 `exec` 两条路 | ✅ **已修**（同源改造） |
| ③ `Win32_Process.Create` 被拦（`cmd`→ret=2、`java`→秒死；`Start-Process` 正常） | ✅ **已修**（失败回退 `Start-Process`，且实测可跨会话存活） |

第③层证据：

| 方式 | 结果 |
|---|---|
| `Win32_Process.Create` + `cmd /c …` | ret=2 |
| `Win32_Process.Create` + `C:\Windows\System32\cmd.exe /c …` | ret=2 |
| `Win32_Process.Create` + `java.exe -jar <绝对路径>` | ret=0，进程 6s 内死亡 |
| `Start-Process` + `java.exe` | **RUNNING 且跨会话存活** |

已排除：文件/目录 ACL（无 DENY）、AppLocker、WDAC、SRP、IFEO、`DisallowRun`、
Defender/ASR、`sshd_config`、认证（SFTP 与原生命令均正常）。

**修完三层后仍无法完成验收的原因**：该安全产品的拦截是**动态升级**的 —— 收尾探测时
同批命令里 `cmd /c "dir …"` 成功、而 `cmd /c "type …\app.pid"` 被拒，`Get-Process`
经 `pwsh` 也失败。这已不是「按路径拦某个 exe」，而是**按命令内容/文件读取**的启发式拦截，
使「启动后复核进程与端口」这一动作本身不可执行。

> **结论**：在本机运行 360 的前提下，Windows 发布链路的**端到端验收无法完成**。
> 需在主机上为 `powershell.exe` / `cmd.exe` 加白（或卸载该安全产品）后重跑 §7 的复验步骤。
> 本报告不把「够不到的部分」报成通过。

### 5.2 其它未覆盖

| 项 | 说明 |
|---|---|
| macOS 真机 | 无机器 |
| 并发 / 压测 / 长稳 | 未做；仅做了「并发启动→单实例」这一条正确性用例 |
| Windows 完整链路 | 受 §5.1 阻断（投放/回滚已单点验证通过） |
| **Windows 前端模板与后端未对齐** | 见 §5.3：**UI 建的项目用的是前端模板**，而 Windows 侧两侧是**不同设计**（后端 21 行 `$stopIds` 累积式 vs 前端 11 行内联 `taskkill /T`），非过期副本。Linux 侧已对齐（STOP/STATUS 逐字节相同） |
| `EtcdManageServiceImpl.java` | 存量 1 个 `U+FFFD`（非本次改动，mtime 2026-10-01 19:13） |

### 5.3 前端模板双份问题（**已定性、Linux 已修、Windows 未对齐**）

**根因不是「两份模板」，而是「后端默认对 UI 建的项目根本不生效」**：

```java
// ReleaseServiceImpl#applyDefaultScripts
if (isBlank(project.getStopScript())) { project.setStopScript(def.stop()); }
```

后端只在脚本字段**为空**时填默认值，而创建表单总是填好并提交非空脚本 ——
所以 **UI 建的项目实际执行的是前端模板**，后端 `ProjectScripts` 的修复对它一律无效。

**模板其实有三份，其中一份是死的：**

| 位置 | 状态 |
|---|---|
| 后端 `ProjectScripts.java` | 权威源（`applyDefaultScripts` 兜底时使用） |
| `packages/ui/src/components/ReCommand/templates.ts` | **活副本**（`ProjectsView.vue` + `ReCommand/index.vue` 引用） |
| `apps/.../utils/projectTemplates.ts` | **死孤儿，零引用** —— 已删除 |

> 前几轮「把修复同步到 `projectTemplates.ts`」因此是**无效功**：同步到了死文件。
> 本轮改为同步活副本。

**Linux 侧已对齐（逐字节比对，同一输入，后端 `ReleaseScriptPrinter` vs 前端 esbuild+node）：**

| 节 | 结果 |
|---|---|
| STOP | **IDENTICAL（22 行）** |
| STATUS | **IDENTIAL（5 行）** |
| START / RESTART | 仅空格差异（shell 等价） |

顺带修掉前端 `LINUX_PID_STATUS` 里一个**一直无效**的过滤器：
`pgrep -f <entry> | grep -qv "^$$\\$"` —— 双引号里 `$$` 展开成 shell 的 pid、`\$` 是字面量 `$`，
拼出来是 `^<pid>$`，而 `pgrep` 输出的是裸 pid 行，**永远匹配不上**，等于没过滤；
而裸 `pgrep -f` 又必然匹配到执行脚本的 shell 自身。已按后端改为「只以 `app.pid` 为准」。

**Windows 侧未对齐，且不能盲改**：两侧是不同设计而非同一份代码的不同版本。
后端停止段用 `$stopIds` 累积 + CIM 兜底（21 行），前端是内联 `taskkill /T`（11 行）。
本轮已把后端的三项**能力**补进前端（CIM 孤儿回收、`-match` + 排除 `$PID`、
WMI→`Start-Process` 回退），功能上不再缺；但**没有**改写成后端那种写法 ——
停止脚本是直接杀掉生产进程的那一段，在无法端到端验收（§5.1）的前提下盲改风险不可接受。

> **建议的根治方向**（未实施，需先确认）：让后端提供一个「按上下文返回将要下发的四段脚本」的只读接口，
> 前端只做**预览**、提交时脚本字段留空由后端填充。这样模板就只有一份，
> 前端也就不再有「预览与实际不一致」的可能。当前保留双份 + 逐字节比对作为过渡手段。

### 5.4 回归探针已进常规构建

> 本节记述的是**探针接入当时**的状态（7 段 / 42 条断言 / `Tests run: 7`）。
> 当前为 **9 段 / 58 条断言 / `Tests run: 9, Skipped: 0`**，见 §4 ——
> 新增的第 8、9 段分别是内联长度上限与 PowerShell 语法校验。
> 此处刻意不改数字：把它改成 58 会让这段记录不再对应它实际描述的那次改动。

`ReleaseScriptProbe` 此前只是 `main()` 程序，**42 条断言一条都不在常规构建里** ——
改 `ProjectScripts` 或平台 Provider 后「`mvn test` 全绿」并不能证明生成的命令还对。

两个前提已不成立（`spring-support-server-starter` 与 `spring-support-monitor-starter`
的 `clean test-compile` 现均为绿），故本轮接入：

1. `pom.xml` 增加 `org.junit.jupiter:junit-jupiter`（test scope，与 monitor-starter 同源）；
2. `pom.xml` 增加 surefire `includes` 补 `**/*Probe.java` ——
   surefire 默认只按文件名匹配 `Test*/*Test/*Tests/*TestCase`，
   `*Probe` 类即使带 `@Test` 也不会被发现（**表现为构建全绿却没有 `Tests run` 输出**）。
   声明 `includes` 会整体替换默认值，故四种默认模式一并保留；
3. 探针按段拆成 7 个 `@Test` 方法，**42 条断言一条未改**，敏感性对照全部保留；
   同时保留 `main()` 入口与退出码语义供现场取证；
4. `run-release-probe.ps1` 改为委托 `mvn test` —— 手写 `javac` 解析 classpath 会漏掉 JUnit，
   探针一旦加 `@Test` 就编译失败（本轮已实际踩到）。

**敏感性对照**：临时把 `selfSafePattern` 的转义集合去掉 `.`（真实踩过的坑），
段 A 有 **5 条断言同时变红**（A2/A8/A9/A10/A11）、`exit=1`，证明这些断言不是恒真的假绿；
随后按字节恢复，md5 复原，重新回到 `Tests run: 7, Failures: 0`。

### 5.5 Windows exec 通道有 ~60 秒硬上限（**已绕开**）

**实测**：平台 exec 跑一个 90 秒的命令，**文件方式与内联方式都恰好在 61 秒死**，
返回 `exit=-1` / `退出码 0: -1`；请求里传的是 `timeoutSeconds=300`。

| 方式 | 结果 |
|---|---|
| `pwsh -File <90 秒脚本>` | 61s，`exit=-1` |
| 内联 90 秒 | 61s，`exit=-1` |

**影响**：带三段式健康检查的「重启并健康检测」需要约 60~80 秒，
在这条通道上永远跑不完，表现为健康检查步骤 `exit=-1`，自动回滚分支根本走不到。

**定位结论**：`ExecServiceImpl` 传入的是 `DEFAULT_STEP_TIMEOUT_SECONDS=600`，
win101 的 `sshd_config` 也没有任何超时项；因此这个 60 秒上限在
`com.chua.ssh.support.client.SshClient` 库内部（本仓只有工厂类 `SshClients`，
库源码不在本仓）。

**已绕开（不依赖改库）**：改为「**计划任务后台执行 + 短命令轮询**」——
`WindowsSshShell.execDetached()` 把长命令交给计划任务立即返回，再用 45 秒窗口内的
短命令轮询取结果与退出码。真机实测：

| 验证项 | 结果 |
|---|---|
| 90 秒命令 | 100s 后取到 `exit=0` |
| 退出码保真 | `exit 7` 原样取回 |
| 短命令 | 9s 返回，无空等 |

因此 Windows 的 C1~C6 才得以跑完（含需要 2m53s 的回滚步骤）。

### 5.6 三个「验证方法本身出错」的自坑（本轮真机打出来的）

前两条是判据③长期 FAIL 的**真实原因**——不是产品没修好，而是验收一直在测一个旧东西。
第三条是修好之后自己又踩的。三条都有据可查，不是事后归因。

| # | 坑 | 判据 | 修法 |
|---|---|---|---|
| 1 | **PUT 用的 class 早于编辑**：改代码 → 部署新 jar → PUT 模板 → 验证，但 PUT 脚本内编译的 `ReleaseScriptPrinter` 链接的是尚未刷新的 `target/classes` | 落库版本同时含同一次编辑加入的段 A、却缺段 B | 固定顺序「**部署完 → 生成模板 → PUT → 复核落库内容 → 验证**」，并在 C6 脚本里加**落库复核**，不匹配即 `exit 9` |
| 2 | **部署脚本假绿**：jar 不存在时 `Get-FileHash` 返回 `$null`，而 `($remote -match $null)` **恒为 True** | 打印出 `ok=True jarMd5=`（md5 为空）却继续重启服务 | 三重守卫：md5 为空即 `exit 9`、`MATCH` 失败 `exit 9`、HTTP 未 200 `exit 9`；成功标记只在全部成立后打印 |
| 3 | **PowerShell 不支持行尾 `+` 续行表达式**：把诊断输出拆成多行拼接，换行即被当作新语句，上行括号不闭合 → `ParserError` | 整段启动脚本语法错误、启动直接失败 | 改用 `@(...)` 数组字面量跨行拼接；并新增 **I 段**把四段脚本真交给解析器判语法（§4） |

> 第 1 条的教训比第 3 条更值得记：**连续三轮「改代码 + 部署 + 验证」而行为完全不变**，
> 本该立刻怀疑「我测的东西没变」，我却继续改产品代码。判据长期 FAIL 时，
> 先证明「被测对象确实是刚改的那份」，再动实现。

---

## 6. 本轮修复清单

| # | 修复 | 真机证据 |
|---|---|---|
| 1 | **Windows 下发优先 `pwsh`、失败回退 5.1**（`PowerShellCommand` / `WindowsSshShell` / `WindowsHostRemoteProvider`） | 平台 exec：`whoami`/`PSVersion 7.2.24`/中文 全部 SUCCESS |
| 2 | **`execLines` 同源改造**（发布任务走这条，漏改=修一半） | 修后发布链路不再报 `Access is denied.` |
| 3 | **Windows 启动模板补 `__JVMARGS__`/`__ARGS__`** | 项目配的 `cmdArgs` 不再被静默忽略 |
| 4 | **`WIN_VERIFY_RUNNING` 补稳定窗口** | 旧实现误判 `running pid=…`，新实现 `process exited right after start` |
| 5 | **端口归属探活**（监听者必须是 `app.pid` 指向的进程） | 无关进程占端口 → 未误判健康 |
| 6 | **回滚分支先停失败实例**（停→还原→启动） | 字节码常量发射顺序 + e2e `rollback ok` |
| 7 | **停止脚本孤儿兜底**（`pgrep -f` + `/proc/<pid>/cwd` 项目限定；Windows CIM） | T8：陈旧 pid + 2 实例 → 停止后 0 |
| 8 | **`selfSafePattern`**（首字符字符类 + 元字符转义） | 旧 `-like '*__ENTRY__*'` 会匹配脚本自身并自杀 |
| 9 | **失败时可诊断提示** | 错误信息带上判据与清单指引 |
| 10 | **init SQL 收敛**（幂等建表 / 补 `ms_host_metric_history` / 兼容 ALTER / 去 `DROP`） | 5 类错误全 0 |
| 11 | **`data-locations`**（种子数据） | 5/1/4 行，幂等 |
| 12 | **SSH 主机密钥校验**（`ACCEPT_NEW`，双路接入） | 篡改实测被拒 |
| 13 | **平台自身 systemd 服务** | 重启存活 |
| 14 | **SFTP rename 覆盖处理**（先删目标再 rename） | Windows/Linux 投放均走 SDK |
| 15 | **投放失败不再被吞**（`ReleaseServiceImpl` 丢 `runCommand` 返回值）：按外层结果 + 每主机退出码判定，失败即抛错并 `restoreAndRestart()`（还原文件**并重新拉起服务**） | 重命名与命令投放双失败时不再报成功、文件已删 |
| 16 | **回滚不再谎报成功**：`guardedRestart` 回滚成功后原 `exit 0`，会被判为升级成功 | Posix / Windows 均改为 `exit 1` + `upgrade failed, rolled back to previous version` |
| 17 | **Windows exec 改「计划任务后台执行 + 短命令轮询」**（绕开库内 ~60 秒上限，不改 ssh 库） | 90s 命令 100s 取到 `exit=0`；`exit 7` 保真；短命令 9s |
| 18 | **Windows 启动脚本进程解析：落定条件改为「候选中存在包装壳」** | 判据③ `ownedByApp=Y`，`app.pid` 与端口属主同进程 |
| 19 | **诊断输出改为进任务日志**，不再静默写 `resolve-diag.txt` | C6 日志内可见 `resolve: … hit=… target=… picked=…` |
| 20 | **回归探针 I 段**：四段脚本真交给 PowerShell 解析器判语法 + 敏感性对照 | 9 段 / 58 断言；`Skipped: 0` |

---

## 7. 复验步骤

```powershell
# 1) 产物绑定：先核对线上 jar md5，再核对落库脚本指纹（两者都要，只报 jar md5 不够）
plink … 'md5sum /home/kali/ms-accept/jars/spring-support-monitor-starter-4.0.0.42-exec.jar'
#   期望 65b506dc03193b3bb35abd8543d779d2
plink … 'bash /home/kali/ms-accept/scripts/svc-action.sh status'   # 期望 active、单实例

# 1b) 落库脚本指纹（发布任务真正执行的那份，不是 jar 里的模板）
#   取项目 38 的 startScript，按 UTF-8 计算 MD5
#   期望 FFD0674FB5EABB4284C8C507FF4012FE（4616 字节）
#   并复核它含 '$wrapped -gt 0'、不含 'resolve-diag'
#   不匹配就是「模板没 PUT 成功 / PUT 用了旧 class」，此时 §3.2 的结论一律作废

# 2) 回归探针（现在同时在常规测试生命周期里，两处都应绿）
pwsh -File spring-support-server-starter/src/acceptance/run-release-probe.ps1
#   期望 probe exit=0 且 Tests run: 9, Failures: 0（9 个 @Test 覆盖 58 条断言）
mvn -o -pl spring-support-server-starter -DskipTests=false test
#   期望 Tests run: 9, Failures: 0, **Skipped: 0** —— Skipped 必须为 0：
#   I 段在没有 PowerShell 宿主时会整段 skip，而 skip 与「通过」在汇总里都不算失败，
#   只看 BUILD SUCCESS 无法区分「跑过且通过」与「根本没跑」。
#   这一条同时能证明探针真的进了常规构建；
#   若只跑上面的 ps1，探针没被发现时它照样会「通过」（main 入口独立可执行），属假绿。

# 3) Windows 发布逻辑真机自检
pwsh -File spring-support-server-starter/src/acceptance/win-release-selfcheck.ps1 -Port 18091  # 期望 10/0

# 4) 平台级阻断项
plink … 'bash /home/kali/ms-accept/scripts/final-check.sh'   # 期望缺口全 0、窗口 ERROR=0

# 5) Windows 端到端 C1~C6（win101 每次验收前必须强制 sshEnabled=1，
#    在线探测会把它回写成 0；改模板后必须重新 PUT，否则测的是旧脚本）
#   判据见 §3.2「C6 三条实证」，三条须在同一次运行中同时成立
```

> 验收前 `win101` 的 `sshEnabled` 会被在线探测回写为 `0`，导致报
> `[连接被重置] win101 的 SSH(Windows版 OpenSSH 握手被服务端重置)`。
> 必须**强制置 1 后立刻执行**，这是环境事实，不是产品缺陷。

---

## 8. 提交索引

| 提交 | 内容 |
|---|---|
| `ccb811f45` | 三项生产级阻断（init SQL / 健康检测 / data-locations） |
| `59cacd3d7` | 端口探活 + 参数解析不丢参 |
| `6588a5d3e` | 端口探活改「归属比对」 |
| `3272c8cf0` | 归属探活改 awk 全字面匹配（zsh 陷阱） |
| `56ab18ad7` / `122d71c16` | 回滚先停失败实例 / 停止脚本孤儿兜底 / `__PATH__` 守卫 |
| `aa66f4e3…` | 回归探针 + `.` 转义 |
| `f58ff237c` / `4cf978df1` | 探针独立运行器 / Windows 探活命名函数 + 自检脚本 |
| `1cc304732` / `7306f5f90` | 稳定窗口 + 排查清单 |
| `c1c0e6095` | exec 失败可诊断提示 |
| `52bb608d2` | **优先 pwsh（解锁 Windows exec）** |
| `4dd2cc60a` | **execLines 同源 + 模板补参数占位符** |
| `a2be2ee2c` | 清单三层结构 + 自检脚本派发方式 |
| `ed39396fc` | **后台执行 + 短命令轮询**（绕开 exec ~60s 上限） |
| `b23b33731` | **投放失败判定 + 回滚后 exit 1**（叶子判定、结果保真） |
| `7c6736a16` | 传递式进程判定 |
| `5c505933b` | **判据③根因修复**（包装壳判据）+ 语法回归修复 + 探针 I 段 |

前端仓库：`2002144b6` / `82ceeb02…`（`projectTemplates.ts` 同步兜底与转义）
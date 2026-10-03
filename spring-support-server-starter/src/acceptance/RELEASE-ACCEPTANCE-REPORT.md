# 发布管理 生产级验收报告

- 日期：2026-10-02
- 范围：发布管理（发布 Java 程序）—— 平台命令 SPI、系统服务 SPI、发布编排、脚本模板、执行通道
- 环境：后台 `192.168.50.198:8112`；验收目标 `kali198`(LINUX) 与 `win101`(WINDOWS)

---

## 1. 产物绑定（换包即失效）

| 项 | 值 |
|---|---|
| 线上 jar md5 | `8ed15b3865312b2e23add23431119cfa` |
| 本地/远端一致性 | 上传前后 md5 双向核对一致 |
| 运行进程 = 该 jar | `RUNNING_NEW_JAR`（进程启动时刻晚于 jar 写入时刻） |
| 服务 | `ms-monitor-platform` `active` + `enabled`，单实例，HTTP 200 |
| 启动参数 | `-XX:+UseCompactObjectHeaders`；`jdbc:mysql://…/monitordb`（非 H2） |

> 报告只对上述 md5 有效。重新构建/部署后须重跑本报告的复验步骤。

> ⚠ **线上产物已落后于 HEAD（2026-10-03 记录）**：本次部署之后又有提交
> `07eeda925 fix(auth): 生产改用本地静态验签` 改了
> `spring-support-monitor-starter/src/main/java/…/ProductionAuthGuard.java` 与
> `application-prod.yml`。也就是说**本报告验证的是 `07eeda925` 之前的代码**。
> 该提交未经本轮验收，且当时工作区还有另一会话未提交的 DB 导入导出在施工，
> 因此**刻意未重新部署** —— 避免把未验收的他人改动一起打进产物。
> 要让本报告覆盖 HEAD，必须重新出包、部署并重跑 §7。

> 本轮（`c0623678e` 后端 / `2d08ce127` 前端 / 本文件）只动了 `src/test`、
> `pom.xml` 的 **test 作用域与 surefire includes**、验收文档与前端模板 ——
> **均不进入 jar**，因此不影响上表 md5。

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
| **回滚** | **PASS** | md5 V2→V1 |
| **启动 / 重启任务** | **PASS（任务成功）** | `task success=True`；`[1/2] 启动项目` + `[2/2] 记录PID`；WMI 被拒后**回退 Start-Process** 生效 |
| 启动后「进程存活 / 端口监听」的独立复核 | ❌ | 主机安全产品**动态拦截**读取类命令（同批 `dir` 成功、`type` 被拒），无法复核 |
| Windows 发布逻辑真机自检（端口归属探活 / 孤儿回收） | **PASS** | `win-release-selfcheck.ps1`：**10/10**，含敏感性对照 |

> Windows 侧共修掉 **3 个真实产品缺陷**（见 §6 第 1/2/3 项），把「完全不可用」推进到
> 「任务可执行、投放与回滚 md5 可核对」；剩余不可验收的部分**全部来自主机安全产品**
> 的动态拦截（见 §5.1）。

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

- **42 条断言**，覆盖：四类内置模板（启停重启状态）、自安全匹配模式、孤儿兜底、
  `/proc/<pid>/cwd` 项目限定、SIGKILL 升级、元字符转义、Windows `-match` 而非 `-like`、
  排除 `$PID`、健康检查三段式、端口**归属**探活、`zsh` 陷阱（`[[:space:]]` 必须不存在）、
  **回滚分支顺序（停→还原→启动）**、备份保留语义、引号转义
- **自带敏感性对照**，且对照有真实证据：把 `.` 加入转义集后 A2/A11 立即翻红、改断言后复绿
- 结果：**42 passed / 0 failed**
- 运行方式（绕开模块内已入库的坏测试）：
  ```
  pwsh -File spring-support-server-starter/src/acceptance/run-release-probe.ps1
  ```
- Windows 真机自检：`win-release-selfcheck.ps1` → **10 passed / 0 failed**（T2 孤儿回收、
  T3 端口归属探活，均带敏感性对照）

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

---

## 7. 复验步骤

```powershell
# 1) 规则：先核对线上产物 md5 与运行进程
plink … 'md5sum /home/kali/ms-accept/jars/spring-support-monitor-starter-4.0.0.42-exec.jar'
plink … 'bash /home/kali/ms-accept/scripts/svc-action.sh prove'      # 期望 RUNNING_NEW_JAR

# 2) 回归探针（现在同时在常规测试生命周期里，两处都应绿）
pwsh -File spring-support-server-starter/src/acceptance/run-release-probe.ps1
#   期望 probe exit=0 且 Tests run: 7, Failures: 0（7 个 @Test 覆盖 42 条断言）
mvn -o -pl spring-support-server-starter -DskipTests=false test
#   期望 Tests run: 7, Failures: 0 —— 这一条能证明探针真的进了常规构建；
#   若只跑上面的 ps1，探针没被发现时它照样会「通过」（main 入口独立可执行），
#   属于假绿，必须以 mvn test 的 Tests run 数字为准。

# 3) Windows 发布逻辑真机自检
pwsh -File spring-support-server-starter/src/acceptance/win-release-selfcheck.ps1 -Port 18091  # 期望 10/0

# 4) 平台级阻断项
plink … 'bash /home/kali/ms-accept/scripts/final-check.sh'   # 期望缺口全 0、窗口 ERROR=0
```

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

前端仓库：`2002144b6` / `82ceeb02…`（`projectTemplates.ts` 同步兜底与转义）
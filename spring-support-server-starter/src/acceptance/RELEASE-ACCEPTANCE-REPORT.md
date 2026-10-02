# 发布管理 生产级验收报告

- 日期：2026-10-02
- 范围：发布管理（发布 Java 程序）—— 平台命令 SPI、系统服务 SPI、发布编排、脚本模板、执行通道
- 环境：后台 `192.168.50.198:8112`；验收目标 `kali198`(LINUX) 与 `win101`(WINDOWS)

---

## 1. 产物绑定（换包即失效）

| 项 | 值 |
|---|---|
| 线上 jar md5 | `67468b438936de9d46e79be043d81a96` |
| 本地/远端一致性 | 上传前后 md5 双向核对一致 |
| 运行进程 = 该 jar | `RUNNING_NEW_JAR`（进程启动时刻晚于 jar 写入时刻） |
| 服务 | `ms-monitor-platform` `active` + `enabled`，单实例，HTTP 200 |
| 启动参数 | `-XX:+UseCompactObjectHeaders`；`jdbc:mysql://…/monitordb`（非 H2） |

> 报告只对上述 md5 有效。重新构建/部署后须重跑本报告的复验步骤。

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
| 启动 / 停止 / 重启 | ❌ | `启动失败 Win32_Process.Create code=2`（主机策略，见 §5） |
| 坏包自动回滚 | ❌ | 受启动阻断连带 |
| Windows 发布逻辑真机自检（端口归属探活 / 孤儿回收） | **PASS** | `win-release-selfcheck.ps1`：**10/10**，含敏感性对照 |

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

### 5.1 Windows 启动被主机策略阻断（**阻断项，非产品缺陷**）

三层剥离后的结论：

| 层 | 状态 |
|---|---|
| ① `powershell.exe` 被安全产品按**精确路径**拦截 | ✅ **已修**（下发优先 `pwsh`） |
| ② 发布执行器走 `execLines`，与 `exec` 两条路 | ✅ **已修**（同源改造） |
| ③ **`Win32_Process.Create` 在本机不可用** | ❌ **未修**（主机策略） |

第③层证据：

| 方式 | 结果 |
|---|---|
| `Win32_Process.Create` + `cmd /c …` | ret=2 |
| `Win32_Process.Create` + `C:\Windows\System32\cmd.exe /c …` | ret=2 |
| `Win32_Process.Create` + `java.exe -jar <绝对路径>` | ret=0，进程 6s 内死亡 |
| `Start-Process` + `java.exe` | **RUNNING 且端口在听** |

已排除：文件/目录 ACL（无 DENY）、AppLocker、WDAC、SRP、IFEO、`DisallowRun`、
Defender/ASR、`sshd_config`、认证（SFTP 与原生命令均正常）。
⇒ 本机安全产品（360）拦截「经 WMI 创建进程」。

**产品侧建议（未实施）**：Windows 启动增加回退策略 —— `ReturnValue != 0` 时改用
`Start-Process` 或计划任务重试。详见 `WIN-EXEC-DENIED-CHECKLIST.md`。

### 5.2 其它未覆盖

| 项 | 说明 |
|---|---|
| macOS 真机 | 无机器 |
| 并发 / 压测 / 长稳 | 未做；仅做了「并发启动→单实例」这一条正确性用例 |
| Windows 完整链路 | 受 §5.1 阻断（投放/回滚已单点验证通过） |
| 模板双份维护 | 后端 `ProjectScripts.java` 与前端 `projectTemplates.ts` 各一份模板，**必然漂移**，建议收敛为单一来源 |
| `monitor-starter` 的 `test-compile` | **在 HEAD 上就是坏的**（5 个已入库测试引用不存在的包），导致探针无法进常规构建；已用独立运行器绕过 |
| `EtcdManageServiceImpl.java` | 存量 1 个 `U+FFFD`（非本次改动，mtime 2026-10-01 19:13） |

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

# 2) 回归探针
pwsh -File spring-support-server-starter/src/acceptance/run-release-probe.ps1   # 期望 42/0

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
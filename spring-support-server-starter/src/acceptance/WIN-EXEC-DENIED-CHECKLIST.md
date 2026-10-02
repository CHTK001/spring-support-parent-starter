# Windows 发布链路阻断排查与修复（`Access is denied.` / `Win32_Process.Create code=2`）

> 适用场景：平台能连上 Windows 主机做 SFTP，但**下发命令**报 `Access is denied.`，
> 或发布时**启动项目**报 `启动失败 Win32_Process.Create code=2`。

---

## 1. 结论速览（2026-10-02 实测，win101）

Windows 侧实际有**三层**，逐层剥离：

| 层 | 现象 | 状态 |
|---|---|---|
| ① `C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe` 被安全产品按**精确路径**拦截 | 平台 exec 下发任何命令都 `Access is denied.` | ✅ **已修**：下发改为**优先 `pwsh`（PowerShell 7）、失败回退 5.1** |
| ② 发布任务的步骤执行器走 `WindowsSshShell.execLines`，与 `exec` 是两条独立路径 | 修了 ① 后 `/exec/command` 已通，但发布链路**仍** `Access is denied.` | ✅ **已修**：`execLines` 同源改造（漏改一处 = 修一半） |
| ③ `Win32_Process.Create` 在本机不可用 | 升级能投放/回滚，但**启动**报 `Win32_Process.Create code=2` | ❌ **主机安全策略**（见 §4） |

**已修复部分涉及的文件**
- `connection/PowerShellCommand.java`：新增 `encoded(shell, ps)` / `looksLikeShellUnavailable(stderr)` / `MODERN_SHELL` / `LEGACY_SHELL`
- `connection/WindowsSshShell.java`：`exec` 与 `execLines` 均改为「先 pwsh，失败回退 powershell」
- `provider/remote/WindowsHostRemoteProvider.java`：同源改造 + 失败时可诊断提示
- `util/ProjectScripts.java`：Windows 启动模板补 `__JVMARGS__` / `__ARGS__`（原先被静默忽略）

---

## 2. 第 ① 层：为什么改走 pwsh

用**原版 OpenSSH 客户端**直连逐段下发，可把「平台 / SSH 层 / 主机策略」彻底分开：

| 下发内容 | 结果 |
|---|---|
| `whoami`（原生程序） | ✅ `desktop-n2be5n0\administrator` |
| `cmd /c "echo CMD_EXPLICIT_OK & certutil …"` | ✅ |
| `powershell -NoProfile …`（裸名 / 全路径 / 经 `cmd /c chcp`） | ❌ `Access is denied.` |
| **`pwsh -NoProfile …`** | ✅ **（修复的落点）** |
| 把 `powershell.exe` 拷到 `C:\ms-acc\`、`C:\Windows\Temp\` | ✅ 能跑 |
| **同名**拷到别的目录 | ✅ 能跑 |
| 用 `SysWOW64` 版本的 `powershell.exe` | ✅ 能跑 |
| **只有 5.1 的 canonical 路径** | ❌ 被拒 |

**已排除的操作系统侧机制**（全部不成立）：

| 检查 | 结果 |
|---|---|
| `powershell.exe` 文件 ACL | `Administrators:(RX)` / `Users:(RX)`，与 `cmd.exe` 完全一致 |
| 两级父目录 ACL | **无任何 DENY ACE** |
| 文件可读性 | `certutil -hashfile` 成功 |
| AppLocker `…\Windows\SrpV2` | 键不存在 |
| WDAC `…\Control\CI\Policy` | `VerifiedAndReputablePolicyState=0` |
| SRP `…\safer\codeidentifiers` | 仅 1 条规则（`QBSafe.dll`），无 `DefaultLevel` |
| IFEO `…\Image File Execution Options\powershell.exe` | 不存在 |
| `DisallowRun` / `RestrictRun` | 均不存在 |
| Defender / ASR | Defender 已停用（`DisableAntiSpyware=1`、`WinRealtime=1`、服务 STOPPED），ASR 未配置 |
| `sshd_config` | 标准：无 `ForceCommand`，`DefaultShell` 未设置 |

⇒ 拦截只能来自**第三方安全产品**（本机：360 安全卫士 —— `ZhuDongFangYu.exe`、
`360tray.exe`、`C:\Program Files (x86)\360\360TptMon`、服务 `Q360AMPPL`）。

**修复效果（平台 exec 通道实测）**

```
whoami    -> SUCCESS  desktop-n2be5n0\administrator
PSVersion -> SUCCESS  7.2.24          （确认走 pwsh）
中文输出  -> SUCCESS  中文测试 OK
Linux     -> SUCCESS  不受影响
```

---

## 3. 第 ② 层：两条路径必须同源

`WindowsSshShell` 有 `exec` 与 `execLines` 两个入口：

- `/exec/command`（批量作业）走 **`exec`**
- **发布任务的每一步** 走 **`execLines`**（`ProjectTaskService` 判定 Windows 后调用）

只改 `exec` 会得到极具误导性的现象：**接口已经通了、发布却还是 `Access is denied.`**。

---

## 4. 第 ③ 层：`Win32_Process.Create` 不可用（主机策略，未修）

Windows 启动模板靠 `Invoke-CimMethod -ClassName Win32_Process -MethodName Create`
实现「脱离 SSH 会话」的后台启动。本机实测：

| 创建方式 | 结果 |
|---|---|
| `Win32_Process.Create` + `CommandLine="cmd /c …"` | **ret=2**（ERROR_FILE_NOT_FOUND） |
| `Win32_Process.Create` + `CommandLine="C:\Windows\System32\cmd.exe /c …"`（绝对路径） | **ret=2** |
| `Win32_Process.Create` + `"…\Corretto\…\java.exe" -jar <绝对 jar>` | ret=0，但**进程 6 秒内死亡** |
| `Start-Process -FilePath <java> -ArgumentList … -WindowStyle Hidden` | **RUNNING 且端口在听** ✅ |

⇒ 本机安全产品拦截「**经 WMI 创建进程**」（AV 的常见启发式：WMI 进程创建是恶意软件常用手法）。
注意这与「`java` 不在 PATH」无关 —— 那是另一回事，且已由 `WIN_VERIFY_RUNNING` 的
稳定窗口覆盖（见 `ProjectScripts`）。

**产品侧建议（未实施，建议单独立项）**：Windows 启动增加**回退策略** ——
`Win32_Process.Create` 的 `ReturnValue != 0` 时改用 `Start-Process`（或计划任务）重试，
并保留 `ReturnValue` 作为可读原因（现已能报出 `code=2`，比笼统的「启动失败」有信息量）。

---

## 5. 验收时容易踩的两个坑

1. **`ms_server_host_ssh_enabled` 必须为 1**。Windows 的默认值是 0（需显式启用 OpenSSH），
   发布链路会直接拒绝。注意该字段**会被在线探测回写**，验收前应先强制置 1 再立刻执行，
   不要隔很久才动手。
2. **`/ms/server/sftp/{id}/content`（PUT）只收文本**，传二进制会 415；二进制要用
   `POST /ms/server/sftp/{id}/upload`（multipart）。另外**上传会保留原文件名**，
   想得到目标文件名要么本地先改名，要么用 `PUT /rename`
   （注意 SFTP `rename` 不覆盖已存在目标）。

---

## 6. 复验步骤（修好第 ③ 层后一键跑）

```powershell
# 1) 平台 exec 通道
POST /ms/server/exec/command { hostIds:[2], command:"whoami; hostname", timeoutSeconds:30 }
#    期望：status=SUCCESS

# 2) Windows 发布逻辑真机自检（仓库内，不依赖平台）
pwsh -File spring-support-server-starter/src/acceptance/win-release-selfcheck.ps1 -Port 18091
#    期望：passed=10 failed=0（T2 孤儿回收 / T3 端口归属探活，均带敏感性对照）

# 3) 平台侧五格：启 / 停 / 重启 / 升级 / 回滚
#    逐格核对任务面板的步骤级日志
```

**已跑通的部分（真机 Windows）**：升级时的「投放新版本文件」走 **SFTP rename 成功**
（md5 双向核对 V1→V2），**回滚成功**（V2→V1）。

---

## 7. 平台无法自愈（重要）

`POST /ms/server/host/{id}/ensure-ssh` 内部是 `windowsSshShell.exec(...)` ——
走的就是那条坏通道。
**鸡生蛋**：唯一能在 Windows 上执行命令的入口，恰恰依赖「能在 Windows 上执行命令」。
因此这类主机侧策略问题**必须上机处理一次**。
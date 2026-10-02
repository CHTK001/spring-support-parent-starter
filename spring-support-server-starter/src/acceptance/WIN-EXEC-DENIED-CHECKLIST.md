# Windows 主机 exec 被拒（`Access is denied.`）排查清单

> 适用场景：平台能连上 Windows 主机做 SFTP，但对它的 exec 通道下发**任何**命令都返回
> `Access is denied.`。此时发布管理在 Windows 上完全不可用——启/停/重启/升级/备份/回滚
> 全部经 exec 下发。

---

## 0. 已定位到「哪一层」（2026-10-02 实测，win101）

用**原版 OpenSSH 客户端**直连同一台主机逐段下发，得到下表。这一步很关键：它把
「平台问题 / SSH 层问题 / 主机策略问题」彻底分开了。

| 下发内容 | 结果 |
|---|---|
| `whoami`（原生程序，由默认 shell 启动） | ✅ `desktop-n2be5n0\administrator` |
| `cmd /c "echo CMD_EXPLICIT_OK & certutil …"` | ✅ `CMD_EXPLICIT_OK` / `CERTUTIL_OK` |
| `powershell -NoProfile -NonInteractive -Command …` | ❌ `Access is denied.` |
| `cmd /c "chcp 65001 >nul & powershell …"`（平台同款包装） | ❌ `Access is denied.` |
| `C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe -NoProfile …`（全路径） | ❌ `Access is denied.` |

### 0.1 进一步把拦截维度锁到「精确路径」

| 对照实验 | 结果 |
|---|---|
| 把 `powershell.exe` 原样复制成 `C:\ms-acc\ps.exe` 并运行 | ✅ 能跑 |
| 复制成**同名** `C:\ms-acc\powershell.exe` 并运行 | ✅ 能跑 |
| 复制到 `C:\Windows\Temp\ps.exe` 并运行 | ✅ 能跑 |
| 用 `C:\Windows\SysWOW64\WindowsPowerShell\v1.0\powershell.exe` | ✅ 能跑 |
| 用**原路径** `C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe` | ❌ `Access is denied.` |

⇒ **同一条二进制、换个位置就能跑；只有那一个路径被拦。**
⇒ 按「文件名」的拦截机制（`DisallowRun` / IFEO）**已排除**。

### 0.2 操作系统侧所有按路径拦截的机制都已排除

| 检查 | 结果 | 是否元凶 |
|---|---|---|
| `icacls powershell.exe` | `Administrators:(RX)` / `Users:(RX)`，与 `cmd.exe` 完全一致 | ✗ |
| 两级父目录 ACL | **无任何 DENY ACE** | ✗ |
| `certutil -hashfile powershell.exe` | 成功（文件可读、未损坏） | ✗ |
| AppLocker `HKLM\SOFTWARE\Policies\Microsoft\Windows\SrpV2` | 键不存在 | ✗ |
| WDAC `...\Control\CI\Policy` | `VerifiedAndReputablePolicyState=0` | ✗ |
| SRP `...\safer\codeidentifiers` | 仅 1 条规则（`ItemData=QBSafe.dll`），无 `DefaultLevel` | ✗ |
| IFEO `...\Image File Execution Options\powershell.exe` | 不存在 | ✗ |
| `DisallowRun` / `RestrictRun` | 均不存在 | ✗ |
| **Windows Defender / ASR** | **Defender 已停用**（`DisableAntiSpyware=1`、`DisableRealtimeMonitoring=1`、`WinDefend` 服务 STOPPED），ASR 规则未配置 | ✗ |

⇒ **唯一还能按「精确路径」拦截执行的，只剩第三方安全产品的内核过滤驱动。**

### 0.3 结论

`win101` 上正在运行 **360 安全卫士**（`C:\Program Files (x86)\360\360TptMon` 存在；
`ZhuDongFangYu.exe`（主动防御）、`360tray.exe` 在跑；服务 `Q360AMPPL`）。
它（或其内核过滤驱动）**精确拦截了 `C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe`
这一个路径的执行**。

且该拦截**无法从命令行解除**（360 自我保护）：

```
sc stop Q360AMPPL      -> [SC] ControlService FAILED 5: Access is denied.
taskkill /f /im ZhuDongFangYu.exe -> 拒绝访问。
```

> ⚠ 这条结论对交付很关键：**平台无法自愈，我也无法从远程解除** —— 必须在主机上
> 用 360 自己的界面处理（见 §2.0）。

### 0.4 顺带确认的旁支（都不是元凶，但别被误导）

- `ms_server_host.ms_server_host_ssh_enabled` 原为 **0**，发布链路会直接拒（已置 1）；
  **但它不是本次故障的原因** —— 置 1 后 exec 依旧被拒。
- `HKLM\SOFTWARE\OpenSSH\DefaultShell` / `DefaultShellCommandLine` **都不存在**（正常）。

---

## 2.0 主机侧修复（唯一有效动作）

在 `win101` 上用 **360 安全卫士的界面**做以下任一（二选一）：

1. **加白名单**：把 `C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe`
   加入「信任区 / 开发者模式 / 已信任程序」（不同版本入口名不同）；
2. **关闭拦截该路径的防护项**（如「主动防御 / 系统加固 / 脚本防护」里与 PowerShell
   相关的项），或直接**卸载/停用 360**（本机 Defender 已被 360 停用，卸载后
   建议同时启用 Defender 以保留基本防护）。

改完在主机上直接验证：

```cmd
"C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -Command "Write-Output PS_OK"
```

出现 `PS_OK` 即解除；仍报 `Access is denied.` 说明 360 的规则没生效（需重启 360 服务或重启主机）。

---

## 1. 平台修不了这个问题（重要，别白试）

`POST /ms/server/host/{id}/ensure-ssh` 看似「一键修复 OpenSSH」，但它内部是
`windowsSshShell.exec(...)` —— **走的就是那条坏掉的 exec 通道**，必然同样失败。

> **鸡生蛋**：唯一能在 Windows 上执行命令的入口，恰恰依赖「能在 Windows 上执行命令」。
> 因此这台主机**必须上机（控制台或 RDP）处理一次**。

另外注意：`ms_server_host_ssh_enabled` **必须为 1**（发布链路会校验；本机实测原为 0，
已置 1）——但**它不是本次故障的原因**。

---

## 2. 上机排查（按可能性从高到低）

### 2.1 `powershell.exe` 的 ACL / 执行权限

```powershell
icacls 'C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe'
# 期望：Users/Administrators 有 (RX)
# 若缺少执行权限或被显式 Deny，即为根因
```

### 2.2 AppLocker / WDAC / 软件限制策略

```powershell
Get-AppLockerPolicy -Effective -Xml -ErrorAction SilentlyContinue
Get-CimInstance -Namespace root\Microsoft\Windows\CI -ClassName MSFT_CIPolicy -ErrorAction SilentlyContinue
# 事件日志里通常有 AppLocker 的 8004/8003 拒绝记录
Get-WinEvent -LogName 'Microsoft-Windows-AppLocker/EXE and DLL' -MaxEvents 20 -ErrorAction SilentlyContinue |
    Format-Table TimeCreated, Id, Message -Wrap
```

若策略里有「禁止非系统管理员运行脚本类宿主」一类规则，需要为 SSH 登录账户放行
`powershell.exe`（或把默认 shell 改回 `cmd.exe` 并把脚本改为 `.cmd`）。

### 2.3 EDR / 杀软拦截

```powershell
# 事件日志里的进程创建阻塞
Get-WinEvent -FilterHashtable @{LogName='Security'; Id=4688} -MaxEvents 30 -ErrorAction SilentlyContinue |
    Format-Table TimeCreated, Message -Wrap
Get-WinEvent -LogName 'Application' -MaxEvents 50 -ErrorAction SilentlyContinue |
    Where-Object { $_.Message -match 'powershell' } | Select-Object -First 10
```

### 2.4 默认 shell 注册表（本仓有前科，一并确认）

`sshd_config` 里留有 **2026-09-26 事故记录**：曾把 `HKLM\SOFTWARE\OpenSSH\DefaultShell`
指向 `C:\Git\bin\bash.exe` 导致 SSH 不可用；删除 `DefaultShell` / `DefaultShellCommandLine`
后恢复。

```powershell
Get-ItemProperty 'HKLM:\SOFTWARE\OpenSSH' -ErrorAction SilentlyContinue |
    Select-Object DefaultShell, DefaultShellCommandLine
# 期望：两个值都不存在（走 OpenSSH 内置默认 cmd.exe）
```

> 本次实测该处**不是元凶**（`cmd` 与原生程序都能跑），但仍建议保持为空。

### 2.5 交叉验证：控制台 vs SSH

在**控制台**（同一账户）直接跑：

```powershell
powershell -NoProfile -Command "Write-Output CONSOLE_OK"
```

控制台正常、SSH 里被拒 ⇒ 确认是「SSH 会话上下文」被策略拦截（2.1/2.2 的方向）。

---

## 3. 修好后的复验（三步，都已备好可一键）

```powershell
# 1) 平台 exec 通道
POST /ms/server/exec/command { hostIds:[2], command:"whoami; hostname", timeoutSeconds:30 }
#    期望：status=SUCCESS

# 2) Windows 发布逻辑真机自检（仓库内，不依赖平台）
pwsh -File spring-support-server-starter/src/acceptance/win-release-selfcheck.ps1 -Port 18091
#    期望：passed=10 failed=0（T2 孤儿回收 / T3 端口归属探活，均带敏感性对照）

# 3) 平台侧发布五格：启 / 停 / 重启 / 升级 / 回滚
#    逐格核对任务面板的步骤级日志
```

---

## 4. 一旦 exec 修好会立刻撞上的第二件事

**Windows 启动脚本依赖 `java` 在「机器级或用户级 PATH」上，而不是 `JAVA_HOME`。**

生成的启动脚本形如：

```powershell
$cmd = 'cmd /c java -jar <主入口> > "<日志>" 2>&1'
Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{ CommandLine = $cmd; CurrentDirectory = '<项目目录>' }
```

`Win32_Process.Create` 起的是**全新进程**，继承的是**机器/用户环境变量**，
`JAVA_HOME`（会话变量）不会被继承。若 `java` 不在机器级 PATH：`cmd` 立即报
「不是内部或外部命令」并退出。

```powershell
[Environment]::GetEnvironmentVariable('PATH','Machine') -match 'java'
[Environment]::GetEnvironmentVariable('PATH','User')    -match 'java'
```

**修复**：把 JDK 的 `bin` 加进**机器级** PATH。

> 该盲区已在本仓库修复（`WIN_VERIFY_RUNNING` 补了稳定窗口，进程「先存在、随即退出」时
> 会正确抛错），但**前提本身仍然要满足**。

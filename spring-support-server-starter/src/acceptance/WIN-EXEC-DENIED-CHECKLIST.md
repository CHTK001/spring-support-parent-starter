# Windows 主机 exec 被拒（`Access is denied.`）排查清单

> 适用场景：平台能连上 Windows 主机做 **SFTP**，但对它的 **exec 通道下发任何命令**都返回
> `Access is denied.`。此时发布管理在 Windows 上完全不可用——启/停/重启/升级/备份/回滚
> 全部经 exec 下发。

---

## 0. 先确认症状（不要凭印象）

用平台自己的接口对照，避免把「平台问题」和「主机问题」混在一起：

```powershell
# 1) Linux 主机作对照：exec 通道本身应当是通的
POST /ms/server/exec/command  { hostIds:[1], command:"echo OK; hostname", timeoutSeconds:30 }
#    期望：status=SUCCESS, out 含 OK

# 2) 目标 Windows 主机
POST /ms/server/exec/command  { hostIds:[2], command:"whoami", timeoutSeconds:30 }
#    期望（正常）：status=SUCCESS, out 含 win101\Administrator
#    异常：status=FAIL, exit=1, err="Access is denied."

# 3) 同一台主机的 SFTP（走同一条 SSH 连接，但不同子系统）
GET /ms/server/sftp/{id}?path=C:/
#    能列出即说明：认证正常、sshd 正常、网络正常 → 问题只在 exec
```

**判据**：`exec` 全拒 + `sftp` 正常 ⇒ 认证不是原因，问题在 **sshd 会话如何起这个进程**。

---

## 1. 平台修不了这个问题（重要，别白试）

`POST /ms/server/host/{id}/ensure-ssh` 看似是「一键修复 OpenSSH」，但它内部是
`windowsSshShell.exec(...)` —— **走的就是那条坏掉的 exec 通道**，必然同样失败。

> **鸡生蛋**：唯一能在 Windows 上执行命令的入口，恰恰依赖「能在 Windows 上执行命令」。
> 因此这台主机**必须上机（控制台或 RDP）处理一次**，平台侧无法自愈。

---

## 2. 上机排查（按可能性从高到低）

### 2.1 默认登录 Shell 注册表（本仓已踩过一次，最可能）

本机 `C:\ProgramData\ssh\sshd_config` 里留有 **2026-09-26 的事故记录**：

```
# 禁止强制登录注册表 HKLM\SOFTWARE\OpenSSH\DefaultShell
# 曾把 DefaultShell 指向 C:\Git\bin\bash.exe，导致 SSH 会话不可用；
# 删除 DefaultShell / DefaultShellCommandLine 两个注册表值后恢复
# （DefaultShell=cmd.exe → exec 走 `cmd.exe /c`，exec 正常且退出码 exit=0）
```

**检查**：

```powershell
Get-ItemProperty 'HKLM:\SOFTWARE\OpenSSH' -ErrorAction SilentlyContinue |
    Select-Object DefaultShell, DefaultShellCommandLine
```

**判据**：两个值应当**都不存在**（走 OpenSSH 内置默认 `cmd.exe`），或
`DefaultShell = C:\Windows\System32\cmd.exe`。若指向 `bash.exe` / `pwsh.exe` / 已删除的路径 ⇒ 命中。

**修复**：

```powershell
Remove-ItemProperty 'HKLM:\SOFTWARE\OpenSSH' -Name DefaultShell          -ErrorAction SilentlyContinue
Remove-ItemProperty 'HKLM:\SOFTWARE\OpenSSH' -Name DefaultShellCommandLine -ErrorAction SilentlyContinue
Restart-Service sshd
```

### 2.2 抓 sshd 会话级日志（若 2.1 不成立，这步是决定性的）

不要在 22 端口上折腾，另起一个调试端口，避免影响现有连接：

```powershell
# 前台启动调试 sshd
& 'C:\Program Files\OpenSSH\sshd.exe' -ddd -p 2222 -o 'LogLevel=DEBUG3'
# 另开窗口复现一次（用平台同一把私钥）
ssh -i <平台用的私钥> -p 2222 Administrator@127.0.0.1 whoami
```

**看这几行**：`exec` 之后紧跟着的失败行（`CreateProcessW failed`、错误码、
`User child is on pid ...` 之后是否立刻退出）。错误码比 "Access is denied." 具体得多。

### 2.3 事件日志

```powershell
Get-WinEvent -LogName 'OpenSSH/Operational' -MaxEvents 50 -ErrorAction SilentlyContinue |
    Format-Table TimeCreated, Id, Message -Wrap
# 进程创建审计（需已开启）
Get-WinEvent -FilterHashtable @{LogName='Security'; Id=4688} -MaxEvents 20 -ErrorAction SilentlyContinue |
    Format-Table TimeCreated, Message -Wrap
```

### 2.4 账户会话与临时目录

以**同一个账户**（平台用的那个）在控制台确认：

```powershell
whoami                      # 应与平台登录账户一致
$env:TEMP                   # 应存在且可写
Test-Path $env:TEMP
whoami /priv                # 本地管理员应含 SeAssignPrimaryToken / SeIncreaseQuota
```

若 `%TEMP%` 不可写或 profile 未加载，Windows OpenSSH 起 shell 时会直接报
`Access is denied.`。

### 2.5 策略类拦截

```powershell
# AppLocker / 软件限制策略会不会拦 cmd.exe / powershell.exe
Get-AppLockerPolicy -Effective -ErrorAction SilentlyContinue | Format-List
Get-ChildItem 'HKLM:\SOFTWARE\Policies\Microsoft\Windows\Safer' -Recurse -ErrorAction SilentlyContinue
```

### 2.6 旁证：同账户在控制台直跑

```powershell
powershell -NoProfile -Command "cmd /c whoami"
```

控制台正常、SSH 里被拒 ⇒ 问题确定在 **sshd 会话层**（2.1/2.2 的方向）。

---

## 3. 修好后的一键复验

```powershell
# 1) 平台侧 exec 通道
POST /ms/server/exec/command { hostIds:[2], command:"whoami; hostname", timeoutSeconds:30 }
#    期望：status=SUCCESS

# 2) Windows 发布逻辑真机自检（本仓库内，不依赖平台）
pwsh -File spring-support-server-starter/src/acceptance/win-release-selfcheck.ps1 -Port 18091
#    期望：passed=10 failed=0（T2 孤儿回收 / T3 端口归属探活，均带敏感性对照）

# 3) 平台侧发布五格（Linux 已通过的同一批用例搬到 Windows 项目上）
#    启 / 停 / 重启 / 升级 / 回滚 各跑一遍，并核对任务面板的步骤级日志
```

---

## 4. 顺带必须确认的第二件事（一旦 exec 修好就会立刻撞上）

**Windows 启动脚本依赖 `java` 在「机器级或用户级 PATH」上，而不是 `JAVA_HOME`。**

生成的启动脚本形如：

```powershell
$cmd = 'cmd /c java -jar <主入口> > "<日志>" 2>&1'
Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{ CommandLine = $cmd; CurrentDirectory = '<项目目录>' }
```

`Win32_Process.Create` 起的是**全新进程**，继承的是**机器/用户环境变量**，
`JAVA_HOME`（当前会话变量）不会被继承。若 `java` 不在机器级 PATH 上：
**`cmd` 立即报「不是内部或外部命令」并退出**，而 `WIN_VERIFY_RUNNING` 只校验了
`cmd.exe` 那一瞬间的 pid 还活着 —— **校验通过，随后进程已死**（实测本机即如此：
启动脚本打印 `running pid=...`，4 秒后进程数为 0）。

```powershell
# 检查
[Environment]::GetEnvironmentVariable('PATH','Machine') -match 'java'
[Environment]::GetEnvironmentVariable('PATH','User')    -match 'java'
```

**修复**：把 JDK 的 `bin` 目录加入**机器级** PATH（`JAVA_HOME` 单独设置无效）。

> 这是一个**产品级的校验盲区**，建议单独立项：启动脚本应改为用绝对路径调用
> `java`，或 `WIN_VERIFY_RUNNING` 应把「进程存活」的判定窗口拉长到覆盖 `cmd` 解析失败的时刻。

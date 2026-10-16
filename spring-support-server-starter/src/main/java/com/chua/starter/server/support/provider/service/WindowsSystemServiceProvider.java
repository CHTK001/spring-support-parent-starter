package com.chua.starter.server.support.provider.service;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.server.support.spi.SystemServiceContext;
import com.chua.starter.server.support.spi.SystemServiceProvider;
import lombok.extern.slf4j.Slf4j;

/**
 * Windows 系统服务挂载提供者（计划任务优先，无计划任务时回退到用户级自启 + 常驻看门狗）。
 *
 * <p><b>主路径（计划任务可用）</b>：{@code <svc>-autostart} 以 ONSTART + SYSTEM 建立
 * （不依赖交互登录，无登录会话的服务器上也能执行），{@code <svc>-guard} 每分钟跑一次看门狗脚本，
 * 状态脚本未报 running 就重新执行启动脚本。</p>
 *
 * <p><b>回退路径（加固主机）</b>：部分加固主机上 Task Scheduler 服务被安全策略置为
 * Stopped，且非提权远程会话无法启动它（实测 {@code Start-Service Schedule} 返回拒绝访问），
 * 此时 {@code schtasks} 全线不可用，旧的实现会直接抛「挂载失败」——功能在整台主机上不可用。
 * 这类主机改用<b>免提权</b>的用户级方案表达同样的语义：</p>
 * <ul>
 *     <li><b>开机自启</b>：写 {@code %APPDATA%\...\Startup\<svc>.cmd} 并登记
 *     {@code HKCU\...\Run} 值，登录时由用户域拉起，全程不需要提权；</li>
 *     <li><b>守护进程</b>：启动一个<b>常驻看门狗进程</b>（每 60s 执行一次看门狗脚本），
 *     用 WMI {@code Win32_Process.Create} 拉起（父进程为 WmiPrvSE，跨会话存活），
 *     并写 {@code <svc>.watchdog.pid} 作为可核验的落地凭据。</li>
 * </ul>
 *
 * <p>两条路径都做终态校验：主路径反查计划任务是否建立，回退路径反查启动项文件与注册表值、
 * 以及看门狗 pid 文件是否落地；卸载后同样反查全部产物是否清除。旧实现在「任务不存在」与
 * 「删除失败」两种情况下都报成功，调用方无法区分。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Spi(value = "windows", order = 100)
public class WindowsSystemServiceProvider implements SystemServiceProvider {

    @Override
    public String osType() {
        return "WINDOWS";
    }

    @Override
    public String defaultServiceName(SystemServiceContext ctx) {
        var base = isBlank(ctx.projectName()) ? "project" : ctx.projectName();
        var name = base.replaceAll("[^A-Za-z0-9_.-]+", "-").replaceAll("(^-|-$)", "");
        if (name.isEmpty()) {
            name = "project";
        }
        return "ms-" + name + "-" + ctx.projectId();
    }

    @Override
    public String launcher(SystemServiceContext ctx) {
        // Windows 侧不走「常驻启动器 + 服务管理器」模型：计划任务直接执行项目启动脚本，
        // 看门狗任务负责拉起，故启动器返回空串表示不适用。
        return "";
    }

    @Override
    public String install(SystemServiceContext ctx, String serviceName) {
        var dir = ctx.projectPath() + "\\.ms";
        var autoPath = dir + "\\" + serviceName + ".autostart.ps1";
        var watchPath = dir + "\\" + serviceName + ".watchdog.ps1";
        var residentPath = dir + "\\" + serviceName + ".resident.ps1";
        var pidPath = dir + "\\" + serviceName + ".watchdog.pid";
        boolean createsAutostart = ctx.autostart();
        boolean createsGuard = ctx.guardian() && !isBlank(ctx.statusScript());
        var ps = new StringBuilder();
        ps.append("$ErrorActionPreference = 'Stop'\n");
        // 本平台只靠计划任务/启动项表达挂载，两个开关都不开就什么都不会建，
        // 此时若继续执行只会写一个无人引用的 autostart.ps1 再打印「挂载完成」——
        // 挂载成功但什么都没发生，调用方无从分辨。这里直接判失败并说明原因。
        if (!createsAutostart && !createsGuard) {
            ps.append("throw '挂载失败: 未启用开机自启与守护进程，Windows 平台靠计划任务表达挂载，")
                    .append("两者都不启用不会创建任何系统服务'\n");
            return ps.toString();
        }
        ps.append("New-Item -ItemType Directory -Force -Path '").append(dir).append("' | Out-Null\n");
        // 开机自启脚本：定位到项目目录后执行启动脚本
        ps.append("$auto = @'\nSet-Location -LiteralPath '").append(ctx.projectPath()).append("'\n")
                .append(orEmpty(ctx.startScript())).append("\n'@\n");
        ps.append("[IO.File]::WriteAllText('").append(autoPath).append("', $auto)\n");
        // 看门狗脚本（守护启用时）：状态不含 running 则执行启动脚本
        if (createsGuard) {
            ps.append("$wd = @'\nSet-Location -LiteralPath '").append(ctx.projectPath()).append("'\n")
                    .append("$s = ").append(ctx.statusScript()).append("\n")
                    .append("if ($s -notmatch 'running') {\n").append(orEmpty(ctx.startScript()))
                    .append("\n}\n'@\n");
            ps.append("[IO.File]::WriteAllText('").append(watchPath).append("', $wd)\n");
        }
        // 先探测计划任务服务：Running 才用 schtasks（原生、无需登录会话）；
        // 否则回退到免提权的用户级自启 + 常驻看门狗。
        ps.append("$__sched = $false\n");
        ps.append("try { $__sched = ((Get-Service Schedule -ErrorAction Stop).Status -eq 'Running') }")
                .append(" catch { $__sched = $false }\n");
        ps.append("if ($__sched) {\n");
        if (createsAutostart) {
            // SYSTEM 账户 + ONSTART：不依赖交互登录（SSH/无登录的服务器上 ONLOGON 任务不会执行）
            ps.append("  schtasks /Create /TN '").append(serviceName).append("-autostart' /SC ONSTART /RU SYSTEM ")
                    .append("/TR \"powershell -ExecutionPolicy BYPASS -File '").append(autoPath).append("'\" /F\n");
        }
        if (createsGuard) {
            ps.append("  schtasks /Create /TN '").append(serviceName).append("-guard' /SC MINUTE /MO 1 /RU SYSTEM ")
                    .append("/TR \"powershell -ExecutionPolicy BYPASS -File '").append(watchPath).append("'\" /F\n");
        }
        if (createsAutostart) {
            ps.append("  if (-not (schtasks /Query /TN '").append(serviceName)
                    .append("-autostart' 2>$null)) { throw '挂载失败: 自启动任务未创建' }\n");
        }
        if (createsGuard) {
            ps.append("  if (-not (schtasks /Query /TN '").append(serviceName)
                    .append("-guard' 2>$null)) { throw '挂载失败: 守护任务未创建' }\n");
        }
        ps.append("} else {\n");
        // ---- 回退：免提权用户级自启（Startup 目录 + HKCU Run）----
        ps.append("  $__startup = [Environment]::GetFolderPath('Startup')\n");
        ps.append("  $__cmd = Join-Path $__startup '").append(serviceName).append(".cmd'\n");
        if (createsGuard) {
            // 常驻看门狗：先写自身 PID 供核验，再进循环（每 60s 跑一次单次看门狗）
            ps.append("  $__res = @'\n")
                    .append("[IO.File]::WriteAllText('").append(pidPath).append("', \"$PID\")\n")
                    .append("while ($true) {\n")
                    .append("  & powershell -NoProfile -ExecutionPolicy BYPASS -File '").append(watchPath).append("'\n")
                    .append("  Start-Sleep -Seconds 60\n")
                    .append("}\n'@\n");
            ps.append("  [IO.File]::WriteAllText('").append(residentPath).append("', $__res)\n");
        }
        // 启动项：登录时拉起应用；守护启用时一并拉起常驻看门狗（重启后仍能恢复守护）
        ps.append("  $__launcher = @'\n@echo off\n");
        if (createsGuard) {
            ps.append("start \"\" /min powershell -NoProfile -WindowStyle Hidden -ExecutionPolicy BYPASS -File \"")
                    .append(residentPath).append("\"\n");
        }
        ps.append("powershell -NoProfile -WindowStyle Hidden -ExecutionPolicy BYPASS -File \"").append(autoPath).append("\"\n'@\n");
        ps.append("  [IO.File]::WriteAllText($__cmd, $__launcher)\n");
        ps.append("  $__runKey = 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Run'\n");
        ps.append("  if (-not (Test-Path $__runKey)) { New-Item -Path $__runKey -Force | Out-Null }\n");
        ps.append("  Set-ItemProperty -Path $__runKey -Name '").append(serviceName)
                .append("' -Value ('\"' + $__cmd + '\"')\n");
        // 终态校验：启动项文件与注册表值必须真实落地
        ps.append("  if (-not (Test-Path $__cmd)) { throw '挂载失败: 启动项文件未创建' }\n");
        ps.append("  if ($null -eq (Get-ItemProperty -Path $__runKey -Name '").append(serviceName)
                .append("' -ErrorAction SilentlyContinue)) { throw '挂载失败: 注册表启动项未写入' }\n");
        if (createsGuard) {
            // 用 WMI 拉起常驻看门狗（父进程为 WmiPrvSE，跨会话存活），并等 pid 文件落地
            ps.append("  Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{ CommandLine = ")
                    .append("'powershell -NoProfile -WindowStyle Hidden -ExecutionPolicy BYPASS -File \"")
                    .append(residentPath).append("\"' } | Out-Null\n");
            ps.append("  $__ok = $false\n");
            ps.append("  for ($i = 0; $i -lt 20; $i++) { if (Test-Path '").append(pidPath)
                    .append("') { $__ok = $true; break }; Start-Sleep -Milliseconds 500 }\n");
            ps.append("  if (-not $__ok) { throw '挂载失败: 守护进程未拉起' }\n");
        }
        ps.append("}\n");
        ps.append("Write-Output '挂载完成: ").append(serviceName).append("'\n");
        return ps.toString();
    }

    @Override
    public String uninstall(SystemServiceContext ctx, String serviceName) {
        var dir = ctx.projectPath() + "\\.ms";
        var watchPath = dir + "\\" + serviceName + ".watchdog.ps1";
        var residentPath = dir + "\\" + serviceName + ".resident.ps1";
        var pidPath = dir + "\\" + serviceName + ".watchdog.pid";
        var ps = new StringBuilder();
        ps.append("$ErrorActionPreference = 'Continue'\n");
        // ---- 主路径产物：计划任务（可能不存在，容忍；用退出码判定，避免本地化错误文本误判）----
        ps.append("schtasks /Delete /TN '").append(serviceName).append("-autostart' /F 2>$null | Out-Null\n");
        ps.append("schtasks /Delete /TN '").append(serviceName).append("-guard' /F 2>$null | Out-Null\n");
        // ---- 回退产物：Startup 启动项 + HKCU Run 值 ----
        ps.append("$__startup = [Environment]::GetFolderPath('Startup')\n");
        ps.append("$__cmd = Join-Path $__startup '").append(serviceName).append(".cmd'\n");
        ps.append("if (Test-Path $__cmd) { Remove-Item -Force $__cmd }\n");
        ps.append("$__runKey = 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Run'\n");
        ps.append("if ($null -ne (Get-ItemProperty -Path $__runKey -Name '").append(serviceName)
                .append("' -ErrorAction SilentlyContinue)) { Remove-ItemProperty -Path $__runKey -Name '")
                .append(serviceName).append("' -Force -ErrorAction SilentlyContinue }\n");
        // ---- 常驻看门狗：按 pid 文件精确结束，再清产物 ----
        ps.append("if (Test-Path '").append(pidPath).append("') {\n");
        ps.append("  $__wp = (Get-Content '").append(pidPath).append("' -ErrorAction SilentlyContinue | Select-Object -First 1)\n");
        ps.append("  if ($__wp) { Stop-Process -Id ([int]$__wp) -Force -ErrorAction SilentlyContinue }\n");
        ps.append("  Remove-Item -Force '").append(pidPath).append("' -ErrorAction SilentlyContinue\n");
        ps.append("}\n");
        ps.append("Remove-Item -Force '").append(watchPath).append("' -ErrorAction SilentlyContinue\n");
        ps.append("Remove-Item -Force '").append(residentPath).append("' -ErrorAction SilentlyContinue\n");
        // ---- 终态校验：计划任务、启动项文件、注册表值都必须已不存在 ----
        ps.append("schtasks /Query /TN '").append(serviceName).append("-autostart' 2>$null | Out-Null\n");
        ps.append("if ($LASTEXITCODE -eq 0) { throw '卸载失败: 自启动任务仍存在' }\n");
        ps.append("schtasks /Query /TN '").append(serviceName).append("-guard' 2>$null | Out-Null\n");
        ps.append("if ($LASTEXITCODE -eq 0) { throw '卸载失败: 守护任务仍存在' }\n");
        ps.append("if (Test-Path $__cmd) { throw '卸载失败: 启动项文件仍存在' }\n");
        ps.append("if ($null -ne (Get-ItemProperty -Path $__runKey -Name '").append(serviceName)
                .append("' -ErrorAction SilentlyContinue)) { throw '卸载失败: 注册表启动项仍存在' }\n");
        ps.append("Write-Output '卸载完成: ").append(serviceName).append("'\n");
        return ps.toString();
    }

    /**
     * 空值归一为空串。
     *
     * @param value 原始值
     * @return 非空值
     */
    private String orEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 判断是否为空串。
     *
     * @param value 待判断值
     * @return 为空返回 true
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

package com.chua.starter.server.support.provider.service;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.server.support.spi.SystemServiceContext;
import com.chua.starter.server.support.spi.SystemServiceProvider;
import lombok.extern.slf4j.Slf4j;

/**
 * Windows 系统服务挂载提供者（计划任务：开机自启 + 守护）。
 *
 * <p>沿用 schtasks 方案：{@code <svc>-autostart} 以 ONSTART + SYSTEM 建立（不依赖交互登录，
 * 无登录会话的服务器上也能执行），{@code <svc>-guard} 每分钟跑一次看门狗脚本，
 * 状态脚本未报 running 就重新执行启动脚本。</p>
 *
 * <p>相对旧实现的修正：卸载不再以 {@code exit 0} 强制成功，而是删完两个任务后
 * 反查任务是否仍存在，仍存在即返回失败；旧实现在任务不存在与删除失败两种情况下
 * 都报成功，调用方无法区分。</p>
 *
 * <p>开机自启任务固定以 SYSTEM 账户运行（schtasks 对非 SYSTEM 账户要求交互式口令），
 * 故上下文的运行账户不参与本平台；需要按用户运行时应在项目脚本内自行切换。</p>
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
        var ps = new StringBuilder();
        ps.append("$ErrorActionPreference = 'Stop'\n");
        ps.append("New-Item -ItemType Directory -Force -Path '").append(dir).append("' | Out-Null\n");
        // 开机自启脚本：定位到项目目录后执行启动脚本
        ps.append("$auto = @'\nSet-Location -LiteralPath '").append(ctx.projectPath()).append("'\n")
                .append(orEmpty(ctx.startScript())).append("\n'@\n");
        ps.append("[IO.File]::WriteAllText('").append(autoPath).append("', $auto)\n");
        if (ctx.autostart()) {
            // SYSTEM 账户 + ONSTART：不依赖交互登录（SSH/无登录的服务器上 ONLOGON 任务不会执行）
            ps.append("schtasks /Create /TN '").append(serviceName).append("-autostart' /SC ONSTART /RU SYSTEM ")
                    .append("/TR \"powershell -ExecutionPolicy BYPASS -File '").append(autoPath).append("'\" /F\n");
        }
        if (ctx.guardian() && !isBlank(ctx.statusScript())) {
            // 看门狗脚本：状态不含 running 则执行启动脚本
            ps.append("$wd = @'\nSet-Location -LiteralPath '").append(ctx.projectPath()).append("'\n")
                    .append("$s = ").append(ctx.statusScript()).append("\n")
                    .append("if ($s -notmatch 'running') {\n").append(orEmpty(ctx.startScript()))
                    .append("\n}\n'@\n");
            ps.append("[IO.File]::WriteAllText('").append(watchPath).append("', $wd)\n");
            ps.append("schtasks /Create /TN '").append(serviceName).append("-guard' /SC MINUTE /MO 1 /RU SYSTEM ")
                    .append("/TR \"powershell -ExecutionPolicy BYPASS -File '").append(watchPath).append("'\" /F\n");
        }
        // 终态校验：按需建立的任务必须真实存在
        if (ctx.autostart()) {
            ps.append("if (-not (schtasks /Query /TN '").append(serviceName)
                    .append("-autostart' 2>$null)) { throw '挂载失败: 自启动任务未创建' }\n");
        }
        if (ctx.guardian() && !isBlank(ctx.statusScript())) {
            ps.append("if (-not (schtasks /Query /TN '").append(serviceName)
                    .append("-guard' 2>$null)) { throw '挂载失败: 守护任务未创建' }\n");
        }
        ps.append("Write-Output '挂载完成: ").append(serviceName).append("'\n");
        return ps.toString();
    }

    @Override
    public String uninstall(SystemServiceContext ctx, String serviceName) {
        var ps = new StringBuilder();
        ps.append("$ErrorActionPreference = 'Continue'\n");
        // 容忍任务本就不存在（未挂守护时没有 -guard），删除失败由末尾反查捕获
        ps.append("schtasks /Delete /TN '").append(serviceName).append("-autostart' /F 2>$null | Out-Null\n");
        ps.append("schtasks /Delete /TN '").append(serviceName).append("-guard' /F 2>$null | Out-Null\n");
        ps.append("if (schtasks /Query /TN '").append(serviceName)
                .append("-autostart' 2>$null) { throw '卸载失败: 自启动任务仍存在' }\n");
        ps.append("if (schtasks /Query /TN '").append(serviceName)
                .append("-guard' 2>$null) { throw '卸载失败: 守护任务仍存在' }\n");
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

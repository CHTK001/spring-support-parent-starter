package com.chua.starter.server.support.provider.service;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.server.support.spi.SystemServiceContext;
import com.chua.starter.server.support.spi.SystemServiceProvider;
import lombok.extern.slf4j.Slf4j;

/**
 * Linux 系统服务挂载提供者（systemd）。
 *
 * <p>写 {@code /etc/systemd/system/<svc>.service}，守护语义由 {@code Restart=always} 表达，
 * 开机自启由 {@code systemctl enable} 表达。</p>
 *
 * <p>相对旧实现的三点修正：</p>
 * <ul>
 *     <li>unit 写入 {@code User=}（上下文给出运行账户时），避免服务以 root 运行；</li>
 *     <li>提权统一用 {@code sudo -n}：主机无免密 sudo 时立即失败并给出可读原因，
 *     而不是在非交互 shell 里挂到密码提示；</li>
 *     <li>卸载不再「无论成败都成功」：逐条容忍噪声后校验 unit 与服务终态，
 *     未真正卸载时返回非零。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Spi(value = "linux", order = 100)
public class SystemdSystemServiceProvider implements SystemServiceProvider {

    @Override
    public String osType() {
        return "LINUX";
    }

    @Override
    public String defaultServiceName(SystemServiceContext ctx) {
        var base = isBlank(ctx.projectName()) ? "project" : ctx.projectName();
        var name = base.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (name.isEmpty()) {
            name = "project";
        }
        return "ms-" + name + "-" + ctx.projectId();
    }

    @Override
    public String launcher(SystemServiceContext ctx) {
        var sb = new StringBuilder("#!/bin/bash\n");
        sb.append("cd '").append(sq(ctx.projectPath())).append("'\n");
        ctx.env().forEach((k, v) -> sb.append("export ").append(k).append("='").append(sq(v)).append("'\n"));
        sb.append("JVM_ARGS='").append(sq(String.join(" ", ctx.jvmArgs()))).append("'\n");
        sb.append("APP_ARGS='").append(sq(String.join(" ", ctx.cmdArgs()))).append("'\n");
        sb.append("exec ").append(foreground(ctx)).append("\n");
        return sb.toString();
    }

    @Override
    public String install(SystemServiceContext ctx, String serviceName) {
        var dir = ctx.projectPath() + "/.ms";
        var launcher = dir + "/" + serviceName + ".run.sh";
        var unit = unit(ctx, serviceName, launcher);
        var sb = new StringBuilder("set -e\n");
        sb.append("mkdir -p '").append(sq(dir)).append("'\n");
        sb.append("cat > '").append(sq(launcher)).append("' <<'MS_LAUNCHER_EOF'\n")
                .append(launcher(ctx)).append("MS_LAUNCHER_EOF\n");
        sb.append("chmod +x '").append(sq(launcher)).append("'\n");
        sb.append("sudo -n tee '/etc/systemd/system/").append(serviceName).append(".service' > /dev/null <<'MS_UNIT_EOF'\n")
                .append(unit).append("MS_UNIT_EOF\n");
        sb.append("sudo -n systemctl daemon-reload\n");
        if (ctx.autostart()) {
            sb.append("sudo -n systemctl enable '").append(serviceName).append("'\n");
        } else {
            sb.append("sudo -n systemctl disable '").append(serviceName).append("' 2>/dev/null || true\n");
        }
        sb.append("sudo -n systemctl restart '").append(serviceName).append("'\n");
        // 终态校验：服务未真正起来就不算挂载成功
        sb.append("systemctl is-active '").append(serviceName).append("' > /dev/null\n");
        sb.append("echo '挂载完成: ").append(serviceName).append("'\n");
        return sb.toString();
    }

    @Override
    public String uninstall(SystemServiceContext ctx, String serviceName) {
        var launcher = ctx.projectPath() + "/.ms/" + serviceName + ".run.sh";
        var unitPath = "/etc/systemd/system/" + serviceName + ".service";
        var sb = new StringBuilder("set -e\n");
        // 逐条容忍「本来就不存在」的噪声，真实失败由末尾的终态校验捕获
        sb.append("sudo -n systemctl stop '").append(serviceName).append("' 2>/dev/null || true\n");
        sb.append("sudo -n systemctl disable '").append(serviceName).append("' 2>/dev/null || true\n");
        sb.append("sudo -n rm -f '").append(unitPath).append("'\n");
        sb.append("sudo -n systemctl daemon-reload\n");
        sb.append("rm -f '").append(sq(launcher)).append("'\n");
        sb.append("if systemctl is-active '").append(serviceName).append("' > /dev/null 2>&1; then ")
                .append("echo '卸载失败: 服务仍在运行'; exit 1; fi\n");
        sb.append("if [ -e '").append(unitPath).append("' ]; then ")
                .append("echo '卸载失败: unit 文件仍存在'; exit 1; fi\n");
        sb.append("echo '卸载完成: ").append(serviceName).append("'\n");
        return sb.toString();
    }

    /**
     * 生成 unit 内容。
     *
     * @param ctx         挂载上下文
     * @param serviceName 服务名
     * @param launcher    启动器绝对路径（ExecStart）
     * @return unit 文件内容
     */
    private String unit(SystemServiceContext ctx, String serviceName, String launcher) {
        var sb = new StringBuilder("[Unit]\n");
        sb.append("Description=").append(isBlank(ctx.projectName()) ? serviceName : ctx.projectName()).append("\n");
        sb.append("After=network.target\n\n[Service]\nType=simple\n");
        sb.append("WorkingDirectory=").append(ctx.projectPath()).append("\n");
        // 已知登录账户时按该用户运行，避免服务以 root 身份常驻
        if (!isBlank(ctx.runAsUser())) {
            sb.append("User=").append(ctx.runAsUser().trim()).append("\n");
        }
        sb.append("ExecStart=").append(launcher).append("\n");
        sb.append("Restart=").append(ctx.guardian() ? "always" : "no").append("\nRestartSec=5\n\n");
        sb.append("[Install]\nWantedBy=multi-user.target\n");
        return sb.toString();
    }

    /**
     * 生成前台常驻命令（由启动器 exec 接管，服务管理器跟踪到的即业务进程）。
     *
     * @param ctx 挂载上下文
     * @return POSIX 命令行
     */
    private String foreground(SystemServiceContext ctx) {
        var type = ctx.projectType() == null ? "" : ctx.projectType();
        return switch (type) {
            case "springboot", "fatjar" -> "java $JVM_ARGS -jar '" + sq(ctx.mainEntry()) + "' $APP_ARGS";
            case "separated" -> "'./" + sq(ctx.mainEntry()) + "' $JVM_ARGS $APP_ARGS";
            case "nginx", "static" -> "nginx -g 'daemon off;'";
            default -> "'./" + sq(ctx.mainEntry()) + "' $APP_ARGS";
        };
    }

    /**
     * POSIX 单引号转义。
     *
     * @param value 原始值
     * @return 转义后的值
     */
    private String sq(String value) {
        return value == null ? "" : value.replace("'", "'\\''");
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

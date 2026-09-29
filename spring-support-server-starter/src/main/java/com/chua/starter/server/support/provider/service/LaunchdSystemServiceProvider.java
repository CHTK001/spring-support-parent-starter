package com.chua.starter.server.support.provider.service;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.server.support.spi.SystemServiceContext;
import com.chua.starter.server.support.spi.SystemServiceProvider;
import lombok.extern.slf4j.Slf4j;

/**
 * macOS 系统服务挂载提供者（launchd 用户级 Agent）。
 *
 * <p>写 {@code ~/Library/LaunchAgents/<svc>.plist} 并用 {@code launchctl bootstrap gui/$(id -u)}
 * 注册到当前用户的 GUI 域。相比 systemd 方案有两个天然优势：</p>
 * <ul>
 *     <li><b>免提权</b>：LaunchAgents 属于用户域，挂载/卸载全程不需要 sudo，
 *     不存在「主机没配免密 sudo 就挂不上」的问题；</li>
 *     <li><b>语义原生</b>：{@code RunAtLoad} 即开机自启、{@code KeepAlive} 即守护进程，
 *     不需要 enable/disable 二次操作，也不会出现「已 enable 但没自启」的中间态。</li>
 * </ul>
 *
 * <p>卸载同样以终态校验收尾：plist 未被真正删除时返回非零。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Spi(value = "macos", order = 100)
public class LaunchdSystemServiceProvider implements SystemServiceProvider {

    /**
     * launchd 标签前缀（反向域名风格，避免与系统内置标签冲突）
     */
    private static final String LABEL_PREFIX = "com.chua.ms.";

    /**
     * 用户级 Agent 目录（相对 HOME，由脚本内 $HOME 展开）
     */
    private static final String AGENT_DIR = "$HOME/Library/LaunchAgents";

    @Override
    public String osType() {
        return "MACOS";
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
        var sb = new StringBuilder("set -e\n");
        sb.append("mkdir -p '").append(sq(dir)).append("' '").append(AGENT_DIR).append("'\n");
        sb.append("cat > '").append(sq(launcher)).append("' <<'MS_LAUNCHER_EOF'\n")
                .append(launcher(ctx)).append("MS_LAUNCHER_EOF\n");
        sb.append("chmod +x '").append(sq(launcher)).append("'\n");
        sb.append("cat > '").append(AGENT_DIR).append('/').append(serviceName).append(".plist' <<'MS_PLIST_EOF'\n")
                .append(plist(ctx, serviceName, launcher)).append("MS_PLIST_EOF\n");
        // 先卸载同名旧实例（bootstrap 要求未加载），再注册到当前用户 GUI 域
        sb.append("launchctl bootout 'gui/$(id -u)/").append(label(serviceName)).append("' 2>/dev/null || true\n");
        sb.append("launchctl bootstrap 'gui/$(id -u)' '").append(AGENT_DIR).append('/').append(serviceName).append(".plist'\n");
        sb.append("launchctl enable 'gui/$(id -u)/").append(label(serviceName)).append("'\n");
        sb.append("launchctl kickstart -k 'gui/$(id -u)/").append(label(serviceName)).append("'\n");
        // 终态校验：作业未真正加载就不算挂载成功
        sb.append("launchctl print 'gui/$(id -u)/").append(label(serviceName)).append("' > /dev/null\n");
        sb.append("echo '挂载完成: ").append(label(serviceName)).append("'\n");
        return sb.toString();
    }

    @Override
    public String uninstall(SystemServiceContext ctx, String serviceName) {
        var launcher = ctx.projectPath() + "/.ms/" + serviceName + ".run.sh";
        var plistPath = AGENT_DIR + "/" + serviceName + ".plist";
        var sb = new StringBuilder("set -e\n");
        sb.append("launchctl bootout 'gui/$(id -u)/").append(label(serviceName)).append("' 2>/dev/null || true\n");
        sb.append("rm -f '").append(plistPath).append("'\n");
        sb.append("rm -f '").append(sq(launcher)).append("'\n");
        sb.append("if launchctl print 'gui/$(id -u)/").append(label(serviceName)).append("' > /dev/null 2>&1; then ")
                .append("echo '卸载失败: 作业仍在 launchd 中'; exit 1; fi\n");
        sb.append("if [ -e '").append(plistPath).append("' ]; then ")
                .append("echo '卸载失败: plist 仍存在'; exit 1; fi\n");
        sb.append("echo '卸载完成: ").append(label(serviceName)).append("'\n");
        return sb.toString();
    }

    /**
     * 生成 plist 内容。
     *
     * @param ctx         挂载上下文
     * @param serviceName 服务名
     * @param launcher    启动器绝对路径
     * @return plist 文件内容
     */
    private String plist(SystemServiceContext ctx, String serviceName, String launcher) {
        var sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" ")
                .append("\"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n");
        sb.append("<plist version=\"1.0\">\n<dict>\n");
        sb.append("  <key>Label</key>\n  <string>").append(xml(label(serviceName))).append("</string>\n");
        sb.append("  <key>ProgramArguments</key>\n  <array>\n    <string>").append(xml(launcher))
                .append("</string>\n  </array>\n");
        sb.append("  <key>WorkingDirectory</key>\n  <string>").append(xml(ctx.projectPath())).append("</string>\n");
        // 开机自启与守护进程都是 plist 的原生键，无需额外 enable 动作
        sb.append("  <key>RunAtLoad</key>\n  ").append(ctx.autostart() ? "<true/>" : "<false/>").append("\n");
        sb.append("  <key>KeepAlive</key>\n  ").append(ctx.guardian() ? "<true/>" : "<false/>").append("\n");
        if (!isBlank(ctx.logPath())) {
            sb.append("  <key>StandardOutPath</key>\n  <string>").append(xml(ctx.logPath())).append("</string>\n");
            sb.append("  <key>StandardErrorPath</key>\n  <string>").append(xml(ctx.logPath())).append("</string>\n");
        }
        sb.append("</dict>\n</plist>\n");
        return sb.toString();
    }

    /**
     * 生成 launchd 作业标签。
     *
     * @param serviceName 服务名
     * @return 作业标签
     */
    private String label(String serviceName) {
        return LABEL_PREFIX + serviceName;
    }

    /**
     * 生成前台常驻命令。
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
     * XML 文本转义（plist 值节点）。
     *
     * @param value 原始值
     * @return 转义后的值
     */
    private String xml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
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

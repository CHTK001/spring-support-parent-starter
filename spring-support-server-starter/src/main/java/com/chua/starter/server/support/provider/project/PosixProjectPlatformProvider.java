package com.chua.starter.server.support.provider.project;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.server.support.spi.ProjectPlatformProvider;

import java.util.List;

/**
 * POSIX 平台（Linux）发布命令提供者。
 *
 * <p>覆盖 tar 备份/回滚、{@code mkdir -p && cd} 执行包装、{@code app.pid} 读取、
 * shell 变量块与参数展开、SpringBoot 外部配置写入、单引号转义规则。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Spi(value = "linux", order = 100)
public class PosixProjectPlatformProvider implements ProjectPlatformProvider {

    @Override
    public String osType() {
        return "LINUX";
    }

    @Override
    public String backup(String projectPath, int keep) {
        var file = "backup_" + System.currentTimeMillis() + ".tar.gz";
        // 排除 backups 自身（避免把正在写入的备份打包进去），并排除运行时 pid 文件：
        // 回滚会把备份里的 pid 还原成「指向备份时刻旧进程」的陈旧 pid，导致停止脚本判定已停止而不真正停止。
        return prefix(projectPath) + "mkdir -p backups && tar -czf backups/" + file
                + " --exclude='./backups' --exclude='./app.pid' --exclude='./logs/nginx.pid' . ; "
                + "ls -1dt backups/backup_* 2>/dev/null | tail -n +" + (keep + 1) + " | xargs -r rm -rf";
    }

    @Override
    public String listBackups(String projectPath) {
        return prefix(projectPath)
                + "if [ -d backups ]; then find backups -maxdepth 1 -name 'backup_*' | while read p; do "
                + "f=$(basename \"$p\"); "
                + "if [ -d \"$p\" ]; then kind=dir; size=; else kind=file; size=$(stat -c %s \"$p\"); fi; "
                + "epoch=$(stat -c %Y \"$p\"); "
                + "echo \"$f|$epoch|$size|$kind\"; done; fi";
    }

    @Override
    public String rollback(String projectPath, String backupName) {
        var p = prefix(projectPath);
        if (backupName != null && !backupName.isBlank()) {
            return p + "latest='backups/" + quote(backupName) + "' && [ -f \"$latest\" ] && tar -xzf \"$latest\"";
        }
        return p + "latest=$(ls -t backups/backup_*.tar.gz | head -1) && tar -xzf \"$latest\"";
    }

    @Override
    public String wrap(String projectPath, String script) {
        if (projectPath == null || projectPath.isBlank()) {
            return script;
        }
        return "mkdir -p '" + quote(projectPath) + "' && cd '" + quote(projectPath) + "' && " + script;
    }

    @Override
    public String readPid() {
        return "cat app.pid 2>/dev/null || true";
    }

    @Override
    public String varsBlock(List<String> envNames, List<String> envValues, List<String> jvmArgs, List<String> cmdArgs) {
        var b = new StringBuilder();
        for (var i = 0; i < envNames.size(); i++) {
            var k = envNames.get(i);
            if (k != null && !k.isBlank()) {
                var v = i < envValues.size() && envValues.get(i) != null ? envValues.get(i) : "";
                b.append("export ").append(k.trim()).append("='").append(quote(v)).append("'\n");
            }
        }
        b.append("JVM_ARGS='").append(joinInline(jvmArgs)).append("'\n");
        b.append("APP_ARGS='").append(joinInline(cmdArgs)).append("'\n");
        return b.toString();
    }

    @Override
    public String jvmArgsLiteral(List<String> jvmArgs) {
        return quotedArgs(jvmArgs);
    }

    @Override
    public String appArgsLiteral(List<String> cmdArgs) {
        return quotedArgs(cmdArgs);
    }

    @Override
    public String envPattern() {
        return "^export\\s+([A-Za-z_][A-Za-z0-9_]*)='(.*)'$";
    }

    @Override
    public String springConfigScript(List<String> keys, List<String> values) {
        var b = new StringBuilder();
        b.append("mkdir -p config\n");
        b.append("cat > config/application.properties <<'MS_CFG_EOF'\n");
        for (var i = 0; i < keys.size(); i++) {
            var k = keys.get(i);
            if (k != null && !k.isBlank()) {
                var v = i < values.size() && values.get(i) != null ? values.get(i) : "";
                b.append(k).append('=').append(v).append('\n');
            }
        }
        b.append("MS_CFG_EOF\n");
        return b.toString();
    }

    @Override
    public String extract(String projectPath, String packagePath) {
        var n = packagePath == null ? "" : packagePath.toLowerCase();
        var p = prefix(projectPath);
        if (n.endsWith(".zip")) {
            return p + "unzip -o '" + quote(packagePath) + "'";
        }
        if (n.endsWith(".gz") && !n.endsWith(".tar.gz")) {
            return p + "gunzip -f '" + quote(packagePath) + "'";
        }
        return p + "tar -xzf '" + quote(packagePath) + "'";
    }

    @Override
    public String place(String projectPath, String packagePath, String mainEntry) {
        var target = mainEntry == null || mainEntry.isBlank() ? baseName(packagePath) : mainEntry;
        return "cp -f '" + quote(packagePath) + "' '" + quote(projectPath) + "/" + quote(target) + "'";
    }

    @Override
    public String guardedRestart(String projectPath, String restartScript, String stopScript,
                                 String startScript, String statusScript, boolean restart) {
        var sb = new StringBuilder();
        if (restart) {
            sb.append(restartScript == null || restartScript.isBlank()
                    ? wrap(projectPath, stopScript) + "\n" + wrap(projectPath, startScript)
                    : wrap(projectPath, restartScript)).append('\n');
        }
        sb.append("sleep 4\n");
        sb.append("out=$(").append(wrap(projectPath, statusScript)).append(")\n");
        sb.append("if echo \"$out\" | grep -qi running; then\n  echo 'health ok'\nelse\n")
                .append("  echo 'health failed, rolling back...'\n  ")
                .append(wrap(projectPath, rollback(projectPath, null))).append('\n')
                .append("  ").append(wrap(projectPath, startScript).replace("\n", "\n  ")).append('\n')
                .append("  sleep 4\n")
                .append("  out2=$(").append(wrap(projectPath, statusScript)).append(")\n")
                .append("  if echo \"$out2\" | grep -qi running; then echo 'rollback ok'; else echo 'rollback failed'; exit 1; fi\n")
                .append("fi");
        return sb.toString();
    }

    @Override
    public String tailLog(String logPath, int lines) {
        return "tail -n " + lines + " " + logPath;
    }

    @Override
    public String quote(String value) {
        return value == null ? "" : value.replace("'", "'\\''");
    }

    /**
     * 取路径最后一段（投放时主入口为空则用包名）。
     *
     * @param path 路径
     * @return 文件名
     */
    private String baseName(String path) {
        var n = path == null ? "" : path.replace('\\', '/');
        var idx = n.lastIndexOf('/');
        return idx >= 0 ? n.substring(idx + 1) : n;
    }

    /**
     * 目录切换前缀。
     *
     * @param projectPath 项目地址
     * @return {@code cd '<path>' && } 或空串
     */
    private String prefix(String projectPath) {
        return projectPath == null || projectPath.isBlank() ? "" : "cd '" + quote(projectPath) + "' && ";
    }

    /**
     * 逐个单引号包裹并空格连接。
     *
     * <p>不能依赖 shell 的单词拆分：bash 会按空白拆分未加引号的展开，而 zsh 默认不拆分，
     * 会把整串参数当成一个参数传入（表现为 Spring 报 {@code Invalid value '8112 ...'}）。
     * 生成期逐个加引号可让 bash / zsh / ash 行为一致。</p>
     *
     * @param items 参数列表
     * @return 内联字符串
     */
    private String quotedArgs(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        var sb = new StringBuilder();
        for (var it : items) {
            if (it == null || it.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append('\'').append(quote(it.trim())).append('\'');
        }
        return sb.toString();
    }

    /**
     * 空格连接（用于已处于单引号上下文中的变量赋值，不再额外包裹引号）。
     *
     * @param items 参数列表
     * @return 内联字符串
     */
    private String joinInline(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        var sb = new StringBuilder();
        for (var it : items) {
            if (it == null || it.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(quote(it.trim()));
        }
        return sb.toString();
    }
}

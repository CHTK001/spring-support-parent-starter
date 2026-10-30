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

    /**
     * 健康检查「就绪等待」上限（秒）：轮询状态脚本直到报告运行中。
     *
     * <p>替代原先固定的 {@code sleep 4}——4 秒对 SpringBoot 类应用过短，
     * 会把正常慢启动误判成失败并触发回滚。</p>
     */
    private static final int READY_WAIT_SECONDS = 20;

    /**
     * 健康检查「稳定窗口」（秒）：就绪后要求连续该时长仍报告运行中，
     * 用于捕捉「启动完成即退出」的崩溃循环。
     */
    private static final int SETTLE_SECONDS = 10;

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
        String pick = (backupName != null && !backupName.isBlank())
                ? "latest='backups/" + quote(backupName) + "'"
                : "latest=$(ls -t backups/backup_*.tar.gz 2>/dev/null | head -1)";
        // 回滚不是「覆盖」而是「回到备份时刻」：升级新增的文件备份里根本没有，
        // 只解压会让它们原样留在目录里（实测：升级前没有 app.jar，回滚后新 jar 仍在、
        // md5 与升级后完全一致，等于没回滚）。所以先按备份清单算出「当前有、备份没有」
        // 的新增文件并删掉，再解压，才真正回到备份时刻。
        // 备份目录自身必须排除，否则会把所有历史备份一起删掉。
        return p + pick + " && [ -f \"$latest\" ] || { echo '没有可用备份' >&2; exit 1; }; "
                + "__keep=$(mktemp) && __cur=$(mktemp) && "
                + "tar -tzf \"$latest\" | sed 's|^\\./||' | grep -v -e '/$' -e '^$' | sort -u > \"$__keep\"; "
                + "find . -path ./backups -prune -o -type f -print | sed 's|^\\./||' | sort -u > \"$__cur\"; "
                + "comm -23 \"$__cur\" \"$__keep\" | while IFS= read -r __f; do rm -f -- \"$__f\"; done; "
                + "rm -f \"$__keep\" \"$__cur\"; "
                + "tar -xzf \"$latest\"";
    }

    @Override
    public String wrap(String projectPath, String script) {
        if (projectPath == null || projectPath.isBlank()) {
            return script;
        }
        return "mkdir -p '" + quote(projectPath) + "' && cd '" + quote(projectPath) + "' && " + script;
    }

    @Override
    public String ensureDir(String dir) {
        return "mkdir -p '" + quote(dir) + "'";
    }

    @Override
    public String readPid() {
        return "cat app.pid 2>/dev/null || true";
    }

    @Override
    public String readPid(String pidFile) {
        String file = pidFile == null || pidFile.isBlank() ? "app.pid" : pidFile.trim();
        return "cat '" + quote(file) + "' 2>/dev/null || true";
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
        return guardedRestart(projectPath, restartScript, stopScript, startScript, statusScript, restart, 0);
    }

    @Override
    public String guardedRestart(String projectPath, String restartScript, String stopScript,
                                 String startScript, String statusScript, boolean restart, int port) {
        var status = wrap(projectPath, statusScript);
        var start = wrap(projectPath, startScript);
        var sb = new StringBuilder();
        if (restart) {
            sb.append(restartScript == null || restartScript.isBlank()
                    ? wrap(projectPath, stopScript) + "\n" + start
                    : wrap(projectPath, restartScript)).append('\n');
        }
        if (port > 0) {
            // 必须确认「监听者是 app.pid 记录的那个进程」，否则无关进程/残留孤儿监听同端口
            // 会被误判成「新版本健康」（实测：别的会话在同端口起 mock 服务，新版本压根没绑上，
            // 健康检查却报 ok）。
            //
            // ⚠ 这里刻意用 awk 的 index() 做全字面匹配，不用 grep 的 [[:space:]] 字符类：
            // 下发命令由目标机登录 shell 执行，kali 的登录 shell 是 zsh，而 zsh 会把
            // 双引号内的 [[:space:]] 当作 printf 格式说明符并报
            // 「bad output format specification」，导致函数恒返回失败 —— 健康检查
            // 于是把正常启动的应用判成失败并回滚（实测踩到）。sh/bash 下无此问题。
            sb.append("listening_port() {\n");
            sb.append("  __want=$1\n");
            sb.append("  [ -f app.pid ] || return 1\n");
            sb.append("  __owner=$(cat app.pid 2>/dev/null)\n");
            sb.append("  [ -n \"$__owner\" ] || return 1\n");
            sb.append("  if command -v ss >/dev/null 2>&1; then\n");
            sb.append("    ss -ltnp 2>/dev/null | awk -v p=\":$__want \" -v o=\"pid=$__owner\" 'index($0,p) && index($0,o) { f=1 } END { exit(f?0:1) }'\n");
            sb.append("  elif command -v netstat >/dev/null 2>&1; then\n");
            // netstat 不带 -p 拿不到 pid；退回「端口存在即可」，属已知的弱化路径
            sb.append("    netstat -ltn 2>/dev/null | awk -v p=\":$__want\" '$4 ~ (p \"$\") { f=1 } END { exit(f?0:1) }'\n");
            sb.append("  else\n");
            sb.append("    return 0\n");
            sb.append("  fi\n");
            sb.append("}\n");
        }
        // 原实现是「sleep 4 后查一次状态」：既把慢启动应用误判成失败（4 秒起不完就回滚好版本），
        // 也把「起来后立刻崩」误判成成功（4 秒窗口内恰好还活着）。改为三段式：
        //   阶段1 就绪等待：轮询状态脚本直到报告运行中，最多 READY_WAIT_SECONDS 秒
        //   阶段2 端口探活：给定端口时确认其已监听（识别「进程活着但没绑上端口」）
        //   阶段3 稳定窗口：连续 SETTLE_SECONDS 秒仍报告运行中，捕捉启动后退出
        sb.append("__up=0; __i=0\n");
        sb.append("while [ $__i -lt ").append(READY_WAIT_SECONDS).append(" ]; do\n");
        sb.append("  __o=$(").append(status).append(")\n");
        sb.append("  if echo \"$__o\" | grep -qi running; then __up=1; break; fi\n");
        sb.append("  sleep 2; __i=$((__i+2))\n");
        sb.append("done\n");
        if (port > 0) {
            sb.append("if [ $__up -eq 1 ]; then\n");
            sb.append("  __i=0\n");
            sb.append("  while [ $__i -lt ").append(READY_WAIT_SECONDS).append(" ]; do\n");
            sb.append("    if listening_port ").append(port).append("; then break; fi\n");
            sb.append("    sleep 2; __i=$((__i+2))\n");
            sb.append("  done\n");
            sb.append("  if ! listening_port ").append(port).append("; then\n");
            sb.append("    echo 'health failed: 端口 ").append(port).append(" 未监听'\n");
            sb.append("    __up=0\n");
            sb.append("  fi\n");
            sb.append("fi\n");
        }
        sb.append("if [ $__up -eq 1 ]; then\n");
        sb.append("  __i=0\n");
        sb.append("  while [ $__i -lt ").append(SETTLE_SECONDS).append(" ]; do\n");
        sb.append("    sleep 2; __i=$((__i+2))\n");
        sb.append("    __o=$(").append(status).append(")\n");
        sb.append("    if ! echo \"$__o\" | grep -qi running; then\n");
        sb.append("      echo 'health failed: 启动后退出'\n");
        sb.append("      __up=0; break\n");
        sb.append("    fi\n");
        sb.append("  done\n");
        sb.append("fi\n");
        sb.append("if [ $__up -eq 1 ]; then\n  echo 'health ok'\nelse\n")
                .append("  echo 'health failed, rolling back...'\n");
        // 必须先停掉刚起的失败实例再回滚：失败可能是「进程仍在跑但端口没绑上」，
        // 此时直接回滚并重启，新进程会与它抢端口、抢 jar 文件（Windows 上 jar 还被占用），
        // 实测表现为「回滚后 rollback failed」——旧进程活着、新进程起不来。
        if (stopScript != null && !stopScript.isBlank()) {
            sb.append("  ").append(wrap(projectPath, stopScript).replace("\n", "\n  ")).append('\n');
        }
        sb.append("  ").append(rollback(projectPath, null)).append('\n')
                .append("  sleep 2\n")
                .append("  ").append(start.replace("\n", "\n  ")).append('\n')
                .append("  sleep ").append(SETTLE_SECONDS + 4).append('\n')
                .append("  __o2=$(").append(status).append(")\n")
                .append("  if echo \"$__o2\" | grep -qi running; then echo 'rollback ok'; else echo 'rollback failed'; exit 1; fi\n")
                // 回滚成功也必须以非 0 退出：对「把新版本放上去并让它跑起来」这个操作而言，
                // 健康检查失败即操作失败，回滚只是止损。实测踩过：回滚成功后 exit 0，
                // 升级任务整体被判 success、日志末尾打「升级成功」，运维据此以为新版本已上线，
                // 而线上跑的是旧版本 —— 这种谎报比直接报错更危险。
                .append("  echo 'upgrade failed, rolled back to previous version'\n")
                .append("  exit 1\n")
                .append("fi");
        return sb.toString();
    }

    @Override
    public String tailLog(String logPath, int lines) {
        // 日志路径历史上出现过被配成目录（如 <projectPath>/app/logs）的情况，
        // 直接 tail 目录只会得到 "Is a directory"，前端看到的就是空日志；
        // 文件不存在时也应明确回一句提示，而不是静默返回空串。
        String p = "'" + quote(logPath) + "'";
        return "p=" + p + "; if [ -d \"$p\" ]; then "
                + "f=$(ls -tp \"$p\"/* 2>/dev/null | grep -v '/$' | head -n 1); "
                + "[ -z \"$f\" ] && f=\"$p/app.log\"; else f=\"$p\"; fi; "
                + "if [ -f \"$f\" ]; then tail -n " + lines + " \"$f\"; "
                + "else echo \"日志文件不存在: $f\"; fi";
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

package com.chua.starter.monitor.support.nginx.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.monitor.support.nginx.spi.NginxCommandProvider;
import com.chua.starter.monitor.support.spi.Platforms;

/**
 * POSIX（Linux / macOS）平台的 nginx 命令文本。
 *
 * <p>这里的每条命令都是从原先 {@code NginxManageServiceImpl} 里逐条搬出来的，语义必须等价：
 * 搬动时唯一允许的变化是路径改用 {@link #quote(String)} 拼接，不再由调用方手工拼串。
 * 有退化分支的（如进程探测 {@code pgrep} → {@code ps -e}）保留两级退化，不要简化成一
 * 级——{@code pgrep} 在部分精简镜像里没有。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Spi(value = "nginx-posix", order = 200)
public class PosixNginxCommandProvider implements NginxCommandProvider {

    /**
     * 单次增量读取的字节上限（256KB）。
     *
     * <p>与既有行为一致：日志暴涨时不能一次把几十 MB 拽回来。放在实现里而不是让调用方
     * 拼 {@code | head -c}，是因为那是 POSIX 管道，Windows 没有对应写法。</p>
     */
    private static final int MAX_DELTA_BYTES = 262144;

    /**
     * POSIX 自证命令：输出内核名（{@code Linux} / {@code Darwin}）。
     *
     * <p>不加 {@code 2>/dev/null}：路由器用退出码 + 输出双重判定，错误信息留在输出里
     * 反而便于排障；并且在 Windows 上这条命令本就该失败。</p>
     */
    private static final String PROBE = "uname -s";

    @Override
    public boolean supports(String platform) {
        return Platforms.LINUX.equals(platform) || Platforms.MACOS.equals(platform);
    }

    @Override
    public String name() {
        return "nginx-posix";
    }

    @Override
    public String quote(String value) {
        // POSIX 单引号内除单引号外全是字面量；单引号自身用 '\'' 收尾-转义-重开
        return "'" + (value == null ? "" : value).replace("'", "'\\''") + "'";
    }

    @Override
    public String probePlatform() {
        return PROBE;
    }

    @Override
    public String parsePlatform(String probeOutput) {
        String text = probeOutput == null ? "" : probeOutput;
        // 只认自己这两个平台：Windows 的判定交给 WindowsNginxCommandProvider，
        // 免得本条命令在别的平台上误认领
        if (text.contains("Darwin") || text.contains("darwin")) {
            return Platforms.MACOS;
        }
        if (text.contains("Linux") || text.contains("linux")) {
            return Platforms.LINUX;
        }
        return "";
    }

    @Override
    public boolean looksLikeError(String output) {
        String text = output == null ? "" : output;
        return text.contains("not found")
                || text.contains("No such file")
                || text.contains("command not found")
                || text.contains("Permission denied");
    }

    @Override
    public String serviceControl(String unit, String action) {
        // sudo -n：非交互；免密未配时立刻失败，而不是挂在等口令上
        return "sudo -n systemctl " + action + " " + quote(unit);
    }

    @Override
    public String serviceUnitOfProcess() {
        // 只认 ExecStart 里明确出现 nginx 的单元，绝不猜；认错单元等于去动一台机器上无关的服务
        return "command -v systemctl >/dev/null 2>&1 || exit 0\n"
                + "systemctl list-units --type=service --all --no-legend --no-pager --plain 2>/dev/null |\n"
                + "while read -r u _rest; do\n"
                + "  case \"$u\" in *.service) ;; *) continue ;; esac\n"
                + "  cmd=$(systemctl show \"$u\" -p ExecStart --value 2>/dev/null) || continue\n"
                + "  case \"$cmd\" in *nginx*) echo \"$u\" ;; esac\n"
                + "done";
    }

    @Override
    public String serviceMainPid(String unit) {
        return "systemctl show " + quote(unit) + " -p MainPID --value 2>/dev/null";
    }

    @Override
    public String processAlive(String confTail) {
        // 三级退化：pgrep（Linux / 部分 BSD）→ ps -e | grep
        String byPgrep = "pgrep -af nginx 2>/dev/null | grep -F -- " + quote(confTail) + " >/dev/null && echo ON || echo OFF";
        String byPs = "ps -e 2>/dev/null | grep -F -- " + quote(confTail) + " | grep -qi '[n]ginx' && echo ON || echo OFF";
        return "if command -v pgrep >/dev/null 2>&1; then\n  " + byPgrep + "\nelse\n  " + byPs + "\nfi";
    }

    @Override
    public String processPid(String confTail) {
        return "pgrep -af nginx 2>/dev/null | grep -F -- " + quote(confTail) + " | head -1 | cut -d' ' -f1";
    }

    @Override
    public String readFile(String path) {
        return "cat " + quote(path);
    }

    @Override
    public String fileExists(String path) {
        return "test -f " + quote(path);
    }

    @Override
    public String makeDirs(String dir) {
        return "mkdir -p " + quote(dir);
    }

    @Override
    public String copyFile(String source, String destination) {
        return "cp -p " + quote(source) + " " + quote(destination);
    }

    @Override
    public String listByModifiedDesc(String dir, String suffix) {
        return "ls -1t " + quote(dir) + "/*" + suffix + " 2>/dev/null";
    }

    @Override
    public String tailLines(String path, int lines) {
        return "tail -n " + lines + " " + quote(path) + " 2>&1";
    }

    @Override
    public String fileSize(String path) {
        return "wc -c < " + quote(path) + " 2>/dev/null | tr -d ' '";
    }

    @Override
    public String bytesFrom(String path, long start) {
        // tail -c +N 的 N 是 1 基，从第 N 个字节开始；再截断到 256KB，
        // 避免日志暴涨时一次传输过大（调用方不再自己拼 head）
        return "tail -c +" + (start + 1) + " " + quote(path) + " 2>/dev/null | head -c " + MAX_DELTA_BYTES;
    }

    @Override
    public String findFiles(String root, String pattern) {
        return "find " + quote(root) + " -name " + quote(pattern) + " -type f 2>/dev/null";
    }

    @Override
    public String listFilesWithMeta(String root, String namePattern, boolean recursive) {
        // 不用 find -printf（GNU 扩展，BSD/macOS 没有）；时间戳走 date -r，
        // 失败再退到 stat -c，两个平台分支都保留
        String scope = recursive ? "" : " -maxdepth 1";
        return "find " + quote(root) + scope + " -name " + quote(namePattern) + " -type f 2>/dev/null | while read -r f; do "
                + "printf '%s\\t%s\\t%s\\n' \"$f\" \"$(wc -c < \"$f\" 2>/dev/null | tr -d ' ')\" "
                + "\"$(date -r \"$f\" '+%Y-%m-%d %H:%M' 2>/dev/null || stat -c '%y' \"$f\" 2>/dev/null | cut -d. -f1)\"; "
                + "done";
    }

    @Override
    public String writeFile(String path, String base64Content) {
        // 语义与原先的 writeFile 完全一致（printf | base64 -d >），只是搬到这里
        return "printf '%s' " + quote(base64Content) + " | base64 -d > " + quote(path);
    }

    @Override
    public String dirExists(String path) {
        return "test -d " + quote(path);
    }

    @Override
    public String parentDir(String path) {
        // cd 到父目录再 pwd：pwd 会解析软链接，拿到真实目录
        return "cd \"$(dirname " + quote(path) + ")\" && pwd";
    }

    @Override
    public String homeDir() {
        return "printf '%s' \"$HOME\"";
    }

    @Override
    public String directiveMatches(String root, String namePattern, String directive, int maxLines) {
        // 保留原写法：无行首锚点、字面量指令名 + POSIX 空白类 + 排除分号
        String grep = "grep -rhoE " + quote(directive + "[[:space:]]+[^;]+") + " " + quote(root)
                + " --include=" + quote(namePattern) + " 2>/dev/null";
        return maxLines > 0 ? grep + " | head -" + maxLines : grep;
    }

    @Override
    public String which(String command) {
        return "command -v " + quote(command) + " 2>/dev/null";
    }

    @Override
    public String versionDump(String binary) {
        return binary(binary) + " -V 2>&1";
    }

    @Override
    public String listenRows() {
        return "ss -lntp 2>/dev/null || netstat -lntp 2>/dev/null";
    }

    @Override
    public String nginxInvocation(String binary, String prefix, String conf, String action) {
        StringBuilder sb = new StringBuilder(binary(binary));
        if (prefix != null && !prefix.isBlank()) {
            sb.append(" -p ").append(quote(prefix));
        }
        if (conf != null && !conf.isBlank()) {
            sb.append(" -c ").append(quote(conf));
        }
        String act = action == null ? "" : action.trim();
        if (!act.isEmpty()) {
            sb.append(' ').append(act);
        }
        return sb.toString();
    }

    /**
     * nginx 可执行文件名：空则用 PATH 里的 {@code nginx}。
     *
     * <p>非静态：内部要用 {@link #quote(String)}，那是接口的实例方法。
     * 之前写成 {@code static} 会在编译期报「静态上下文引用非静态方法」。</p>
     *
     * @param binary 配置的可执行文件
     * @return 实际使用的命令名
     */
    private String binary(String binary) {
        return binary == null || binary.isBlank() ? "nginx" : quote(binary);
    }
}

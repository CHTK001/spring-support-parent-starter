package com.chua.starter.datasourcehub.support.support;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 文件库（SQLite / H2 file / Derby / HSQLDB）JDBC URL 的路径守卫。
 *
 * <p>这类连接串里带的是**磁盘路径**，而连接串存放在数据源台账里 ——
 * 台账能被编辑，路径就会落到应用进程手里执行。不校验的话，
 * {@code jdbc:sqlite:/tmp/x/../../../etc/cron.d/x} 这种串能让探活与 SQL 执行
 * 以应用权限读写任意文件。所以两道闸一起上：</p>
 *
 * <ol>
 *   <li><b>穿越段</b>：路径里出现 {@code ..} 直接拒绝，不依赖规范化；</li>
 *   <li><b>白名单</b>：路径 canonical 化（消掉相对段与软链）后必须落在
 *       允许的根目录之内 —— 规范化能挡住没写 {@code ..} 的软链越界，
 *       但挡不住「规范化成功、位置却在白名单外」的绝对路径。</li>
 * </ol>
 *
 * <p>只管本地文件形态；{@code mem:}、{@code tcp://}、{@code hsql://}、
 * {@code res:} 这类不碰本地磁盘的形态一律放行。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class FileJdbcPathGuard {

    private FileJdbcPathGuard() {
    }

    /**
     * 校验文件库 JDBC URL 的磁盘路径。
     *
     * @param url             整串 JDBC URL
     * @param extraAllowedDirs 配置追加的允许目录（可为 null/空）
     * @return 错误文案；null 表示通过，或该 URL 与本地文件路径无关
     */
    public static String pathError(String url, List<String> extraAllowedDirs) {
        String path = extractLocalPath(url);
        if (path == null) {
            return null;
        }
        if (path.indexOf('\0') >= 0) {
            return "文件库路径包含非法字符";
        }
        // 穿越段：路径分隔符两种都算，大小写无关的前缀匹配交给下面的白名单兜底
        if (path.replace('\\', '/').matches("(?i).*(^|/)\\.\\.(/|$).*")) {
            return "文件库路径不允许包含 .. 穿越段: " + path;
        }
        // H2 的 ~/ 表示用户主目录：先展开再比较，否则 canonical 化后必出白名单
        if (path.equals("~") || path.startsWith("~/") || path.startsWith("~\\")) {
            path = System.getProperty("user.home", "") + path.substring(1);
        }
        String canonical;
        try {
            canonical = new File(path).getCanonicalPath();
        } catch (IOException e) {
            return "文件库路径无法解析: " + e.getMessage();
        }
        List<String> roots = new ArrayList<>();
        for (File root : defaultRoots(extraAllowedDirs)) {
            String rootPath;
            try {
                rootPath = root.getCanonicalPath();
            } catch (IOException e) {
                rootPath = root.getAbsolutePath();
            }
            roots.add(rootPath);
            // 精确等于根目录本身，或位于根目录之下 —— 必须带分隔符比较，
            // 否则 /tmp 会把 /tmpfoo 也当成自己人
            if (canonical.equals(rootPath) || canonical.startsWith(rootPath + File.separator)) {
                return null;
            }
        }
        return "文件库路径越界: " + canonical + "，仅允许位于 " + String.join("、", roots)
                + "（可用 plugin.datasource.hub.file-jdbc-allowed-dirs 放行更多目录）";
    }

    /**
     * 允许的根目录：工作目录、系统临时目录、用户主目录 + 配置追加。
     *
     * <p>三个默认值分别覆盖「相对路径落盘」「/tmp 试用一把」「{@code jdbc:h2:~/data}」
     * 三种正常用法 —— 白名单挡越界，但不能把正常用法也挡掉。</p>
     *
     * @param extraAllowedDirs 配置追加的目录，可为 null/空
     * @return 根目录列表
     */
    private static List<File> defaultRoots(List<String> extraAllowedDirs) {
        List<File> roots = new ArrayList<>();
        roots.add(new File(System.getProperty("user.dir", ".")));
        roots.add(new File(System.getProperty("java.io.tmpdir", "/tmp")));
        String home = System.getProperty("user.home");
        if (home != null && !home.isBlank()) {
            roots.add(new File(home));
        }
        if (extraAllowedDirs != null) {
            for (String configured : extraAllowedDirs) {
                if (configured != null && !configured.isBlank()) {
                    roots.add(new File(configured.trim()));
                }
            }
        }
        return roots;
    }

    /**
     * 从 JDBC URL 里抠出本地文件路径；非文件族或非本地形态返回 null。
     *
     * <p>剥离顺序有讲究：先切参数段（H2/Derby/HSQLDB 用 {@code ;}，
     * SQLite URI 形态用 {@code ?}），再剥 {@code file:} 前缀，最后识别
     * {@code :memory:} —— 顺序反了会把 {@code file::memory:?x=1} 当成路径。</p>
     *
     * @param url 整串 JDBC URL
     * @return 本地文件路径；null 表示与本地文件路径无关
     */
    static String extractLocalPath(String url) {
        if (url == null) {
            return null;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        String rest;
        if (lower.startsWith("jdbc:sqlite:")) {
            rest = url.substring("jdbc:sqlite:".length());
        } else if (lower.startsWith("jdbc:h2:")) {
            rest = url.substring("jdbc:h2:".length());
            String r = rest.toLowerCase(Locale.ROOT);
            // H2 的 mem:/tcp:/ssl:/ws: 都不落本地文件
            if (r.startsWith("mem:") || r.startsWith("tcp:") || r.startsWith("ssl:")
                    || r.startsWith("ws:") || r.startsWith("//")) {
                return null;
            }
        } else if (lower.startsWith("jdbc:derby:")) {
            rest = url.substring("jdbc:derby:".length());
            String r = rest.toLowerCase(Locale.ROOT);
            // Derby 的 memory: 与 //网络客户端 形态不落本地文件
            if (r.startsWith("memory:") || r.startsWith("//")) {
                return null;
            }
        } else if (lower.startsWith("jdbc:hsqldb:")) {
            rest = url.substring("jdbc:hsqldb:".length());
            String r = rest.toLowerCase(Locale.ROOT);
            // HSQLDB 的 mem:、hsql:// 服务端、res: 类路径资源不落本地文件
            if (r.startsWith("mem:") || r.startsWith("hsql:") || r.startsWith("res:")
                    || r.startsWith("//")) {
                return null;
            }
        } else {
            return null;
        }
        int semi = rest.indexOf(';');
        if (semi >= 0) {
            rest = rest.substring(0, semi);
        }
        int question = rest.indexOf('?');
        if (question >= 0) {
            rest = rest.substring(0, question);
        }
        rest = rest.trim();
        if (rest.toLowerCase(Locale.ROOT).startsWith("file:")) {
            rest = rest.substring("file:".length());
        }
        rest = rest.trim();
        if (rest.isEmpty() || rest.equalsIgnoreCase(":memory:")) {
            return null;
        }
        return rest;
    }
}

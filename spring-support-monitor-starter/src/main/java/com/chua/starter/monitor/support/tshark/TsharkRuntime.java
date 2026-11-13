// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.monitor.support.tshark;

import com.chua.starter.monitor.support.config.TsharkInstallProperties;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * tshark 运行时：探测本机可用的可执行文件，缺失时在虚拟线程里自动补齐。
 *
 * <p><b>为什么由服务端自己补</b>：抓包页的绝大多数能力（导入 pcap、解析、统计、看生命周期）
 * 只依赖 tshark 本体，不依赖抓包驱动。让运维为一台干净机器手工装一遍 Wireshark，
 * 成本和「页面直接告诉他人去哪装」一样高，却每次部署都要重复一次。</p>
 *
 * <p><b>启动即补，且只补一次</b>：挂在 {@link ApplicationReadyEvent} 上，
 * 用 {@code Thread.ofVirtual()} 起一条虚拟线程 —— 下载可能耗时几十秒，
 * 绝不能占用 Web 线程或拖慢启动。多实例 / 重复触发由 {@link #installing} 拦住。</p>
 *
 * <p><b>只补本体，不装驱动</b>：便携包解压即用，无需管理员权限；实时抓包所需的 Npcap
 * （Windows）/ libpcap（Linux）单独探测，通过 {@link TsharkRuntimeStatus#captureReady()}
 * 如实上报，页面据此提示，而不是静默装一个内核驱动。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
public class TsharkRuntime {

    /**
     * 环境变量：显式指定 tshark 可执行文件。
     */
    private static final String ENV_TSHARK_BIN = "TSHARK_BIN";

    /**
     * 环境变量：覆盖自动安装目录。
     */
    private static final String ENV_INSTALL_DIR = "TSHARK_INSTALL_DIR";

    /**
     * 可执行文件名（按平台）。
     */
    private static final String EXE_NAME = isWindows() ? "tshark.exe" : "tshark";

    /**
     * 兜底命令名：交给系统按 PATH 解析，行为与改造前一致。
     */
    private static final String PATH_BIN = "tshark";

    /**
     * 探测版本 / 列表网卡的超时秒数。
     */
    private static final long PROBE_TIMEOUT_SECONDS = 10L;

    /**
     * 下载缓冲区大小。
     */
    private static final int BUFFER_SIZE = 8192;

    /**
     * 进度推送的最小间隔（毫秒）：下载每读一小块就推一次会刷爆 SSE 与浏览器。
     */
    private static final long PROGRESS_INTERVAL_MS = 300L;

    /**
     * 下载阶段占进度条的区间：[0, 90]，留 10 给解压与自检。
     */
    private static final int DOWNLOAD_WEIGHT = 90;

    /**
     * 解压时最多允许的条目数，防 zip 炸弹。
     */
    private static final int MAX_UNZIP_ENTRIES = 5000;

    /**
     * 解压后查找可执行文件的最大目录深度。
     */
    private static final int MAX_SCAN_DEPTH = 4;

    /**
     * 解包命令（tar/ar）的输出尾部保留行数：报错时给出最后几行，够定位即可
     */
    private static final int TOOL_TAIL_LINES = 20;

    /**
     * 解包命令超时（秒）：几十 MB 的 tar 在慢盘上也不会超过这个量级
     */
    private static final long TOOL_TIMEOUT_SECONDS = 180L;

    /**
     * Windows 内置包候选文件名
     */
    private static final List<String> BUNDLE_WINDOWS = List.of("tshark-package.zip");

    /**
     * Linux 内置包候选文件名：deb 优先，其次自打 tar
     */
    private static final List<String> BUNDLE_LINUX = List.of(
            "tshark-package.deb", "tshark-package.tar.xz", "tshark-package.tar.gz");

    /**
     * macOS 内置包候选文件名
     */
    private static final List<String> BUNDLE_DARWIN = List.of(
            "tshark-package.tar.gz", "tshark-package.tar.xz");

    /**
     * 系统常见安装路径（只做存在性判断，命中就不下载）。
     */
    private static final List<String> SYSTEM_CANDIDATES = List.of(
            "C:\\Program Files\\Wireshark\\tshark.exe",
            "C:\\Program Files (x86)\\Wireshark\\tshark.exe",
            "/usr/bin/tshark",
            "/usr/local/bin/tshark",
            "/opt/wireshark/bin/tshark",
            "/opt/homebrew/bin/tshark");

    private final TsharkInstallProperties properties;

    private final TsharkInstallStreamRegistry streamRegistry;

    /**
     * 安装互斥：同一时刻只允许一条安装线程。
     */
    private final AtomicBoolean installing = new AtomicBoolean(false);

    /**
     * 最近一次探测到的可执行文件；null 表示尚未探测或探测落空。
     */
    private volatile String resolvedBin;

    /**
     * 探测到的版本首行。
     */
    private volatile String resolvedVersion = "";

    /**
     * 可执行文件来源：env / property / installed / system / path。
     */
    private volatile String resolvedSource = "";

    /**
     * 是否存在可用的抓包接口。
     */
    private volatile boolean captureCapable;

    /**
     * 当前状态。
     */
    private volatile TsharkRuntimeStatus status =
            TsharkRuntimeStatus.of(TsharkInstallState.CHECKING, "正在探测 tshark");

    /**
     * 构造器。
     *
     * @param properties     安装配置
     * @param streamRegistry 进度推送注册表
     */
    public TsharkRuntime(TsharkInstallProperties properties, TsharkInstallStreamRegistry streamRegistry) {
        this.properties = properties;
        this.streamRegistry = streamRegistry;
    }

    /**
     * 服务就绪后异步探测 / 补齐一次。
     *
     * <p>放在 {@code ApplicationReadyEvent} 而不是 {@code @PostConstruct}：
     * 后者在 Bean 初始化阶段执行，此时配置中心、数据源、任务调度都可能还没就绪，
     * 一旦下载卡住会把整个启动流程一起拖住。</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        ensureAsync();
    }

    /**
     * 状态快照。
     *
     * @return 当前状态
     */
    public TsharkRuntimeStatus status() {
        return status;
    }

    /**
     * 当前应使用的可执行文件。
     *
     * <p>尚未探测到结果时返回 {@code tshark}，交给系统按 PATH 解析 ——
     * 与改造前行为一致，避免「探测还没跑完就抓包」直接失败。</p>
     *
     * @return 可执行文件路径或命令名
     */
    public String bin() {
        String bin = resolvedBin;
        return bin == null || bin.isBlank() ? PATH_BIN : bin;
    }

    /**
     * tshark 是否可用（可解析 / 导入 pcap）。
     *
     * @return true 表示探测通过
     */
    public boolean isReady() {
        return status.ready();
    }

    /**
     * 触发一次异步补齐（幂等）。
     *
     * <p>已经在跑、或已经可用时直接返回；页面上的「重试」也走这里，
     * 因此失败后不需要重启服务。</p>
     */
    public void ensureAsync() {
        if (status.ready()) {
            return;
        }
        if (!installing.compareAndSet(false, true)) {
            return;
        }
        Thread.ofVirtual().name("tshark-install").start(() -> {
            try {
                doEnsure();
            } catch (Exception e) {
                log.warn("[tshark] 自动补齐失败", e);
                publish(TsharkInstallState.FAILED, 0, "自动补齐失败：" + e.getMessage());
            } finally {
                installing.set(false);
            }
        });
    }

    /**
     * 探测 + 按需安装的主流程，运行在虚拟线程上。
     */
    private void doEnsure() {
        publish(TsharkInstallState.CHECKING, 0, "正在探测本机 tshark");
        boolean found = resolveBinary();
        if (found) {
            publishReady("已找到本机 tshark");
            return;
        }
        if (!properties.isAutoInstall()) {
            publish(TsharkInstallState.MISSING, 0,
                    "未找到 tshark，且已关闭自动安装（plugin.monitor.tshark.auto-install=false）");
            return;
        }
        Path targetDir = installDir();
        try {
            Files.createDirectories(targetDir);
            Path pkg = resolvePackage();
            if (pkg == null) {
                // resolvePackage 已在下载失败时发布过 FAILED，这里不能再覆盖成 MISSING，
                // 否则「网络不通」会被显示成「没配安装包」，运维会照着错误的提示去改配置
                if (status.state() == TsharkInstallState.FAILED) {
                    return;
                }
                publish(TsharkInstallState.MISSING, 0,
                        "未找到 tshark，也没有可用的安装包：请配置 plugin.monitor.tshark.package-url "
                                + "（或按平台配置 package-url-windows / package-url-linux），"
                                + "也可以把安装包放到 package-path");
                return;
            }
            if (!pkg.equals(downloadTarget())) {
                // 本地包：不占下载区间，直接进解压
                publish(TsharkInstallState.EXTRACTING, DOWNLOAD_WEIGHT, "正在解压便携包");
            }
            extract(pkg, targetDir);
            Path bin = findExecutable(targetDir);
            if (bin == null) {
                publish(TsharkInstallState.FAILED, 0,
                        "便携包里没有找到 " + EXE_NAME + "，请确认压缩的是带依赖的整包");
                return;
            }
            ensureExecutable(bin);
            String version = probeVersion(bin);
            if (version.isBlank()) {
                publish(TsharkInstallState.FAILED, 0,
                        "解压后无法执行 " + bin + "（常见原因：缺少同目录下的依赖 DLL/so，"
                                + "或 Linux 上没有可执行权限 / 缺少 libpcap）");
                return;
            }
            resolvedBin = bin.toString();
            resolvedVersion = version;
            resolvedSource = "installed";
            publishReady("已自动安装 tshark " + shortVersion(version));
        } catch (IOException e) {
            log.warn("[tshark] 解压或安装失败 dir={}", targetDir, e);
            publish(TsharkInstallState.FAILED, 0, "解压或安装失败：" + e.getMessage());
        }
    }

    /**
     * 按候选顺序探测可执行文件。
     *
     * @return true 表示探测到可用文件（已写入 {@link #resolvedBin}）
     */
    private boolean resolveBinary() {
        Optional<Candidate> env = candidate(System.getenv(ENV_TSHARK_BIN), "env");
        if (env.isPresent()) {
            return accept(env.get());
        }
        Optional<Candidate> configured = candidate(properties.getBin(), "property");
        if (configured.isPresent()) {
            return accept(configured.get());
        }
        Optional<Candidate> installed = candidate(installDir().resolve(EXE_NAME).toString(), "installed");
        if (installed.isPresent()) {
            return accept(installed.get());
        }
        for (String path : SYSTEM_CANDIDATES) {
            Optional<Candidate> system = candidate(path, "system");
            if (system.isPresent()) {
                return accept(system.get());
            }
        }
        // 最后交给 PATH：与改造前一致，装好 Wireshark 且加进 PATH 的机器直接命中
        String version = probeVersion(Paths.get(PATH_BIN));
        if (!version.isBlank()) {
            resolvedBin = PATH_BIN;
            resolvedVersion = version;
            resolvedSource = "path";
            captureCapable = probeCapture(PATH_BIN);
            return true;
        }
        return false;
    }

    /**
     * 校验候选路径并接受它。
     *
     * @param candidate 候选
     * @return true 表示版本探测通过
     */
    private boolean accept(Candidate candidate) {
        String version = probeVersion(Paths.get(candidate.path()));
        if (version.isBlank()) {
            log.debug("[tshark] 候选不可用 path={} source={}", candidate.path(), candidate.source());
            return false;
        }
        resolvedBin = candidate.path();
        resolvedVersion = version;
        resolvedSource = candidate.source();
        captureCapable = probeCapture(candidate.path());
        return true;
    }

    /**
     * 把路径包装成候选；空值或文件不存在时返回空。
     *
     * @param path   路径
     * @param source 来源标记
     * @return 候选
     */
    private Optional<Candidate> candidate(String path, String source) {
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }
        if (!Files.isRegularFile(Paths.get(path))) {
            return Optional.empty();
        }
        return Optional.of(new Candidate(path, source));
    }

    /**
     * 执行 {@code tshark --version} 取首行，失败返回空串。
     *
     * @param bin 可执行文件
     * @return 版本首行或空串
     */
    private String probeVersion(Path bin) {
        List<String> lines = runQuietly(List.of(bin.toString(), "--version"));
        return lines.isEmpty() ? "" : lines.get(0);
    }

    /**
     * 探测是否存在可抓包的接口。
     *
     * <p>{@code tshark -D} 在有抓包驱动时列出 {@code \Device\NPF_{GUID}} 这类设备；
     * 没有驱动（或权限不足）时输出为空或只有错误。用它判断比额外引入 Npcap 探测库更稳。</p>
     *
     * @param bin 可执行文件
     * @return true 表示至少有一个可用抓包接口
     */
    private boolean probeCapture(String bin) {
        List<String> lines = runQuietly(List.of(bin, "-D"));
        return lines.stream().anyMatch(line -> !line.isBlank() && !line.startsWith("tshark:"));
    }

    /**
     * 执行命令并返回输出行；任何异常都按「无输出」处理。
     *
     * @param command 命令
     * @return 输出行
     */
    private List<String> runQuietly(List<String> command) {
        List<String> lines = new ArrayList<>();
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null && lines.size() < 200) {
                    lines.add(line);
                }
            }
            if (!process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                log.debug("[tshark] 命令超时 command={}", command);
                return List.of();
            }
            return lines;
        } catch (Exception e) {
            log.debug("[tshark] 命令执行失败 command={} reason={}", command, e.getMessage());
            return List.of();
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    /**
     * 解析出可用的 zip：本地文件优先，其次按配置下载。
     *
     * @return zip 路径；没有可用的包返回 null（下载失败时已发布 FAILED）
     */
    private Path resolvePackage() {
        String local = platformPackagePath();
        if (local != null && !local.isBlank()) {
            Path path = Paths.get(local);
            if (Files.isRegularFile(path)) {
                return path;
            }
            log.warn("[tshark] package-path 不存在 path={}", local);
        }
        Path bundled = bundledPackage();
        if (bundled != null) {
            return bundled;
        }
        String url = platformPackageUrl();
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            return download(url);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("[tshark] 下载便携包失败 url={}", url, e);
            publish(TsharkInstallState.FAILED, 0, "下载安装包失败：" + e.getMessage());
            return null;
        }
    }

    /**
     * 下载便携包到安装目录。
     *
     * @param url 下载地址
     * @return 落盘后的 zip 路径
     * @throws IOException          网络或落盘失败
     * @throws InterruptedException 线程被中断
     */
    private Path download(String url) throws IOException, InterruptedException {
        Path dir = installDir();
        Files.createDirectories(dir);
        Path target = downloadTarget();
        publish(TsharkInstallState.DOWNLOADING, 0, "正在下载安装包");
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20L))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(Math.max(30, properties.getTimeoutSeconds())))
                .GET()
                .build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + response.statusCode());
        }
        long total = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        long downloaded = 0L;
        long lastPush = 0L;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream in = response.body();
             OutputStream out = Files.newOutputStream(target)) {
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
                downloaded += read;
                long now = System.currentTimeMillis();
                if (now - lastPush >= PROGRESS_INTERVAL_MS) {
                    int percent = total > 0 ? (int) Math.min(DOWNLOAD_WEIGHT, downloaded * DOWNLOAD_WEIGHT / total) : 0;
                    publish(TsharkInstallState.DOWNLOADING, percent, progressText(downloaded, total));
                    lastPush = now;
                }
            }
        }
        publish(TsharkInstallState.EXTRACTING, DOWNLOAD_WEIGHT, "下载完成，正在解压");
        return target;
    }

    /**
     * 下载进度文案。
     *
     * @param downloaded 已下载字节
     * @param total      总字节，未知为 -1
     * @return 文案
     */
    private String progressText(long downloaded, long total) {
        String done = humanSize(downloaded);
        return total > 0 ? "正在下载安装包 " + done + " / " + humanSize(total) : "正在下载安装包 " + done;
    }

    /**
     * 取内置便携包：先看 classpath，再看应用目录下的 {@code tshark/<os>-<arch>/}。
     *
     * <p>两种放法都支持：</p>
     * <ol>
     *   <li>把 resource jar 放进 classpath（引入依赖即可）；</li>
     *   <li>不加依赖，直接把 jar 里的 {@code tshark/} 解到应用工作目录 —— 部署时
     *       {@code unzip utils-support-resource-tshark-*.jar 'tshark/*' -d <app 目录>} 就行，
     *       不必把 41MB 塞进依赖树。</li>
     * </ol>
     *
     * <p>目录为空时返回 null，继续走 package-url。</p>
     *
     * @return 安装包路径；没有内置包返回 null
     */
    private Path bundledPackage() {
        String platform = platformTag();
        List<String> names = platform.startsWith("windows") ? BUNDLE_WINDOWS
                : platform.startsWith("darwin") ? BUNDLE_DARWIN : BUNDLE_LINUX;
        ClassLoader loader = TsharkRuntime.class.getClassLoader();
        for (String name : names) {
            String resource = "tshark/" + platform + "/" + name;
            Path target = installDir().resolve(name);
            try {
                if (Files.isRegularFile(target) && Files.size(target) > 0L) {
                    return target;
                }
                try (InputStream in = loader.getResourceAsStream(resource)) {
                    if (in == null) {
                        continue;
                    }
                    Files.createDirectories(target.getParent());
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
                log.info("[tshark] 使用内置便携包 {}", target);
                return target;
            } catch (IOException e) {
                log.warn("[tshark] 释放内置便携包失败 resource={} reason={}", resource, e.getMessage());
                return null;
            }
        }
        // 不加依赖的放法：把 jar 里的 tshark/ 解到应用工作目录，直接从磁盘取
        Path local = Paths.get("tshark", platform);
        for (String name : names) {
            Path candidate = local.resolve(name);
            if (Files.isRegularFile(candidate)) {
                Path absolute = candidate.toAbsolutePath().normalize();
                log.info("[tshark] 使用应用目录内的便携包 {}", absolute);
                return absolute;
            }
        }
        return null;
    }

    /**
     * 当前平台标识（与资源目录命名一致）。
     *
     * @return 形如 windows-x86_64 / linux-x86_64 / darwin-aarch64
     */
    private static String platformTag() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String name;
        if (os.contains("win")) {
            name = "windows";
        } else if (os.contains("mac")) {
            name = "darwin";
        } else {
            name = "linux";
        }
        boolean arm = arch.contains("aarch64") || arch.contains("arm64");
        return name + "-" + (arm ? "aarch64" : "x86_64");
    }

    /**
     * 取当前平台的本地包路径：平台专用 &gt; 通用。
     *
     * @return 配置值，未配置返回空串
     */
    private String platformPackagePath() {
        String platform = isWindows() ? properties.getPackagePathWindows() : properties.getPackagePathLinux();
        return firstNonBlank(platform, properties.getPackagePath());
    }

    /**
     * 取当前平台的下载地址：平台专用 &gt; 通用。
     *
     * @return 配置值，未配置返回空串
     */
    private String platformPackageUrl() {
        String platform = isWindows() ? properties.getPackageUrlWindows() : properties.getPackageUrlLinux();
        return firstNonBlank(platform, properties.getPackageUrl());
    }

    /**
     * 取第一个非空白值。
     *
     * @param first    首选值
     * @param fallback 兜底值
     * @return 值，均为空返回空串
     */
    private static String firstNonBlank(String first, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return fallback == null ? "" : fallback;
    }

    /**
     * 按格式解包：zip / tar(.gz/.xz) / deb。
     *
     * <p>为什么不再只认 zip：Wireshark 官方没有 Linux 便携 zip，发行版产物是
     * {@code .deb} 或 {@code .tar.xz}；运维自己打的内网包也多半是 tar。
     * tar 与 deb 的解包交给系统 {@code tar}/{@code ar}，不在 Java 里重写一遍归档格式。</p>
     *
     * @param pkg 安装包
     * @param dir 目标目录
     * @throws IOException 解包失败
     */
    private void extract(Path pkg, Path dir) throws IOException {
        PackageType type = detectType(pkg);
        log.info("[tshark] 安装包 type={} file={}", type, pkg.getFileName());
        switch (type) {
            case ZIP -> unzip(pkg, dir);
            case TAR -> runTool(List.of("tar", "-xf", pkg.toString(), "-C", dir.toString()), "解压安装包");
            case DEB -> extractDeb(pkg, dir);
            default -> throw new IOException("无法识别的安装包格式（支持 zip / tar.gz / tar.xz / deb）："
                    + pkg.getFileName());
        }
    }

    /**
     * 安装包格式。
     */
    private enum PackageType {
        /**
         * zip
         */
        ZIP,
        /**
         * tar / tar.gz / tar.xz
         */
        TAR,
        /**
         * Debian 包
         */
        DEB,
        /**
         * 未知
         */
        UNKNOWN
    }

    /**
     * 按魔数识别安装包格式（后缀只作兜底）。
     *
     * @param pkg 安装包
     * @return 格式
     * @throws IOException 读取失败
     */
    private static PackageType detectType(Path pkg) throws IOException {
        byte[] head = new byte[8];
        try (InputStream in = Files.newInputStream(pkg)) {
            int read = in.read(head);
            if (read >= 4 && head[0] == 'P' && head[1] == 'K') {
                return PackageType.ZIP;
            }
            if (read >= 2 && (head[0] & 0xff) == 0x1f && (head[1] & 0xff) == 0x8b) {
                return PackageType.TAR;
            }
            if (read >= 6 && (head[0] & 0xff) == 0xfd && head[1] == 0x37 && head[2] == 0x7a
                    && head[3] == 0x58 && head[4] == 0x5a) {
                return PackageType.TAR;
            }
            if (read >= 8 && head[0] == '!' && head[1] == '<' && head[2] == 'a' && head[3] == 'r'
                    && head[4] == 'c' && head[5] == 'h') {
                return PackageType.DEB;
            }
        }
        String name = pkg.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".zip")) {
            return PackageType.ZIP;
        }
        if (name.endsWith(".tar") || name.endsWith(".tar.gz") || name.endsWith(".tgz")
                || name.endsWith(".tar.xz") || name.endsWith(".txz")) {
            return PackageType.TAR;
        }
        if (name.endsWith(".deb")) {
            return PackageType.DEB;
        }
        return PackageType.UNKNOWN;
    }

    /**
     * 解 deb：取出 {@code data.tar.*} 再交给 tar。
     *
     * @param deb deb 包
     * @param dir 目标目录
     * @throws IOException 解包失败
     */
    private void extractDeb(Path deb, Path dir) throws IOException {
        List<String> members = runToolForLines(List.of("ar", "t", deb.toString()), "列出 deb 内容");
        String data = members.stream()
                .filter(line -> line.trim().startsWith("data.tar"))
                .map(String::trim)
                .findFirst()
                .orElseThrow(() -> new IOException("deb 里没有 data.tar* 成员（可能是控制包）"));
        Path inner = Files.createTempFile("tshark-deb-", ".tar");
        try {
            runToolToFile(List.of("ar", "p", deb.toString(), data), inner, "取出 " + data);
            runTool(List.of("tar", "-xf", inner.toString(), "-C", dir.toString()), "解压 " + data);
        } finally {
            Files.deleteIfExists(inner);
        }
    }

    /**
     * 执行外部命令，失败时带上输出尾部一并抛出。
     *
     * @param command 命令
     * @param action  动作名（用于报错）
     * @throws IOException 执行失败或超时
     */
    private void runTool(List<String> command, String action) throws IOException {
        Process process = startTool(command);
        List<String> tail = drain(process);
        waitTool(process, action, tail);
    }

    /**
     * 执行外部命令并收集输出行。
     *
     * @param command 命令
     * @param action  动作名
     * @return 输出行
     * @throws IOException 执行失败
     */
    private List<String> runToolForLines(List<String> command, String action) throws IOException {
        Process process = startTool(command);
        List<String> lines = drain(process);
        waitTool(process, action, lines);
        return lines;
    }

    /**
     * 执行外部命令并把标准输出写进文件。
     *
     * @param command 命令
     * @param target  输出文件
     * @param action  动作名
     * @throws IOException 执行失败
     */
    private void runToolToFile(List<String> command, Path target, String action) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        pb.redirectOutput(target.toFile());
        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new IOException(action + " 启动失败：" + e.getMessage()
                    + "（系统缺少命令 " + command.getFirst() + "）", e);
        }
        waitTool(process, action, List.of());
    }

    /**
     * 启动外部命令。
     *
     * @param command 命令
     * @return 进程
     * @throws IOException 启动失败
     */
    private Process startTool(List<String> command) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        try {
            return pb.start();
        } catch (IOException e) {
            throw new IOException("启动失败：" + e.getMessage()
                    + "（系统缺少命令 " + command.getFirst() + "）", e);
        }
    }

    /**
     * 收集进程输出（最多保留尾部若干行）。
     *
     * @param process 进程
     * @return 输出行
     */
    private static List<String> drain(Process process) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (lines.size() >= TOOL_TAIL_LINES) {
                    lines.remove(0);
                }
                lines.add(line);
            }
        } catch (IOException e) {
            log.debug("[tshark] 读取命令输出失败: {}", e.getMessage());
        }
        return lines;
    }

    /**
     * 等待进程结束并校验退出码。
     *
     * @param process 进程
     * @param action  动作名
     * @param tail    输出尾部
     * @throws IOException 超时或非 0 退出
     */
    private static void waitTool(Process process, String action, List<String> tail) throws IOException {
        try {
            if (!process.waitFor(TOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException(action + "超时（" + TOOL_TIMEOUT_SECONDS + "s）");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(action + "被中断", e);
        }
        if (process.exitValue() != 0) {
            throw new IOException(action + "失败（exit=" + process.exitValue() + "）："
                    + String.join(" | ", tail));
        }
    }

    /**
     * 给解出来的 tshark 补可执行位。
     *
     * <p>zip 不携带 Unix 权限位，{@code Files.copy} 落盘默认 644，Linux 上直接执行会
     * {@code Permission denied}。这里只补 x 位，不动其它权限；非 POSIX 平台跳过。</p>
     *
     * @param bin 可执行文件
     */
    private void ensureExecutable(Path bin) {
        if (isWindows() || bin == null) {
            return;
        }
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(bin);
            EnumSet<PosixFilePermission> next = EnumSet.copyOf(permissions);
            next.add(PosixFilePermission.OWNER_EXECUTE);
            next.add(PosixFilePermission.GROUP_EXECUTE);
            next.add(PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(bin, next);
        } catch (UnsupportedOperationException | IOException e) {
            log.debug("[tshark] 补可执行位跳过 bin={} reason={}", bin, e.getMessage());
        }
    }

    /**
     * 解压 zip 到目标目录（防目录穿越）。
     *
     * @param zip 压缩包
     * @param dir 目标目录
     * @throws IOException 解压失败
     */
    private void unzip(Path zip, Path dir) throws IOException {
        int count = 0;
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (++count > MAX_UNZIP_ENTRIES) {
                    throw new IOException("压缩包条目过多，已中止");
                }
                Path target = dir.resolve(entry.getName()).normalize();
                if (!target.startsWith(dir)) {
                    throw new IOException("压缩包包含非法路径：" + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    /**
     * 在解压目录里找可执行文件（便携包常带一层顶层目录）。
     *
     * @param dir 解压目录
     * @return 可执行文件；找不到返回 null
     */
    private Path findExecutable(Path dir) {
        Path direct = dir.resolve(EXE_NAME);
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        try (Stream<Path> stream = Files.walk(dir, MAX_SCAN_DEPTH)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> EXE_NAME.equalsIgnoreCase(p.getFileName().toString()))
                    .min(Comparator.comparingInt(p -> p.getNameCount()))
                    .orElse(null);
        } catch (IOException e) {
            log.debug("[tshark] 扫描解压目录失败 dir={} reason={}", dir, e.getMessage());
            return null;
        }
    }

    /**
     * 安装目录。优先级：环境变量 → 配置 → 用户主目录。
     *
     * @return 安装目录
     */
    private Path installDir() {
        String fromEnv = System.getenv(ENV_INSTALL_DIR);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return Paths.get(fromEnv);
        }
        String configured = properties.getInstallDir();
        if (configured != null && !configured.isBlank()) {
            return Paths.get(configured);
        }
        return Paths.get(System.getProperty("user.home"), "spring-support", "tshark");
    }

    /**
     * 下载落盘位置。
     *
     * @return 下载目标文件路径
     */
    private Path downloadTarget() {
        return installDir().resolve("tshark-package.zip");
    }

    /**
     * 发布就绪状态。
     *
     * @param message 文案
     */
    private void publishReady(String message) {
        // 只装上了 tshark、没有抓包驱动/权限时，别让「已就绪」误导用户：
        // 解析 pcap 能用，实时抓包不能用，这两件事必须在状态里说清楚。
        String text = captureCapable ? message : message + "（实时抓包不可用：" + captureHint() + "）";
        TsharkRuntimeStatus next = new TsharkRuntimeStatus(TsharkInstallState.READY, 100, text,
                true, captureCapable, resolvedBin == null ? "" : resolvedBin,
                resolvedVersion, resolvedSource, System.currentTimeMillis());
        status = next;
        log.info("[tshark] {} bin={} captureReady={}", text, next.binPath(), next.captureReady());
        streamRegistry.broadcast(next);
    }

    /**
     * 实时抓包不可用时的原因提示。
     *
     * <p>Windows 缺 Npcap，Linux 缺 libpcap 或没有 CAP_NET_RAW/CAP_NET_ADMIN ——
     * 后者在容器里尤其常见，提示要能直接指向处理办法。</p>
     *
     * @return 提示文案
     */
    private static String captureHint() {
        return isWindows()
                ? "未检测到 Npcap 抓包驱动，请安装 Npcap 后重试"
                : "需要 libpcap，且进程要有 CAP_NET_RAW/CAP_NET_ADMIN 权限（或以 root 运行）";
    }

    /**
     * 发布一个非就绪状态。
     *
     * @param state    状态
     * @param progress 进度
     * @param message  文案
     */
    private void publish(TsharkInstallState state, int progress, String message) {
        TsharkRuntimeStatus next = new TsharkRuntimeStatus(state, clamp(progress), message, false,
                captureCapable, resolvedBin == null ? "" : resolvedBin, resolvedVersion, resolvedSource,
                System.currentTimeMillis());
        status = next;
        log.info("[tshark] state={} progress={} message={}", state, next.progress(), message);
        streamRegistry.broadcast(next);
    }

    /**
     * 进度夹紧到 0-100。
     *
     * @param value 原始值
     * @return 合法进度
     */
    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    /**
     * 版本首行裁短，日志与页面都用它。
     *
     * @param version 版本首行
     * @return 形如 4.4.1
     */
    private static String shortVersion(String version) {
        if (version == null || version.isBlank()) {
            return "";
        }
        String[] parts = version.trim().split("\\s+");
        return parts.length > 1 ? parts[1] : parts[0];
    }

    /**
     * 字节数转可读大小。
     *
     * @param bytes 字节数
     * @return 形如 12.3 MB
     */
    private static String humanSize(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024.0) {
            return String.format(Locale.ROOT, "%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        return mb < 1024.0 ? String.format(Locale.ROOT, "%.1f MB", mb) : String.format(Locale.ROOT, "%.2f GB", mb / 1024.0);
    }

    /**
     * 是否 Windows。
     *
     * @return true 表示 Windows
     */
    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /**
     * 候选可执行文件。
     *
     * @param path   路径
     * @param source 来源标记
     */
    private record Candidate(String path, String source) {
    }
}
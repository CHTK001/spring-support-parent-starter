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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
                                + "或把便携包放到 plugin.monitor.tshark.package-path");
                return;
            }
            if (!pkg.equals(downloadTarget())) {
                // 本地包：不占下载区间，直接进解压
                publish(TsharkInstallState.EXTRACTING, DOWNLOAD_WEIGHT, "正在解压便携包");
            }
            unzip(pkg, targetDir);
            Path bin = findExecutable(targetDir);
            if (bin == null) {
                publish(TsharkInstallState.FAILED, 0,
                        "便携包里没有找到 " + EXE_NAME + "，请确认压缩的是带依赖的整包");
                return;
            }
            String version = probeVersion(bin);
            if (version.isBlank()) {
                publish(TsharkInstallState.FAILED, 0,
                        "解压后无法执行 " + bin + "（常见原因：缺少同目录下的依赖 DLL）");
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
        String local = properties.getPackagePath();
        if (local != null && !local.isBlank()) {
            Path path = Paths.get(local);
            if (Files.isRegularFile(path)) {
                return path;
            }
            log.warn("[tshark] package-path 不存在 path={}", local);
        }
        String url = properties.getPackageUrl();
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
        TsharkRuntimeStatus next = new TsharkRuntimeStatus(TsharkInstallState.READY, 100, message,
                true, captureCapable, resolvedBin == null ? "" : resolvedBin,
                resolvedVersion, resolvedSource, System.currentTimeMillis());
        status = next;
        log.info("[tshark] {} bin={} captureReady={}", message, next.binPath(), next.captureReady());
        streamRegistry.broadcast(next);
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
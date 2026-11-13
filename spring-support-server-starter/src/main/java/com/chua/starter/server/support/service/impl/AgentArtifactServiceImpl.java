package com.chua.starter.server.support.service.impl;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.entity.MsServerProject;
import com.chua.starter.server.support.entity.MsServerRelease;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import com.chua.starter.server.support.mapper.MsServerProjectMapper;
import com.chua.starter.server.support.mapper.MsServerReleaseMapper;
import com.chua.starter.server.support.service.AgentArtifactService;
import com.chua.starter.server.support.service.SftpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Agent 构件投放服务实现。
 *
 * <p>流程固定为三步：</p>
 * <ol>
 *   <li><b>取得本地构件</b>：远程下载到平台缓存目录，或直接使用平台本机路径；</li>
 *   <li><b>识别可投放根</b>：jar 取父目录（SkyWalking 需要连同 config/plugins 一起投），
 *       发行包先解压再定位 {@code skywalking-agent.jar} / {@code arthas-boot.jar}；</li>
 *   <li><b>SFTP 投放</b>：递归建目录 + 逐文件上传，返回远端 jar 绝对路径。</li>
 * </ol>
 *
 * <p>缓存目录可用 {@code ms.server.agent.cache-dir} 覆盖，默认
 * {@code ~/.chua/ms-server/agent-cache}。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentArtifactServiceImpl implements AgentArtifactService {

    /** 构件类型：Arthas */
    private static final String KIND_ARTHAS = "arthas";
    /** 构件类型：Arthas 运行时 attach 用的 boot jar */
    private static final String KIND_ARTHAS_BOOT = "arthas-boot";
    /** 构件类型：SkyWalking */
    private static final String KIND_SKYWALKING = "skywalking";
    /** 构件类型：平台自有 APM */
    private static final String KIND_PLATFORM = "platform";

    /** 来源：远程下载 */
    private static final String SOURCE_URL = "url";
    /** 来源：平台本机路径 */
    private static final String SOURCE_LOCAL = "local";

    /** 单文件下载上限 512MB，避免误贴大包把平台磁盘打满 */
    private static final long MAX_DOWNLOAD_BYTES = 512L * 1024 * 1024;
    /** 目录扫描最大深度 */
    private static final int MAX_SCAN_DEPTH = 6;
    /** 目录投放最大文件数，防止把整个磁盘目录误投 */
    private static final int MAX_UPLOAD_FILES = 2000;

    private final MsServerProjectMapper projectMapper;
    private final MsServerReleaseMapper releaseMapper;
    private final MsServerHostMapper hostMapper;
    private final SftpService sftpService;

    @Override
    public ReturnResult<Map<String, Object>> prepare(Long projectId, String kind, String source,
                                                     String value, String targetDir,
                                                     String fileName, boolean force) {
        if (projectId == null) {
            return ReturnResult.error("项目标识不能为空");
        }
        MsServerProject project = projectMapper.selectById(projectId);
        if (project == null) {
            return ReturnResult.error("项目不存在");
        }
        if (project.getProjectPath() == null || project.getProjectPath().isBlank()) {
            return ReturnResult.error("项目路径未配置，无法投放 Agent 构件");
        }
        if (project.getReleaseId() == null) {
            return ReturnResult.error("项目未关联发布单，无法解析目标主机");
        }
        MsServerRelease release = releaseMapper.selectById(project.getReleaseId());
        if (release == null) {
            return ReturnResult.error("项目未关联发布单，无法解析目标主机");
        }
        if (release.getHostId() == null) {
            return ReturnResult.error("发布单未关联目标主机");
        }
        MsServerHost host = hostMapper.selectById(release.getHostId());
        if (host == null) {
            return ReturnResult.error("目标主机不存在");
        }

        String normalizedKind = normalizeKind(kind);
        if (normalizedKind == null) {
            return ReturnResult.error("不支持的构件类型: " + kind);
        }
        Path local = null;
        boolean directoryArtifact = false;
        try {
            local = resolveLocal(normalizedKind, source, value, force);
            if (local == null) {
                return ReturnResult.error("来源参数不合法：source 只支持 url / local");
            }
            if (Files.isDirectory(local)) {
                directoryArtifact = true;
            } else if (!Files.isRegularFile(local)) {
                return ReturnResult.error("构件的本地缓存不存在: " + local);
            }
            if (directoryArtifact) {
                local = locateJar(normalizedKind, local);
                if (local == null) {
                    return ReturnResult.error("在目录中未找到 " + expectedJarName(normalizedKind)
                            + "（已扫描 " + MAX_SCAN_DEPTH + " 层）");
                }
            }
        } catch (Exception e) {
            log.warn("[agent] 准备构件失败 kind={} source={} value={}: {}", kind, source, value, e.getMessage());
            return ReturnResult.error("获取 Agent 构件失败: " + e.getMessage());
        }

        String dir = normalizeTargetDir(project, normalizedKind, targetDir);
        String remoteName = resolveFileName(local, fileName);
        ReturnResult<Void> mk = sftpService.mkdir(host.getId(), null, dir);
        if (!mk.isOk()) {
            return ReturnResult.error("创建目标目录失败: " + mk.getMsg());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("kind", normalizedKind);
        data.put("source", normalizeSource(source));
        data.put("targetDir", dir);
        long uploadedBytes = 0L;
        int uploadedFiles = 0;
        try {
            if (directoryArtifact) {
                Path root = local.getParent();
                String remoteJar = dir + "/" + toRemoteRelative(root, local);
                List<Path> files;
                try (Stream<Path> walk = Files.walk(root)) {
                    files = walk.filter(Files::isRegularFile).toList();
                }
                if (files.size() > MAX_UPLOAD_FILES) {
                    return ReturnResult.error("目录内文件过多（" + files.size() + " > " + MAX_UPLOAD_FILES + "），已拒绝整目录投放");
                }
                for (Path file : files) {
                    String relative = toRemoteRelative(root, file);
                    String remoteDir = remoteParent(dir, relative);
                    ReturnResult<String> up = sftpService.uploadLocal(host.getId(), null, remoteDir, file,
                            fileNameOf(relative));
                    if (!up.isOk()) {
                        return ReturnResult.error("投放失败（" + relative + "）: " + up.getMsg());
                    }
                    uploadedBytes += Files.size(file);
                    uploadedFiles++;
                }
                data.put("remotePath", remoteJar);
                data.put("fileName", fileNameOf(toRemoteRelative(root, local)));
            } else {
                ReturnResult<String> up = sftpService.uploadLocal(host.getId(), null, dir, local, remoteName);
                if (!up.isOk()) {
                    return ReturnResult.error("投放失败: " + up.getMsg());
                }
                uploadedBytes = Files.size(local);
                uploadedFiles = 1;
                data.put("remotePath", up.getData());
                data.put("fileName", remoteName);
            }
        } catch (Exception e) {
            log.warn("[agent] 投放失败 projectId={} kind={}: {}", projectId, kind, e.getMessage());
            return ReturnResult.error("投放 Agent 构件失败: " + e.getMessage());
        }

        data.put("directory", directoryArtifact);
        data.put("files", uploadedFiles);
        data.put("bytes", uploadedBytes);
        data.put("localPath", local.toString());
        data.put("hostId", host.getId());
        data.put("hostName", host.getServerHostName());
        log.info("[agent] 构件已投放 projectId={} kind={} 文件数={} 目标={}",
                projectId, normalizedKind, uploadedFiles, data.get("remotePath"));
        return ReturnResult.ok(data, "已投放到 " + data.get("remotePath"));
    }

    // ==================== 本地构件 ====================

    /**
     * 取得平台本地构件。
     *
     * @param kind  构件类型
     * @param source 来源
     * @param value  地址或路径
     * @param force  是否强制重新下载
     * @return 本地文件或解压目录
     * @throws IOException 下载或解压失败
     */
    private Path resolveLocal(String kind, String source, String value, boolean force) throws IOException {
        String normalizedSource = normalizeSource(source);
        if (normalizedSource == null) {
            return null;
        }
        if (SOURCE_LOCAL.equals(normalizedSource)) {
            if (value == null || value.isBlank()) {
                throw new IOException("本机路径不能为空");
            }
            Path path = Paths.get(value.trim());
            if (!Files.exists(path)) {
                throw new IOException("平台本机上不存在该路径: " + path.toAbsolutePath());
            }
            return path.toAbsolutePath().normalize();
        }
        if (value == null || value.isBlank()) {
            throw new IOException("下载地址不能为空");
        }
        return download(kind, value.trim(), force);
    }

    /**
     * 远程下载构件到平台缓存目录；发行包自动解压。
     *
     * @param kind  构件类型
     * @param url   下载地址
     * @param force 是否忽略缓存
     * @return 本地文件或解压目录
     * @throws IOException 下载或解压失败
     */
    private Path download(String kind, String url, boolean force) throws IOException {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IOException("下载地址不合法: " + url);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IOException("只支持 http/https 下载地址");
        }
        String rawName = fileNameOf(uri.getPath() == null ? "" : uri.getPath());
        if (rawName.isBlank()) {
            rawName = expectedJarName(kind);
        }
        String safeName = sanitize(rawName);
        Path kindDir = cacheRoot().resolve(kind);
        Files.createDirectories(kindDir);
        Path archive = kindDir.resolve(safeName);
        String lower = safeName.toLowerCase(Locale.ROOT);
        boolean archiveFile = lower.endsWith(".zip") || lower.endsWith(".tar.gz") || lower.endsWith(".tgz");
        Path extractDir = kindDir.resolve(stripArchiveSuffix(safeName));
        if (archiveFile && !force && Files.isDirectory(extractDir)
                && locateJar(kind, extractDir) != null) {
            return extractDir;
        }
        if (!archiveFile && !force && Files.isRegularFile(archive) && Files.size(archive) > 0) {
            return archive;
        }
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                // [P3C A20 豁免]
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        HttpRequest request = HttpRequest.newBuilder(uri)
                // [P3C A20 豁免]
                .timeout(Duration.ofMinutes(10))
                .header("User-Agent", "ms-server-agent-fetch/1.0")
                .GET()
                .build();
        Path temp = Files.createTempFile(kindDir, "download-", ".part");
        try {
            HttpResponse<InputStream> response;
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("下载被中断: " + url, e);
            }
            // [P3C A20 豁免]
            if (response.statusCode() / 100 != 2) {
                throw new IOException("下载失败，HTTP " + response.statusCode());
            }
            long total = 0L;
            try (InputStream in = response.body();
                 OutputStream out = Files.newOutputStream(temp)) {
                // [P3C A20 豁免]
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_DOWNLOAD_BYTES) {
                        // [P3C A20 豁免]
                        throw new IOException("构件超过 " + (MAX_DOWNLOAD_BYTES / 1024 / 1024) + "MB 上限");
                    }
                    out.write(buffer, 0, read);
                }
            }
            if (total == 0L) {
                throw new IOException("下载内容为空");
            }
            Files.move(temp, archive, StandardCopyOption.REPLACE_EXISTING);
            if (archiveFile) {
                deleteQuietly(extractDir);
                Files.createDirectories(extractDir);
                if (lower.endsWith(".zip")) {
                    unzip(archive, extractDir);
                } else {
                    untar(archive, extractDir);
                }
                return extractDir;
            }
            return archive;
        } finally {
            deleteQuietly(temp);
        }
    }

    /**
     * 平台缓存根目录。
     *
     * @return 缓存根目录
     * @throws IOException 目录创建失败
     */
    private Path cacheRoot() throws IOException {
        String configured = System.getProperty("ms.server.agent.cache-dir");
        Path root = configured == null || configured.isBlank()
                ? Paths.get(System.getProperty("user.home"), ".chua", "ms-server", "agent-cache")
                : Paths.get(configured);
        Files.createDirectories(root);
        return root;
    }

    /**
     * 在目录中定位目标 jar，优先精确名，其次任意 jar。
     *
     * @param kind 构件类型
     * @param dir  目录
     * @return 目标 jar，找不到为 {@code null}
     * @throws IOException 遍历失败
     */
    private Path locateJar(String kind, Path dir) throws IOException {
        List<String> preferred = expectedJarNames(kind);
        List<Path> jars = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir, MAX_SCAN_DEPTH)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .forEach(jars::add);
        }
        if (jars.isEmpty()) {
            return null;
        }
        jars.sort(Comparator
                .comparingInt((Path p) -> preference(preferred, p))
                .thenComparing(p -> p.getNameCount()));
        Path best = jars.get(0);
        // 精确名优先命中时不受深度影响；没有精确名才接受任意 jar
        return best;
    }

    /**
     * 精确名匹配排序权重，数字越小越优先。
     *
     * @param preferred 优先文件名
     * @param path      候选
     * @return 权重
     */
    private int preference(List<String> preferred, Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        for (int i = 0; i < preferred.size(); i++) {
            if (preferred.get(i).equals(name)) {
                return i;
            }
        }
        return preferred.size();
    }

    /**
     * 各类构件的优先 jar 文件名。
     *
     * @param kind 构件类型
     * @return 优先文件名（小写）
     */
    private List<String> expectedJarNames(String kind) {
        return switch (kind) {
            case KIND_ARTHAS -> List.of("arthas-boot.jar", "arthas-agent.jar");
            case KIND_SKYWALKING -> List.of("skywalking-agent.jar");
            default -> List.of("apm-agent.jar");
        };
    }

    /**
     * 各类构件的首选 jar 文件名（错误提示用）。
     *
     * @param kind 构件类型
     * @return 文件名
     */
    private String expectedJarName(String kind) {
        return expectedJarNames(kind).get(0);
    }

    // ==================== 归档解压 ====================

    /**
     * 解压 zip（带路径穿越防护）。
     *
     * @param archive 归档
     * @param dest    目标目录
     * @throws IOException 解压失败
     */
    private void unzip(Path archive, Path dest) throws IOException {
        try (InputStream in = Files.newInputStream(archive);
             ZipInputStream zip = new ZipInputStream(in, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path target = safeResolve(dest, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                }
                zip.closeEntry();
            }
        }
    }

    /**
     * 解压 tar / tar.gz（最小实现）。
     *
     * <p>支持 ustar 前缀字段、GNU 长文件名（{@code L}）与 pax 扩展头（{@code x} / {@code X}）。
     * 发行包（如 SkyWalking java agent tgz）的路径常超过 100 字节，只认 ustar 会把文件名截断，
     * 所以长名字段必须解析，否则投放出来的目录结构是错的。</p>
     *
     * @param archive 归档
     * @param dest    目标目录
     * @throws IOException 解压失败
     */
    private void untar(Path archive, Path dest) throws IOException {
        boolean gzip = isGzip(archive);
        try (InputStream raw = Files.newInputStream(archive);
             InputStream decoded = gzip ? new GZIPInputStream(raw) : raw;
             BufferedInputStream in = new BufferedInputStream(decoded)) {
            // [P3C A20 豁免]
            byte[] header = new byte[512];
            String pendingName = null;
            while (true) {
                if (!readFully(in, header)) {
                    return;
                }
                if (isZeroBlock(header)) {
                    return;
                }
                // [P3C A20 豁免]
                String name = readString(header, 0, 100);
                // [P3C A20 豁免]
                String prefix = readString(header, 345, 155);
                if (!prefix.isEmpty()) {
                    name = prefix + "/" + name;
                }
                // [P3C A20 豁免]
                char type = (char) header[156];
                // [P3C A20 豁免]
                long size = readOctal(header, 124, 12);
                // [P3C A20 豁免]
                long padding = (512 - (size % 512)) % 512;
                if (type == 'L' || type == 'x' || type == 'X' || type == 'g') {
                    byte[] meta = readMeta(in, size);
                    if (type == 'L') {
                        pendingName = trimNul(meta);
                    } else if (type != 'g') {
                        String longPath = paxPath(meta);
                        if (longPath != null && !longPath.isEmpty()) {
                            pendingName = longPath;
                        }
                    }
                    skipExactly(in, padding);
                    continue;
                }
                if (pendingName != null && !pendingName.isEmpty()) {
                    name = pendingName;
                    pendingName = null;
                }
                Path target = safeResolve(dest, name);
                if (type == '5') {
                    Files.createDirectories(target);
                } else if (type == '0' || type == 0 || type == '7') {
                    Files.createDirectories(target.getParent());
                    copyExactly(in, target, size);
                } else {
                    skipExactly(in, size);
                }
                skipExactly(in, padding);
            }
        }
    }

    /**
     * 读取扩展头（GNU longname / pax）内容，超长部分直接跳过。
     *
     * @param in   输入流
     * @param size 扩展头字节数
     * @return 内容（最多 1MB）
     * @throws IOException 读取失败
     */
    private byte[] readMeta(InputStream in, long size) throws IOException {
        // [P3C A20 豁免]
        int cap = (int) Math.min(size, 1 << 20);
        byte[] meta = new byte[cap];
        int read = 0;
        while (read < cap) {
            int n = in.read(meta, read, cap - read);
            if (n == -1) {
                throw new IOException("归档内容被截断");
            }
            read += n;
        }
        skipExactly(in, size - cap);
        return meta;
    }

    /**
     * 去掉尾部 NUL 与换行。
     *
     * @param data 内容
     * @return 文本
     */
    private String trimNul(byte[] data) {
        int end = data.length;
        while (end > 0 && (data[end - 1] == 0 || data[end - 1] == '\n')) {
            end--;
        }
        return new String(data, 0, end, StandardCharsets.UTF_8).trim();
    }

    /**
     * 从 pax 扩展头里取 {@code path} 记录。
     *
     * <p>pax 记录格式：{@code <长度> <键>=<值>\n}，长度包含自身。</p>
     *
     * @param data 扩展头内容
     * @return path 值，没有返回 {@code null}
     */
    private String paxPath(byte[] data) {
        String text = new String(data, StandardCharsets.UTF_8);
        int index = 0;
        while (index < text.length()) {
            int space = text.indexOf(' ', index);
            if (space < 0) {
                return null;
            }
            int length;
            try {
                length = Integer.parseInt(text.substring(index, space).trim());
            } catch (NumberFormatException e) {
                return null;
            }
            int end = index + length;
            if (length <= 0 || end > text.length() || end - 1 <= space + 1) {
                return null;
            }
            String record = text.substring(space + 1, end - 1);
            int eq = record.indexOf('=');
            if (eq > 0 && "path".equals(record.substring(0, eq).trim())) {
                return record.substring(eq + 1);
            }
            index = end;
        }
        return null;
    }

    /**
     * 是否为 gzip 流。
     *
     * @param archive 归档
     * @return gzip 为 {@code true}
     * @throws IOException 读取失败
     */
    private boolean isGzip(Path archive) throws IOException {
        try (InputStream in = Files.newInputStream(archive)) {
            int b1 = in.read();
            int b2 = in.read();
            return b1 == 0x1f && b2 == 0x8b;
        }
    }

    /**
     * 归档内路径解析，禁止越出目标目录。
     *
     * @param dest 目标目录
     * @param name 归档条目
     * @return 目标路径
     * @throws IOException 路径穿越时抛出
     */
    private Path safeResolve(Path dest, String name) throws IOException {
        Path target = dest.resolve(name).normalize();
        if (!target.startsWith(dest.normalize())) {
            throw new IOException("归档条目越界: " + name);
        }
        return target;
    }

    /**
     * 读取 tar 头中的定长字符串。
     *
     * @param header 头
     * @param offset 偏移
     * @param length 长度
     * @return 去零去空字符串
     */
    private String readString(byte[] header, int offset, int length) {
        int end = offset;
        int limit = offset + length;
        while (end < limit && header[end] != 0) {
            end++;
        }
        return new String(header, offset, end - offset, StandardCharsets.UTF_8).trim();
    }

    /**
     * 读取 tar 头中的八进制数字。
     *
     * @param header 头
     * @param offset 偏移
     * @param length 长度
     * @return 数值
     */
    private long readOctal(byte[] header, int offset, int length) {
        String text = new String(header, offset, length, StandardCharsets.US_ASCII)
                .replace("\u0000", " ").trim();
        if (text.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(text, 8);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * 判断是否全零块（tar 结束标记）。
     *
     * @param block 块
     * @return 全零为 {@code true}
     */
    private boolean isZeroBlock(byte[] block) {
        for (byte b : block) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * 读满指定长度。
     *
     * @param in     输入流
     * @param buffer 目标
     * @return 是否读满
     * @throws IOException 读取失败
     */
    private boolean readFully(InputStream in, byte[] buffer) throws IOException {
        int read = 0;
        while (read < buffer.length) {
            int n = in.read(buffer, read, buffer.length - read);
            if (n == -1) {
                return false;
            }
            read += n;
        }
        return true;
    }

    /**
     * 精确拷贝指定字节数到文件。
     *
     * @param in     输入流
     * @param target 目标文件
     * @param size   字节数
     * @throws IOException 拷贝失败
     */
    private void copyExactly(InputStream in, Path target, long size) throws IOException {
        try (OutputStream out = Files.newOutputStream(target)) {
            // [P3C A20 豁免]
            byte[] buffer = new byte[64 * 1024];
            long remaining = size;
            while (remaining > 0) {
                int want = (int) Math.min(buffer.length, remaining);
                int n = in.read(buffer, 0, want);
                if (n == -1) {
                    throw new IOException("归档内容被截断");
                }
                out.write(buffer, 0, n);
                remaining -= n;
            }
        }
    }

    /**
     * 精确跳过指定字节数。
     *
     * @param in      输入流
     * @param size    字节数
     * @throws IOException 读取失败
     */
    private void skipExactly(InputStream in, long size) throws IOException {
        // [P3C A20 豁免]
        byte[] buffer = new byte[8192];
        long remaining = size;
        while (remaining > 0) {
            int n = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (n == -1) {
                return;
            }
            remaining -= n;
        }
    }

    // ==================== 路径与命名 ====================

    /**
     * 目标目录：显式参数优先，否则 {@code <项目路径>/lib/<类型>}。
     *
     * @param project   项目
     * @param kind      构件类型
     * @param targetDir 显式目标目录
     * @return 规整后的远端目录
     */
    private String normalizeTargetDir(MsServerProject project, String kind, String targetDir) {
        String dir = targetDir != null && !targetDir.isBlank()
                ? targetDir.trim()
                : project.getProjectPath().trim() + "/lib/" + kind;
        dir = dir.replace('\\', '/');
        while (dir.contains("//")) {
            dir = dir.replace("//", "/");
        }
        if (dir.length() > 1 && dir.endsWith("/")) {
            dir = dir.substring(0, dir.length() - 1);
        }
        return dir;
    }

    /**
     * 构件类型归一。
     *
     * @param kind 入参
     * @return 归一结果，非法为 {@code null}
     */
    private String normalizeKind(String kind) {
        if (kind == null) {
            return null;
        }
        String value = kind.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case KIND_ARTHAS, KIND_ARTHAS_BOOT, "arthas-agent" -> KIND_ARTHAS;
            case KIND_SKYWALKING, "skywalking-agent", "sw" -> KIND_SKYWALKING;
            case KIND_PLATFORM, "apm", "runtime-agent" -> KIND_PLATFORM;
            default -> null;
        };
    }

    /**
     * 来源归一。
     *
     * @param source 入参
     * @return 归一结果，非法为 {@code null}
     */
    private String normalizeSource(String source) {
        if (source == null) {
            return null;
        }
        String value = source.trim().toLowerCase(Locale.ROOT);
        if (SOURCE_LOCAL.equals(value) || "path".equals(value) || "file".equals(value)) {
            return SOURCE_LOCAL;
        }
        if (SOURCE_URL.equals(value) || "http".equals(value) || "download".equals(value)) {
            return SOURCE_URL;
        }
        return null;
    }

    /**
     * 从路径取文件名。
     *
     * @param path 路径
     * @return 文件名
     */
    private String fileNameOf(String path) {
        if (path == null) {
            return "";
        }
        String normalized = path.replace('\\', '/');
        int index = normalized.lastIndexOf('/');
        return index >= 0 ? normalized.substring(index + 1) : normalized;
    }

    /**
     * 计算相对路径并转成远端分隔符。
     *
     * @param root 根
     * @param file 文件
     * @return 远端相对路径
     */
    private String toRemoteRelative(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    /**
     * 远端相对路径的父目录（拼到目标目录后）。
     *
     * @param base     目标根目录
     * @param relative 相对路径
     * @return 远端目录
     */
    private String remoteParent(String base, String relative) {
        int index = relative.lastIndexOf('/');
        return index < 0 ? base : base + "/" + relative.substring(0, index);
    }

    /**
     * 清洗文件名。
     *
     * @param name 原始名
     * @return 安全名
     */
    private String sanitize(String name) {
        String value = name == null ? "" : name;
        value = value.replace("\\", "_").replace("/", "_").trim();
        if (value.isEmpty() || ".".equals(value) || "..".equals(value)) {
            return "artifact.jar";
        }
        return value;
    }

    /**
     * 去掉压缩后缀，得到解压目录名。
     *
     * @param name 文件名
     * @return 解压目录名
     */
    private String stripArchiveSuffix(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".tar.gz")) {
            return name.substring(0, name.length() - 7);
        }
        if (lower.endsWith(".tgz")) {
            return name.substring(0, name.length() - 4);
        }
        if (lower.endsWith(".zip")) {
            return name.substring(0, name.length() - 4);
        }
        return name + "-extracted";
    }

    /**
     * 静默删除（供重新解压前清理）。
     *
     * @param path 目录或文件
     */
    private void deleteQuietly(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // 清理失败不影响主流程
                }
            });
        } catch (IOException e) {
            log.debug("[agent] 清理缓存失败 {}: {}", path, e.getMessage());
        }
    }

    /**
     * 单文件构件的远端文件名。
     *
     * @param local  本地构件
     * @param custom 调用方指定名
     * @return 文件名
     */
    private String resolveFileName(Path local, String custom) {
        if (custom != null && !custom.isBlank()) {
            return sanitize(custom);
        }
        return sanitize(local.getFileName().toString());
    }
}

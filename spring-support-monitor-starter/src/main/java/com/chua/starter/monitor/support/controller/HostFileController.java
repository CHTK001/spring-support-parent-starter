package com.chua.starter.monitor.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.model.DiskUsageNodeVO;
import com.chua.starter.monitor.support.model.FileEntryVO;
import com.chua.starter.monitor.support.model.FilePermVO;
import com.chua.starter.monitor.support.spi.SystemInfoProvider;
import com.chua.starter.monitor.support.spi.SystemInfoProviderRouter;
import com.chua.starter.monitor.support.support.ShellSupport;
import com.chua.starter.server.support.constants.ServerConstants;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 主机文件管理统一控制器。
 *
 * <p>前端只有一套接口：按主机当前采集模式经 {@link SystemInfoProviderRouter} 路由到对应
 * Provider —— AGENT 走 Agent HTTP、OpenSSH 走 SFTP/SSH、WinRM 走 WinRM、本机走 NIO。
 * 严格跟随采集模式，不做跨通道静默回退。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Tag(name = "主机文件管理", description = "按采集通道路由的远程文件浏览与传输")
@RestController
@RequiredArgsConstructor
@RequestMapping(ServerConstants.API_PREFIX + "/host/file/{hostId}")
public class HostFileController {

    /**
     * 主机台账 Mapper
     */
    private final MsServerHostMapper hostMapper;

    /**
     * 系统信息 Provider 路由器
     */
    private final SystemInfoProviderRouter providerRouter;

    /**
     * 列出目录。
     *
     * @param hostId 主机标识
     * @param path   目录路径，为空时由 Provider 决定默认目录
     * @return 条目列表
     */
    @Operation(summary = "列出目录")
    @GetMapping
    public ReturnResult<List<FileEntryVO>> list(@PathVariable Long hostId,
                                                @RequestParam(required = false) String path) {
        MsServerHost host = load(hostId);
        if (host == null) {
            return ReturnResult.error("主机不存在: " + hostId);
        }
        try {
            return ReturnResult.success(provider(host).files(host, path));
        } catch (Exception e) {
            return ReturnResult.error("列目录失败: " + e.getMessage());
        }
    }

    /**
     * 磁盘占用分析：递归统计目录/文件大小，返回 WizTree 风格占用树。
     *
     * @param hostId 主机标识
     * @param path   起始路径，为空时由 Provider 决定默认目录
     * @param depth  递归深度，为空取默认
     * @return 占用树根节点
     */
    @Operation(summary = "磁盘占用树")
    @GetMapping("/usage")
    public ReturnResult<DiskUsageNodeVO> usage(@PathVariable Long hostId,
                                                @RequestParam(required = false) String path,
                                                @RequestParam(required = false, defaultValue = "4") Integer depth) {
        MsServerHost host = load(hostId);
        if (host == null) {
            return ReturnResult.error("主机不存在: " + hostId);
        }
        try {
            SystemInfoProvider provider = provider(host);
            DiskUsageNodeVO root = provider.diskUsage(host, path, depth == null ? 4 : depth);
            // 根节点补卷总容量/可用空间（供前端按整盘比例绘制并显示“可用空间”）
            fillVolumeSpace(provider, host, root);
            return ReturnResult.success(root);
        } catch (Exception e) {
            return ReturnResult.error("磁盘占用分析失败: " + e.getMessage());
        }
    }

    /**
     * 按根路径匹配所在卷，把总容量/可用空间（字节）填到根节点。
     *
     * <p>Windows 按盘符（C:）匹配；Linux/Mac 取与根路径前缀匹配且挂载点最长的卷。
     * Provider 未给容量时静默跳过，不阻断分析。</p>
     *
     * @param provider 系统信息提供者
     * @param host     目标主机
     * @param root     占用树根节点
     */
    private void fillVolumeSpace(SystemInfoProvider provider, MsServerHost host, DiskUsageNodeVO root) {
        if (root == null) {
            return;
        }
        try {
            List<com.chua.starter.monitor.support.model.DiskRowVO> disks = provider.disks(host);
            if (disks == null || disks.isEmpty()) {
                return;
            }
            String target = root.getPath() == null ? "" : root.getPath().trim();
            com.chua.starter.monitor.support.model.DiskRowVO best = null;
            String drive = windowsDrive(target);
            if (drive != null) {
                // Windows：盘符（C:）匹配 mount 或 name
                for (com.chua.starter.monitor.support.model.DiskRowVO d : disks) {
                    if (drive.equals(windowsDrive(d.getMount())) || drive.equals(windowsDrive(d.getName()))) {
                        best = d;
                        break;
                    }
                }
            } else {
                // Linux/Mac：挂载点为目标路径前缀，取最长
                int bestLen = -1;
                for (com.chua.starter.monitor.support.model.DiskRowVO d : disks) {
                    String m = d.getMount() == null ? "" : d.getMount().trim();
                    if (!m.isEmpty() && target.startsWith(m) && m.length() > bestLen) {
                        best = d;
                        bestLen = m.length();
                    }
                }
            }
            if (best != null && best.getTotalGb() > 0) {
                long gb = 1024L * 1024L * 1024L;
                root.setTotalSpace((long) (best.getTotalGb() * gb));
                root.setFreeSpace((long) (Math.max(0, best.getAvailGb()) * gb));
            }
        } catch (Exception ignore) {
            // 容量补充失败不影响占用树本身
        }
    }

    /**
     * 取 Windows 盘符前缀（小写 "c:"），非 Windows 路径返回 null。
     *
     * @param path 路径
     * @return 盘符（如 "c:"），无法识别返回 null
     */
    private String windowsDrive(String path) {
        if (path == null || path.length() < 2) {
            return null;
        }
        if (path.charAt(1) == ':') {
            return path.substring(0, 2).toLowerCase(java.util.Locale.ROOT);
        }
        return null;
    }

    /**
     * 读取文本文件内容。
     *
     * @param hostId 主机标识
     * @param path   文件路径
     * @return 文本内容
     */
    @Operation(summary = "读取文本内容")
    @GetMapping("/content")
    public ReturnResult<String> readContent(@PathVariable Long hostId,
                                            @RequestParam String path) {
        MsServerHost host = load(hostId);
        if (host == null) {
            return ReturnResult.error("主机不存在: " + hostId);
        }
        try {
            String text = provider(host).readText(host, path);
            // Provider 以 null 表示读失败：当成空内容返回会让不存在的文件在编辑器里显示为空白，
            // 用户一保存就把「读失败」变成了「写成功」
            return text == null ? ReturnResult.error("读取文件失败: " + path) : ReturnResult.success(text);
        } catch (Exception e) {
            return ReturnResult.error("读取文件失败: " + e.getMessage());
        }
    }

    /**
     * 写入文本文件内容（整文件覆盖）。
     *
     * @param hostId  主机标识
     * @param path    文件路径
     * @param content 新内容
     * @return 结果
     */
    @Operation(summary = "写入文本内容")
    @PutMapping("/content")
    public ReturnResult<Void> writeContent(@PathVariable Long hostId,
                                           @RequestParam String path,
                                           @RequestBody String content) {
        MsServerHost host = load(hostId);
        if (host == null) {
            return ReturnResult.error("主机不存在: " + hostId);
        }
        try {
            boolean ok = provider(host).writeText(host, path, content);
            return ok ? ReturnResult.success() : ReturnResult.error("写入文件失败: " + path);
        } catch (Exception e) {
            return ReturnResult.error("写入文件失败: " + e.getMessage());
        }
    }

    /**
     * 上传文件到指定目录。
     *
     * @param hostId 主机标识
     * @param path   目标目录
     * @param file   上传文件
     * @return 远端完整路径
     */
    @Operation(summary = "上传文件")
    @PostMapping("/upload")
    public ReturnResult<String> upload(@PathVariable Long hostId,
                                       @RequestParam(required = false) String path,
                                       @RequestParam("file") MultipartFile file) {
        MsServerHost host = load(hostId);
        if (host == null) {
            return ReturnResult.error("主机不存在: " + hostId);
        }
        try {
            String remote = joinPath(path, file.getOriginalFilename(), host);
            boolean ok;
            try (InputStream in = file.getInputStream()) {
                ok = provider(host).uploadFile(host, remote, in, file.getSize());
            }
            return ok ? ReturnResult.success(remote) : ReturnResult.error("上传失败: " + remote);
        } catch (Exception e) {
            return ReturnResult.error("上传失败: " + e.getMessage());
        }
    }

    /**
     * 下载文件或目录。
     *
     * <p>文件直接流式回传；目录由服务端遍历远端目录树并实时打成 ZIP（条目分隔符统一为
     * {@code /}、保留相对路径），下载名为「目录名.zip」。全程流式输出，不把整包读进内存。</p>
     *
     * @param hostId 主机标识
     * @param path   文件或目录路径
     * @return 流式响应（文件为原始字节，目录为 ZIP）
     */
    @Operation(summary = "下载文件/目录(目录自动打包zip)")
    @GetMapping("/download")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable Long hostId,
                                                          @RequestParam String path) {
        MsServerHost host = load(hostId);
        if (host == null) {
            return ResponseEntity.notFound().build();
        }
        SystemInfoProvider provider = provider(host);
        boolean directory;
        try {
            directory = pathIsDirectory(provider, host, path);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }

        String baseName = baseName(path);
        if (baseName.isBlank()) {
            baseName = "download";
        }
        HttpHeaders headers = new HttpHeaders();
        if (directory) {
            String zipName = URLEncoder.encode(baseName + ".zip", StandardCharsets.UTF_8).replace("+", "%20");
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + zipName + "\"");
            StreamingResponseBody body = out -> {
                try (ZipOutputStream zos = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
                    writeZipEntries(provider, host, path, "", zos);
                }
            };
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.parseMediaType("application/zip"))
                    .body(body);
        }

        String fileName = URLEncoder.encode(baseName, StandardCharsets.UTF_8).replace("+", "%20");
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"");
        StreamingResponseBody body = out -> {
            try (InputStream in = provider.downloadFile(host, path)) {
                if (in == null) {
                    throw new java.io.IOException("文件不存在或读取失败: " + path);
                }
                in.transferTo(out);
            }
        };
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }

    /**
     * 递归把远端目录写入 ZIP。
     *
     * @param provider 采集通道
     * @param host     主机
     * @param dirAbs   当前层远端绝对目录
     * @param relPrefix 当前层相对 ZIP 根的前缀（空串为最外层）
     * @param zos      ZIP 输出
     */
    private void writeZipEntries(SystemInfoProvider provider, MsServerHost host, String dirAbs,
                                 String relPrefix, ZipOutputStream zos) throws java.io.IOException {
        List<FileEntryVO> entries = provider.files(host, dirAbs);
        if (entries == null) {
            return;
        }
        for (FileEntryVO e : entries) {
            String rel = relPrefix.isEmpty() ? e.getName() : relPrefix + "/" + e.getName();
            String childAbs = joinPath(dirAbs, e.getName(), host);
            if (e.isDirectory()) {
                zos.putNextEntry(new ZipEntry(rel + "/"));
                zos.closeEntry();
                writeZipEntries(provider, host, childAbs, rel, zos);
            } else {
                zos.putNextEntry(new ZipEntry(rel));
                try (InputStream in = provider.downloadFile(host, childAbs)) {
                    if (in != null) {
                        in.transferTo(zos);
                    }
                }
                zos.closeEntry();
            }
        }
    }

    /**
     * 判断目标是否为目录：列出其父目录并按名称匹配条目，避免给 Provider 增加 stat 接口。
     *
     * @param provider 采集通道
     * @param host     主机
     * @param path     目标路径
     * @return 目录返回 true
     */
    private boolean pathIsDirectory(SystemInfoProvider provider, MsServerHost host, String path) {
        if (path == null) {
            return false;
        }
        String trimmed = path.trim();
        if (isRootPath(trimmed)) {
            return true;
        }
        int slash = Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'));
        if (slash < 0) {
            // 无父目录信息：尝试直接列该路径，能列出内容即视为目录
            List<FileEntryVO> entries = provider.files(host, trimmed);
            return entries != null && !entries.isEmpty();
        }
        String name = trimmed.substring(slash + 1);
        if (name.isEmpty()) {
            // 以分隔符结尾（如 C:\、/var/）视为目录
            return true;
        }
        String parent = slash == 0 ? trimmed.substring(0, 1) : trimmed.substring(0, slash);
        // Windows 盘符根：C:\ziptest 的父目录必须是 C:\ 而不是 C:（后者列目录无法匹配）
        if (slash == 2 && trimmed.charAt(1) == ':') {
            parent = trimmed.substring(0, 3);
        }
        List<FileEntryVO> entries = provider.files(host, parent);
        if (entries == null) {
            return false;
        }
        for (FileEntryVO e : entries) {
            if (name.equals(e.getName())) {
                return e.isDirectory();
            }
        }
        return false;
    }

    /**
     * 是否为文件系统根：Windows 盘符根（C:\、C:、C:/）或类 Unix 根（/）。
     */
    private static boolean isRootPath(String path) {
        if ("/".equals(path)) {
            return true;
        }
        return path.matches("[A-Za-z]:[\\\\/]?");
    }

    /**
     * 取路径最后一段作为文件/目录名。
     */
    private static String baseName(String path) {
        if (path == null) {
            return "";
        }
        String p = path.trim();
        while (p.length() > 1 && (p.endsWith("/") || p.endsWith("\\"))) {
            p = p.substring(0, p.length() - 1);
        }
        int slash = Math.max(p.lastIndexOf('/'), p.lastIndexOf('\\'));
        return slash >= 0 && slash + 1 < p.length() ? p.substring(slash + 1) : p;
    }

    /**
     * 创建目录（含父目录）。
     *
     * @param hostId 主机标识
     * @param path   新目录路径
     * @return 结果
     */
    @Operation(summary = "创建目录")
    @PostMapping("/mkdir")
    public ReturnResult<Void> mkdir(@PathVariable Long hostId,
                                    @RequestParam String path) {
        MsServerHost host = load(hostId);
        if (host == null) {
            return ReturnResult.error("主机不存在: " + hostId);
        }
        try {
            boolean ok = provider(host).mkdir(host, path);
            return ok ? ReturnResult.success() : ReturnResult.error("创建目录失败: " + path);
        } catch (Exception e) {
            return ReturnResult.error("创建目录失败: " + e.getMessage());
        }
    }

    /**
     * 删除文件或目录（目录递归删除）。
     *
     * @param hostId 主机标识
     * @param path   目标路径
     * @return 结果
     */
    @Operation(summary = "删除文件或目录")
    @DeleteMapping
    public ReturnResult<Void> delete(@PathVariable Long hostId,
                                     @RequestParam String path) {
        MsServerHost host = load(hostId);
        if (host == null) {
            return ReturnResult.error("主机不存在: " + hostId);
        }
        try {
            boolean ok = provider(host).delete(host, path);
            return ok ? ReturnResult.success() : ReturnResult.error("删除失败: " + path);
        } catch (Exception e) {
            return ReturnResult.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 重命名 / 移动。
     *
     * @param hostId 主机标识
     * @param from   源路径
     * @param to     目标路径
     * @return 结果
     */
    @Operation(summary = "重命名/移动")
    @PutMapping("/rename")
    public ReturnResult<Void> rename(@PathVariable Long hostId,
                                     @RequestParam String from,
                                     @RequestParam String to) {
        MsServerHost host = load(hostId);
        if (host == null) {
            return ReturnResult.error("主机不存在: " + hostId);
        }
        try {
            boolean ok = provider(host).rename(host, from, to);
            return ok ? ReturnResult.success() : ReturnResult.error("重命名失败: " + from);
        } catch (Exception e) {
            return ReturnResult.error("重命名失败: " + e.getMessage());
        }
    }

    /**
     * 读取文件/目录权限（仅类 Unix）。
     *
     * @param hostId 主机标识
     * @param path   目标路径
     * @return mode（八进制，如 755）、owner、group
     */
    @Operation(summary = "读取权限(类Unix)")
    @GetMapping("/chmod")
    public ReturnResult<FilePermVO> readChmod(@PathVariable Long hostId,
                                              @RequestParam String path) {
        MsServerHost host = load(hostId);
        if (host == null) {
            return ReturnResult.error("主机不存在: " + hostId);
        }
        if (isWindows(host)) {
            return ReturnResult.error("权限编辑仅支持 Linux / Unix 主机");
        }
        try {
            SystemInfoProvider provider = provider(host);
            ShellSupport.Probe probe = ShellSupport.compose("", "stat -c '%a %U %G' " + shellQuote(path),
                    provider.shellDialect(host), true);
            ShellSupport.ProbeResult result = ShellSupport.interpret(provider.exec(host, probe.line()), probe);
            if (result.failed()) {
                return ReturnResult.error("读取权限失败: " + result.reason());
            }
            Matcher m = CHMOD_OUT.matcher(result.output());
            if (!m.find()) {
                return ReturnResult.error("无法解析权限: " + result.output().trim());
            }
            return ReturnResult.success(new FilePermVO(m.group(1), m.group(2), m.group(3)));
        } catch (Exception e) {
            return ReturnResult.error("读取权限失败: " + e.getMessage());
        }
    }

    /**
     * 下发文件/目录权限（仅类 Unix）。
     *
     * @param hostId 主机标识
     * @param path   目标路径
     * @param mode   八进制权限（3 或 4 位，如 755 / 4755）
     * @return 结果
     */
    @Operation(summary = "下发权限(类Unix)")
    @PutMapping("/chmod")
    public ReturnResult<Void> writeChmod(@PathVariable Long hostId,
                                         @RequestParam String path,
                                         @RequestParam String mode) {
        MsServerHost host = load(hostId);
        if (host == null) {
            return ReturnResult.error("主机不存在: " + hostId);
        }
        if (isWindows(host)) {
            return ReturnResult.error("权限编辑仅支持 Linux / Unix 主机");
        }
        String m = String.valueOf(mode).trim();
        if (!m.matches("[0-7]{3,4}")) {
            return ReturnResult.error("非法权限模式: " + mode);
        }
        try {
            SystemInfoProvider provider = provider(host);
            ShellSupport.Probe probe = ShellSupport.compose("", "chmod " + m + " " + shellQuote(path),
                    provider.shellDialect(host), true);
            ShellSupport.ProbeResult result = ShellSupport.interpret(provider.exec(host, probe.line()), probe);
            // 丢掉了 exec 结果就等于对不存在的文件、非属主、只读挂载一律报成功
            if (result.failed()) {
                return ReturnResult.error("下发权限失败: " + result.reason());
            }
            return ReturnResult.success();
        } catch (Exception e) {
            return ReturnResult.error("下发权限失败: " + e.getMessage());
        }
    }

    /** stat 输出解析：755 owner group */
    private static final Pattern CHMOD_OUT = Pattern.compile("(\\d{3,4})\\s+(\\S+)\\s+(\\S+)");

    /**
     * 目标是否为 Windows 主机。
     *
     * @param host 主机
     * @return Windows 返回 true
     */
    private static boolean isWindows(MsServerHost host) {
        return "WINDOWS".equalsIgnoreCase(host.getServerHostOsType());
    }

    /**
     * 单引号包裹 shell 参数，路径中的单引号做标准转义。
     *
     * @param s 原始路径
     * @return shell 安全字面量
     */
    private static String shellQuote(String s) {
        return "'" + String.valueOf(s).replace("'", "'\\''") + "'";
    }

    /**
     * 按标识加载主机。
     *
     * @param hostId 主机标识
     * @return 主机，不存在返回 null
     */
    private MsServerHost load(Long hostId) {
        return hostMapper.selectById(hostId);
    }

    /**
     * 按主机当前采集模式路由 Provider。
     *
     * @param host 目标主机
     * @return 匹配的 Provider
     */
    private SystemInfoProvider provider(MsServerHost host) {
        return providerRouter.route(host);
    }

    /**
     * 拼接目录与文件名，按平台选择分隔符。
     *
     * @param dir      目录路径，可为空
     * @param fileName 文件名
     * @param host     目标主机
     * @return 完整路径
     */
    private static String joinPath(String dir, String fileName, MsServerHost host) {
        String name = fileName == null ? "file" : fileName;
        if (dir == null || dir.isBlank()) {
            return name;
        }
        boolean windows = "WINDOWS".equalsIgnoreCase(host.getServerHostOsType());
        String sep = windows ? "\\" : "/";
        String base = dir;
        if (base.endsWith("/") || base.endsWith("\\")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + sep + name;
    }
}

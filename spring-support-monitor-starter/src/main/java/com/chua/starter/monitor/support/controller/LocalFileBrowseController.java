package com.chua.starter.monitor.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * 本机文件浏览（给「选择文件 / 选择文件夹」用的只读目录列举）。
 *
 * <p>为什么需要它：数据同步的输入源支持「服务器本地文件 / 目录」，而现有可浏览的能力只有两类——
 * {@code HostFileController} 走 SSH 列<b>远端主机</b>的文件，文件服务器那套是对象存储。
 * 都不适用于「选后端进程所在机器的目录」，所以这里补一个只读的本机列举。</p>
 *
 * <p><b>权限面</b>：本接口只读（不提供写 / 删 / 上传），且只返回名字、是否目录、大小、修改时间；
 * 但它确实能列举后端进程可访问的任何绝对路径。与 {@code /ms/monitor/file-assets} 同属监控台的
 * 受信运维面（{@code /ms/**} 在生产由鉴权网关把关）。若需要收紧，可在 {@code monitor.file-browse.roots}
 * 配置允许的根目录前缀（留空表示不限制，默认留空以保持开箱可用）。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
@Tag(name = "本机文件浏览", description = "只读列举后端所在机器的目录与数据文件，供同步任务选择输入源")
@RestController
@RequestMapping("/ms/monitor/local-files")
public class LocalFileBrowseController {

    /**
     * 允许浏览的根目录前缀列表（逗号分隔，可空表示不限制）
     */
    private static final String ROOTS_PROPERTY = "monitor.file-browse.roots";

    /**
     * 「选择文件」时只认这些扩展名，避免把整台机器的杂物都列出来
     */
    private static final List<String> DATA_SUFFIXES = List.of(".csv", ".tsv", ".txt", ".xls", ".xlsx", ".xlsm");

    /**
     * 单次最多返回的条目数（目录很大时避免把响应撑爆）
     */
    private static final int MAX_ENTRIES = 500;

    /**
     * 列举子目录（可含数据文件）。
     *
     * @param path   绝对路径；留空表示列举文件系统根（Windows 列出盘符，类 Unix 列出 /）
     * @param files  是否同时返回文件（「选择文件」传 true，「选择文件夹」传 false）
     * @return 条目列表，每项 {key, name, isDir, size, updatedAt}
     */
    @Operation(summary = "列举本机目录（只读）")
    @GetMapping("/list")
    public ReturnResult<List<LocalFileEntry>> list(@RequestParam(required = false) String path,
                                                   @RequestParam(required = false) Boolean files) {
        boolean withFiles = files != null && files;
        try {
            if (path == null || path.isBlank()) {
                return ReturnResult.ok(roots());
            }
            Path dir = Paths.get(path);
            if (!dir.isAbsolute()) {
                throw new IllegalArgumentException("必须使用绝对路径: " + path);
            }
            if (!Files.isDirectory(dir)) {
                throw new IllegalArgumentException("目录不存在: " + path);
            }
            assertAllowed(dir);
            return ReturnResult.ok(children(dir, withFiles));
        } catch (IllegalArgumentException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.warn("[local-files] 列举目录失败, path={}, message={}", path, e.getMessage());
            return ReturnResult.error("列举目录失败: " + e.getMessage());
        }
    }

    /**
     * 文件系统根：Windows 列出可用盘符，其它系统列出 {@code /}。
     *
     * @return 根条目
     */
    private List<LocalFileEntry> roots() {
        List<LocalFileEntry> list = new ArrayList<>();
        File[] roots = File.listRoots();
        if (roots != null) {
            for (File root : roots) {
                if (root.canRead()) {
                    list.add(new LocalFileEntry(root.getAbsolutePath(), root.getAbsolutePath(), true, null, null));
                }
            }
        }
        if (list.isEmpty()) {
            list.add(new LocalFileEntry("/", "/", true, null, null));
        }
        return list;
    }

    /**
     * 列举目录子项：目录在前，其次按名字排序；文件按数据扩展名过滤。
     *
     * @param dir       目录
     * @param withFiles 是否包含文件
     * @return 条目列表
     * @throws Exception 读取目录失败
     */
    private List<LocalFileEntry> children(Path dir, boolean withFiles) throws Exception {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(Files::isDirectory)
                    .map(child -> toEntry(child, true))
                    .sorted(Comparator.comparing(LocalFileEntry::name, String.CASE_INSENSITIVE_ORDER))
                    .limit(MAX_ENTRIES)
                    .collect(java.util.stream.Collectors.collectingAndThen(
                            java.util.stream.Collectors.toList(),
                            directories -> {
                                if (!withFiles) {
                                    return directories;
                                }
                                directories.addAll(filesOf(dir));
                                return directories;
                            }));
        }
    }

    /**
     * 目录下的数据文件。
     *
     * @param dir 目录
     * @return 文件条目
     */
    private List<LocalFileEntry> filesOf(Path dir) {
        List<LocalFileEntry> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(this::isDataFile)
                    .map(child -> toEntry(child, false))
                    .sorted(Comparator.comparing(LocalFileEntry::name, String.CASE_INSENSITIVE_ORDER))
                    .limit(MAX_ENTRIES)
                    .forEach(files::add);
        } catch (Exception e) {
            log.debug("[local-files] 列举文件失败, dir={}, message={}", dir, e.getMessage());
        }
        return files;
    }

    /**
     * 是否数据文件（按扩展名）。
     *
     * @param file 文件
     * @return true 表示可作为同步输入
     */
    private boolean isDataFile(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return DATA_SUFFIXES.stream().anyMatch(name::endsWith);
    }

    /**
     * 组条目。
     *
     * @param path     路径
     * @param isDir    是否目录
     * @return 条目
     */
    private LocalFileEntry toEntry(Path path, boolean isDir) {
        Long size = null;
        Long updatedAt = null;
        try {
            if (!isDir) {
                size = Files.size(path);
            }
            updatedAt = Files.getLastModifiedTime(path).toMillis();
        } catch (Exception e) {
            log.debug("[local-files] 读取文件属性失败, path={}", path);
        }
        return new LocalFileEntry(path.toAbsolutePath().toString(), path.getFileName().toString(), isDir, size, updatedAt);
    }

    /**
     * 允许根校验（未配置时不限制）。
     *
     * @param dir 待访问目录
     */
    private void assertAllowed(Path dir) {
        String configured = System.getProperty(ROOTS_PROPERTY);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("MONITOR_FILE_BROWSE_ROOTS");
        }
        if (configured == null || configured.isBlank()) {
            return;
        }
        String target = dir.toAbsolutePath().toString();
        for (String root : configured.split(",")) {
            String item = root.trim();
            if (!item.isEmpty() && target.startsWith(item)) {
                return;
            }
        }
        throw new IllegalArgumentException("路径不在允许浏览的根目录内: " + target);
    }

    /**
     * 本机文件条目。
     *
     * @param key       绝对路径
     * @param name      文件名
     * @param isDir     是否目录
     * @param size      文件大小（目录为 null）
     * @param updatedAt 修改时间毫秒（取不到为 null）
     */
    public record LocalFileEntry(String key, String name, boolean isDir, Long size, Long updatedAt) {
    }
}

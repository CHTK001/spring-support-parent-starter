package com.chua.starter.filesystem.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.filesystem.support.pojo.FileDirItemResponse;
import com.chua.starter.filesystem.support.properties.FileStorageProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 服务器目录浏览接口（安全开关控制，默认关闭）。
 * <p>
 * 提供服务器本地目录树浏览能力，供前端「上传目录 / 选择目录」选择器使用。
 * 通过 {@code plugin.filesystem.dir-list} 开关控制是否暴露，默认关闭。
 * 返回的是服务器绝对路径，因此仅管理员可访问。
 *
 * <p><b>为什么同时返回文件</b>：选择目录时如果只列子目录，最后一层目录看起来永远是空的，
 * 用户无法确认自己进的是不是放模型 / 数据文件的目录。这里始终把文件也列出来，
 * 由前端按「目录可选择、文件仅展示」处理选择语义。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/v2/file/dir")
@RequiredArgsConstructor
@Tag(name = "文件目录浏览", description = "服务器本地目录浏览（需开启 dir-list 开关）")
public class FileDirController {

    /**
     * 父级目录条目名
     */
    private static final String PARENT_ENTRY = "..";

    /**
     * 单次最多返回的条目数（目录很大时避免把响应撑爆）
     */
    private static final int MAX_ENTRIES = 1000;

    /**
     * 文件存储配置
     */
    private final FileStorageProperties properties;

    /**
     * 浏览指定目录下的子目录与文件。
     *
     * @param path  目录路径；留空或 {@code /} 时返回文件系统根（Windows 为盘符列表）
     * @param files 是否同时返回文件，默认 true
     * @return 子目录与文件列表，目录在前
     */
    @Operation(summary = "目录列表", description = "浏览指定路径下的子目录与文件")
    @Permission(role = {"admin", "superadmin"})
    @GetMapping("/list")
    public ReturnResult<List<FileDirItemResponse>> list(
            @Parameter(description = "目录路径") @RequestParam(required = false) String path,
            @Parameter(description = "是否同时返回文件") @RequestParam(required = false, defaultValue = "true") Boolean files
    ) {
        // 安全开关：未开启时拒绝访问
        if (!properties.isDirList()) {
            return ReturnResult.error("目录浏览功能未开启，请配置 plugin.filesystem.dir-list=true");
        }

        boolean withFiles = files == null || files;
        boolean windows = File.separatorChar == '\\';
        try {
            // 组件用虚拟根 `/` 表示文件系统根：Windows 上 `/` 不是绝对路径，
            // 直接 Paths.get("/") 会落到当前盘根，必须换成盘符列表。
            if (path == null || path.isBlank() || (windows && "/".equals(path.trim()))) {
                return ReturnResult.ok(windows ? roots() : children(Paths.get("/"), withFiles));
            }

            Path dir = Paths.get(path);
            if (!dir.isAbsolute()) {
                return ReturnResult.error("必须使用绝对路径: " + path);
            }
            if (!Files.exists(dir) || !Files.isDirectory(dir)) {
                return ReturnResult.error("目录不存在: " + dir);
            }
            return ReturnResult.ok(children(dir, withFiles));
        } catch (IllegalArgumentException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.warn("[FileDir] 浏览目录失败: {} - {}", path, e.getMessage());
            return ReturnResult.error("目录读取失败: " + e.getMessage());
        }
    }

    /**
     * 文件系统根（Windows 盘符列表）。
     *
     * @return 根目录条目
     */
    private List<FileDirItemResponse> roots() {
        List<FileDirItemResponse> result = new ArrayList<>();
        File[] roots = File.listRoots();
        if (roots != null) {
            for (File root : roots) {
                if (root != null && root.canRead()) {
                    result.add(toEntry(root.toPath()));
                }
            }
        }
        return result;
    }

    /**
     * 列出目录子项：目录在前，其次按名称排序。
     *
     * @param dir       目录
     * @param withFiles 是否包含文件
     * @return 条目列表
     * @throws IOException 读取目录失败
     */
    private List<FileDirItemResponse> children(Path dir, boolean withFiles) throws IOException {
        List<FileDirItemResponse> result = new ArrayList<>();
        Path parent = dir.getParent();
        if (parent != null) {
            result.add(new FileDirItemResponse(
                    PARENT_ENTRY,
                    parent.toAbsolutePath().normalize().toString(),
                    true,
                    null,
                    null));
        }

        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> entries = stream
                    .filter(entry -> Files.isDirectory(entry) || (withFiles && Files.isRegularFile(entry)))
                    .sorted(Comparator
                            .comparing((Path entry) -> !Files.isDirectory(entry))
                            .thenComparing(entry -> nameOf(entry), String.CASE_INSENSITIVE_ORDER))
                    .limit(MAX_ENTRIES)
                    .toList();
            for (Path entry : entries) {
                result.add(toEntry(entry));
            }
        }
        return result;
    }

    /**
     * 组条目。
     *
     * @param path 路径
     * @return 条目
     */
    private FileDirItemResponse toEntry(Path path) {
        boolean isDir = Files.isDirectory(path);
        Long size = null;
        Long updatedAt = null;
        try {
            if (!isDir) {
                size = Files.size(path);
            }
            updatedAt = Files.getLastModifiedTime(path).toMillis();
        } catch (Exception e) {
            log.debug("[FileDir] 读取文件属性失败: {}", path, e);
        }
        return new FileDirItemResponse(
                nameOf(path),
                path.toAbsolutePath().normalize().toString(),
                isDir,
                size,
                updatedAt);
    }

    /**
     * 条目显示名：普通路径取文件名，盘符根取绝对路径。
     *
     * @param path 路径
     * @return 显示名
     */
    private String nameOf(Path path) {
        Path fileName = path.getFileName();
        return fileName == null ? path.toString() : fileName.toString();
    }
}

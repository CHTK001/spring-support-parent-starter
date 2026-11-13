package com.chua.starter.server.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.storage.FileStorage;
import com.chua.common.support.storage.metadata.Metadata;
import com.chua.common.support.storage.request.ListObjectRequest;
import com.chua.common.support.storage.result.DeleteObjectResult;
import com.chua.common.support.storage.result.GetObjectResult;
import com.chua.common.support.storage.result.ListObjectResult;
import com.chua.common.support.storage.result.ObjectResult;
import com.chua.starter.filesystem.support.template.FileStorageTemplate;
import com.chua.starter.server.support.constants.ServerConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 主机文件（运行本服务的这台机器）的浏览、下载与删除。
 *
 * <p>存储后端直接用 filesystem-starter 的 {@link FileStorageTemplate}，
 * 不另写一套落盘逻辑：文件物理位置就是该 provider 的 {@code base-path/<bucket>/<key>}。
 * 上传刻意不在这里实现——分片上传、断点与全局进度 SSE 由 filesystem-starter 的
 * {@code /v2/file/upload-task/*} 提供，前端直接对接，避免两套上传进度。</p>
 *
 * <p>宿主没有装配文件存储（未引入 filesystem-starter 或未开 {@code plugin.filesystem.enable}）时，
 * 本控制器返回明确的「未启用」错误，而不是启动失败。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Tag(name = "主机文件", description = "运行本服务的这台机器的文件浏览、下载与删除")
@Slf4j
@RestController
@RequestMapping(ServerConstants.API_PREFIX + "/host-file")
public class ReleaseHostFileController {

    /**
     * 文件存储模板（宿主未装配时为 null）
     */
    private final ObjectProvider<FileStorageTemplate> templateProvider;

    /**
     * 构造
     *
     * @param templateProvider 文件存储模板提供者
     */
    public ReleaseHostFileController(ObjectProvider<FileStorageTemplate> templateProvider) {
        this.templateProvider = templateProvider;
    }

    /**
     * 主机文件根信息：默认桶名 + 本地根目录（前端面包屑与路径回填用）。
     *
     * @param bucket 存储桶名，留空用默认桶
     * @return bucket / root / enabled
     */
    @Operation(summary = "主机文件根信息")
    @GetMapping("/config")
    public ReturnResult<Map<String, Object>> config(@RequestParam(name = "bucket", required = false) String bucket) {
        FileStorageTemplate template = templateProvider.getIfAvailable();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", template != null);
        if (template == null) {
            return ReturnResult.ok(data);
        }
        String name = bucket == null || bucket.isBlank() ? template.getDefaultStorageName() : bucket;
        data.put("bucket", name);
        java.nio.file.Path root = rootPath(resolve(bucket));
        data.put("root", root == null ? "" : root.toString());
        return ReturnResult.ok(data);
    }

    /**
     * 列出目录下的文件与子目录。
     *
     * @param bucket 存储桶名，留空用默认桶
     * @param path   目录（相对桶根），留空为根
     * @param limit  每页条数
     * @param offset 跳过条数
     * @return 条目列表 + 总数
     */
    @Operation(summary = "列出主机文件")
    @GetMapping("/list")
    public ReturnResult<Map<String, Object>> list(@RequestParam(name = "bucket", required = false) String bucket,
                                                  @RequestParam(name = "path", required = false) String path,
                                                  @RequestParam(name = "limit", defaultValue = "200") int limit,
                                                  @RequestParam(name = "offset", defaultValue = "0") int offset) {
        FileStorage storage = resolve(bucket);
        if (storage == null) {
            return ReturnResult.error("文件存储未启用，请开启 plugin.filesystem.enable");
        }
        try {
            ListObjectResult result = storage.listObject(ListObjectRequest.builder()
                    .filePath(normalizeDir(path))
                    .limit(Math.max(1, limit))
                    .offset(Math.max(0, offset))
                    .build());
            List<Map<String, Object>> items = new ArrayList<>();
            java.nio.file.Path root = rootPath(storage);
            if (result != null && result.getMetadata() != null) {
                for (Metadata meta : result.getMetadata()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", meta.getName());
                    row.put("key", meta.getKey());
                    row.put("size", meta.getSize());
                    row.put("directory", meta.isDirectory());
                    row.put("lastModified", meta.getLastModified());
                    // 升级投放要的是主机绝对路径：由存储实现给根目录，别在前端猜 base-path 拼接规则
                    if (root != null && !meta.isDirectory()) {
                        row.put("hostPath", root.resolve(meta.getKey()).toString());
                    }
                    items.add(row);
                }
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("items", items);
            data.put("total", result == null ? items.size() : result.getTotal());
            data.put("marker", result == null ? null : result.getMarker());
            return ReturnResult.ok(data);
        } catch (Exception e) {
            log.error("[主机文件] 列目录失败: bucket={} path={}", bucket, path, e);
            return ReturnResult.error("列目录失败: " + e.getMessage());
        }
    }

    /**
     * 删除文件。
     *
     * @param bucket 存储桶名，留空用默认桶
     * @param key    文件 key
     * @return 是否删除成功
     */
    @Operation(summary = "删除主机文件")
    @DeleteMapping
    public ReturnResult<Boolean> delete(@RequestParam(name = "key") String key,
                                        @RequestParam(name = "bucket", required = false) String bucket) {
        FileStorage storage = resolve(bucket);
        if (storage == null) {
            return ReturnResult.error("文件存储未启用，请开启 plugin.filesystem.enable");
        }
        if (key == null || key.isBlank()) {
            return ReturnResult.error("缺少文件 key");
        }
        try {
            DeleteObjectResult result = storage.deleteObject(key);
            boolean ok = result != null && result.getResultCode() == ObjectResult.ResultCode.SUCCESS;
            return ok ? ReturnResult.ok(Boolean.TRUE)
                    : ReturnResult.error(result == null ? "删除失败" : result.getMessage());
        } catch (Exception e) {
            log.error("[主机文件] 删除失败: key={}", key, e);
            return ReturnResult.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 下载文件（浏览器直接下载，不落临时文件）。
     *
     * @param key    文件 key
     * @param bucket 存储桶名，留空用默认桶
     * @return 文件流
     */
    @Operation(summary = "下载主机文件")
    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> download(@RequestParam(name = "key") String key,
                                                        @RequestParam(name = "bucket", required = false) String bucket) {
        FileStorage storage = resolve(bucket);
        if (storage == null || key == null || key.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        GetObjectResult result = storage.getObject(key);
        if (result == null || result.getInputStream() == null) {
            return ResponseEntity.notFound().build();
        }
        String fileName = result.getMetadata() == null || result.getMetadata().getName() == null
                ? key.substring(key.lastIndexOf('/') + 1)
                : result.getMetadata().getName();
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"file\"; filename*=UTF-8''" + encoded)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(result.getInputStream()));
    }

    /**
     * 计算文件 MD5（流式读取，不整包进内存）。
     *
     * <p>按需计算而不是列目录就全算：一次列 200 个 jar 会把磁盘打满，
     * 用户真正要的通常只是「马上要投放的那个包」的指纹。</p>
     *
     * @param key    文件 key
     * @param bucket 存储桶名，留空用默认桶
     * @return md5 / size / elapsedMs
     */
    @Operation(summary = "计算主机文件 MD5")
    @GetMapping("/md5")
    public ReturnResult<Map<String, Object>> md5(@RequestParam(name = "key") String key,
                                                 @RequestParam(name = "bucket", required = false) String bucket) {
        FileStorage storage = resolve(bucket);
        if (storage == null) {
            return ReturnResult.error("文件存储未启用，请开启 plugin.filesystem.enable");
        }
        java.nio.file.Path root = rootPath(storage);
        if (root == null) {
            return ReturnResult.error("仅支持本地文件存储计算 MD5");
        }
        if (key == null || key.isBlank()) {
            return ReturnResult.error("缺少文件 key");
        }
        java.nio.file.Path file = root.resolve(key).normalize();
        // 目录穿越防护：key 来自前端，归一后必须仍在根目录内
        if (!file.startsWith(root) || !java.nio.file.Files.isRegularFile(file)) {
            return ReturnResult.error("文件不存在: " + key);
        }
        long start = System.currentTimeMillis();
        try (InputStream in = java.nio.file.Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            // [P3C A20 豁免]
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("md5", HexFormat.of().formatHex(digest.digest()));
            data.put("size", java.nio.file.Files.size(file));
            data.put("elapsedMs", System.currentTimeMillis() - start);
            return ReturnResult.ok(data);
        } catch (Exception e) {
            log.error("[主机文件] 计算 MD5 失败: key={}", key, e);
            return ReturnResult.error("计算 MD5 失败: " + e.getMessage());
        }
    }

    /**
     * 取存储后端：显式桶名优先，否则默认桶。
     *
     * @param bucket 桶名
     * @return 存储后端；未装配或桶不存在返回 null
     */
    private FileStorage resolve(String bucket) {
        FileStorageTemplate template = templateProvider.getIfAvailable();
        if (template == null) {
            return null;
        }
        if (bucket == null || bucket.isBlank()) {
            return template.getDefaultStorage();
        }
        return template.getStorage(bucket);
    }

    /**
     * 本地存储的根目录：只有本地实现能还原主机绝对路径。
     *
     * @param storage 存储后端
     * @return 根目录；非本地实现返回 null
     */
    private java.nio.file.Path rootPath(FileStorage storage) {
        if (storage instanceof com.chua.starter.filesystem.support.local.LocalFileStorage local) {
            return local.getRootPath();
        }
        return null;
    }

    /**
     * 目录归一：空表示桶根；保证以 {@code /} 结尾，避免 key 前缀串到同级文件。
     *
     * @param path 目录
     * @return 归一后的目录
     */
    private String normalizeDir(String path) {
        if (path == null || path.isBlank() || "/".equals(path)) {
            return "";
        }
        String dir = path.startsWith("/") ? path.substring(1) : path;
        return dir.endsWith("/") ? dir : dir + "/";
    }
}

package com.chua.starter.ai.support.manage.api;

import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.common.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 前端文件系统控制器（对接 nomicore {@code fs.*} 契约）。
 *
 * <p>替代原 Rust 后端的文件系统能力，使用 {@link java.nio.file} 真实操作本地磁盘。
 * 所有接口以 {@code /api/fs} 为前缀，请求体字段统一使用 snake_case，响应统一包裹在
 * {@link ApiResult} 信封中（{@code success:true,data:<T>}）。</p>
 *
 * <p>基准目录由 {@code nomifun.fs-root} 配置（默认 {@code ./fs-root}），{@code list} 等接口的
 * {@code root}/{@code workspace} 参数可作为当次请求的覆盖基准目录。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@RestController
@RequestMapping("/api/fs")
@RequiredArgsConstructor
@Slf4j
public class ApiFsController {

    /**
     * 文件系统根目录（可被单次请求覆盖）
     */
    @Value("${nomifun.fs-root:./fs-root}")
    private String fsRoot;

    /**
     * 远端图片缓存目录（懒初始化）
     */
    private Path remoteCacheDir;

    /**
     * Office 文件监听集合（workspace -> 监听标记）
     */
    private final Map<String, Boolean> officeWatchRegistry = new ConcurrentHashMap<>();

    /**
     * 快照状态集合（workspace -> 快照状态）
     */
    private final Map<String, SnapshotState> snapshotRegistry = new ConcurrentHashMap<>();

    /**
     * 确保文件系统根目录与远端缓存目录存在。
     *
     * @return 远端缓存目录
     */
    private Path ensureCacheDir() {
        try {
            Path root = Paths.get(fsRoot);
            if (!Files.exists(root)) {
                Files.createDirectories(root);
            }
            if (remoteCacheDir == null) {
                remoteCacheDir = root.resolve(".cache").resolve("remote");
                Files.createDirectories(remoteCacheDir);
            }
        } catch (IOException e) {
            log.warn("[fs] 初始化根目录失败: {}", e.getMessage());
        }
        return remoteCacheDir;
    }

    /**
     * 列出工作区下的扁平文件。
     *
     * @param body 请求体 {@code { root: string }}
     * @return 扁平文件列表（{@code name/full_path/relative_path}）
     */
    @PostMapping("/list")
    public ApiResult<List<Map<String, String>>> list(@RequestBody Map<String, Object> body) {
        String root = asText(body.get("root"));
        Path base = resolveRoot(root);
        List<Map<String, String>> result = new ArrayList<>();
        if (!Files.exists(base)) {
            return ApiResult.ok(result);
        }
        try (Stream<Path> stream = Files.list(base)) {
            List<Path> children = stream.sorted().toList();
            for (Path p : children) {
                String name = p.getFileName().toString();
                String rel = base.relativize(p).toString().replace("\\", "/");
                result.add(flatFile(name, base.resolve(rel).toString(), rel));
            }
        } catch (IOException e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "读取目录失败: " + e.getMessage());
        }
        return ApiResult.ok(result);
    }

    /**
     * 读取图片并以 base64 返回。
     *
     * @param body 请求体 {@code { path: string, workspace?: string }}
     * @return base64 字符串或 null
     */
    @PostMapping("/image-base64")
    public ApiResult<String> imageBase64(@RequestBody Map<String, Object> body) {
        Path file = resolvePath(body);
        if (!Files.exists(file) || Files.isDirectory(file)) {
            return ApiResult.ok(null);
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            return ApiResult.ok(Base64.getEncoder().encodeToString(bytes));
        } catch (IOException e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "读取图片失败: " + e.getMessage());
        }
    }

    /**
     * 抓取远端图片并落盘到缓存目录。
     *
     * @param body 请求体 {@code { url: string }}
     * @return 落盘后的绝对路径
     */
    @PostMapping("/fetch-remote-image")
    public ApiResult<String> fetchRemoteImage(@RequestBody Map<String, Object> body) {
        String url = asText(body.get("url"));
        if (url == null || url.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "url 不能为空");
        }
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "远端图片拉取失败: HTTP " + response.statusCode());
            }
            String fileName = url.substring(url.lastIndexOf('/') + 1);
            if (fileName.isBlank() || fileName.contains("?")) {
                fileName = "remote-" + System.currentTimeMillis();
            }
            Path target = ensureCacheDir().resolve(fileName);
            try (InputStream in = response.body()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return ApiResult.ok(target.toAbsolutePath().toString());
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "抓取远端图片失败: " + e.getMessage());
        }
    }

    /**
     * 读取文本文件内容。
     *
     * @param body 请求体 {@code { path: string, workspace?: string }}
     * @return 文件内容或 null
     */
    @PostMapping("/read")
    public ApiResult<String> read(@RequestBody Map<String, Object> body) {
        Path file = resolvePath(body);
        if (!Files.exists(file) || Files.isDirectory(file)) {
            return ApiResult.ok(null);
        }
        try {
            return ApiResult.ok(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "读取文件失败: " + e.getMessage());
        }
    }

    /**
     * 写入文本文件内容。
     *
     * @param body 请求体 {@code { path: string, data: string }}
     * @return 是否成功
     */
    @PostMapping("/write")
    public ApiResult<Boolean> write(@RequestBody Map<String, Object> body) {
        Path file = resolvePath(body);
        String data = body.get("data") == null ? "" : String.valueOf(body.get("data"));
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, data, StandardCharsets.UTF_8);
            return ApiResult.ok(true);
        } catch (IOException e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "写入文件失败: " + e.getMessage());
        }
    }

    /**
     * 将一组文件打包为 zip。
     *
     * @param body 请求体 {@code { path, request_id?, files: [{ name, content?, source_path? }] }}
     * @return 是否成功
     */
    @PostMapping("/zip")
    public ApiResult<Boolean> zip(@RequestBody Map<String, Object> body) {
        String path = asText(body.get("path"));
        if (path == null || path.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "path 不能为空");
        }
        Path target = resolveRoot(path).toAbsolutePath().normalize();
        Object filesObj = body.get("files");
        if (!(filesObj instanceof List)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "files 必须为数组");
        }
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(target))) {
                for (Object item : (List<?>) filesObj) {
                    if (!(item instanceof Map)) {
                        continue;
                    }
                    Map<?, ?> f = (Map<?, ?>) item;
                    String name = String.valueOf(f.get("name"));
                    if (name == null || name.isBlank()) {
                        continue;
                    }
                    ZipEntry entry = new ZipEntry(name);
                    zos.putNextEntry(entry);
                    if (f.get("content") != null) {
                        byte[] content = String.valueOf(f.get("content")).getBytes(StandardCharsets.UTF_8);
                        zos.write(content);
                    } else if (f.get("source_path") != null) {
                        Path src = resolveRoot(String.valueOf(f.get("source_path")));
                        if (Files.exists(src) && !Files.isDirectory(src)) {
                            zos.write(Files.readAllBytes(src));
                        }
                    }
                    zos.closeEntry();
                }
            }
            return ApiResult.ok(true);
        } catch (ApiException ex) {
            throw ex;
        } catch (IOException e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "打包 zip 失败: " + e.getMessage());
        }
    }

    /**
     * 取消进行中的 zip 任务（当前实现为占位，始终返回成功）。
     *
     * @param body 请求体 {@code { request_id: string }}
     * @return 是否成功
     */
    @PostMapping("/zip/cancel")
    public ApiResult<Boolean> zipCancel(@RequestBody Map<String, Object> body) {
        return ApiResult.ok(true);
    }

    /**
     * 读取文件元数据。
     *
     * @param body 请求体 {@code { path: string, workspace?: string }}
     * @return 文件元数据
     */
    @PostMapping("/metadata")
    public ApiResult<Map<String, Object>> metadata(@RequestBody Map<String, Object> body) {
        Path file = resolvePath(body);
        if (!Files.exists(file)) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "文件不存在: " + file);
        }
        try {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("name", file.getFileName().toString());
            meta.put("path", file.toAbsolutePath().toString());
            meta.put("size", Files.size(file));
            boolean isDir = Files.isDirectory(file);
            meta.put("type", isDir ? "directory" : "file");
            meta.put("is_directory", isDir);
            meta.put("last_modified", Files.getLastModifiedTime(file).toMillis());
            return ApiResult.ok(meta);
        } catch (IOException e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "读取元数据失败: " + e.getMessage());
        }
    }

    /**
     * 将文件复制到工作区。
     *
     * @param body 请求体 {@code { file_paths: string[], workspace: string, source_root?: string }}
     * @return 复制结果
     */
    @PostMapping("/copy")
    public ApiResult<Map<String, Object>> copy(@RequestBody Map<String, Object> body) {
        Object pathsObj = body.get("file_paths");
        String workspace = asText(body.get("workspace"));
        String sourceRoot = asText(body.get("source_root"));
        Path destRoot = resolveRoot(workspace);
        Path srcRoot = sourceRoot == null ? null : Paths.get(sourceRoot);
        List<String> copied = new ArrayList<>();
        List<Map<String, String>> failed = new ArrayList<>();
        if (pathsObj instanceof List) {
            for (Object p : (List<?>) pathsObj) {
                String src = String.valueOf(p);
                try {
                    Path source = srcRoot != null ? srcRoot.resolve(src) : Paths.get(src);
                    Path dest = destRoot.resolve(source.getFileName().toString());
                    Files.createDirectories(dest.getParent());
                    Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
                    copied.add(dest.toAbsolutePath().toString());
                } catch (IOException e) {
                    Map<String, String> f = new LinkedHashMap<>();
                    f.put("path", src);
                    f.put("error", e.getMessage());
                    failed.add(f);
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("copied_files", copied);
        result.put("failed_files", failed);
        return ApiResult.ok(result);
    }

    /**
     * 删除文件或目录。
     *
     * @param body 请求体 {@code { path: string }}
     * @return 成功信封
     */
    @PostMapping("/remove")
    public ApiResult<Void> remove(@RequestBody Map<String, Object> body) {
        Path file = resolvePath(body);
        if (!Files.exists(file)) {
            return ApiResult.ok();
        }
        try {
            deleteRecursively(file);
        } catch (IOException e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "删除失败: " + e.getMessage());
        }
        return ApiResult.ok();
    }

    /**
     * 重命名文件或目录。
     *
     * @param body 请求体 {@code { path: string, new_name: string }}
     * @return 新路径
     */
    @PostMapping("/rename")
    public ApiResult<Map<String, String>> rename(@RequestBody Map<String, Object> body) {
        Path file = resolvePath(body);
        String newName = asText(body.get("new_name"));
        if (newName == null || newName.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "new_name 不能为空");
        }
        if (!Files.exists(file)) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "文件不存在: " + file);
        }
        Path target = file.getParent().resolve(newName);
        try {
            Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "重命名失败: " + e.getMessage());
        }
        Map<String, String> result = new LinkedHashMap<>();
        result.put("new_path", target.toAbsolutePath().toString());
        return ApiResult.ok(result);
    }

    /**
     * 上传文件（multipart）。字段名：{@code file}、{@code file_name}(可选)、{@code conversation_id}(可选)。
     *
     * @param file           文件本体
     * @param fileName       可选文件名覆盖
     * @param conversationId 可选会话 ID
     * @return 落盘后的绝对路径字符串
     */
    @PostMapping("/upload")
    public ApiResult<String> upload(@RequestParam("file") MultipartFile file,
                                    @RequestParam(value = "file_name", required = false) String fileName,
                                    @RequestParam(value = "conversation_id", required = false) String conversationId) {
        if (file.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "上传文件为空");
        }
        String name = fileName != null && !fileName.isBlank() ? fileName : file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            name = "upload-" + System.currentTimeMillis();
        }
        try {
            Path dir = Paths.get(fsRoot);
            if (conversationId != null && !conversationId.isBlank()) {
                dir = dir.resolve("uploads").resolve(conversationId);
            } else {
                dir = dir.resolve("uploads").resolve("temp");
            }
            Files.createDirectories(dir);
            Path target = dir.resolve(name);
            file.transferTo(target.toFile());
            return ApiResult.ok(target.toAbsolutePath().toString());
        } catch (IOException e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "上传失败: " + e.getMessage());
        }
    }

    /**
     * 浏览目录（供设置页面目录选择器使用）。
     *
     * @param path      目录路径
     * @param showFiles 是否显示文件
     * @return 目录条目列表
     */
    @GetMapping("/browse")
    public ApiResult<Map<String, Object>> browse(
            @RequestParam(defaultValue = "") String path,
            @RequestParam(defaultValue = "false") boolean showFiles) {
        Path dir = path.isBlank() ? Paths.get(System.getProperty("user.home")) : Paths.get(path).toAbsolutePath().normalize();
        if (!Files.isDirectory(dir)) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "目录不存在: " + path);
        }
        List<Map<String, Object>> items = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream.sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                    .forEach(p -> {
                        boolean isDir = Files.isDirectory(p);
                        if (!showFiles && !isDir) {
                            return;
                        }
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("name", p.getFileName().toString());
                        item.put("path", p.toAbsolutePath().toString());
                        item.put("isDirectory", isDir);
                        item.put("isFile", !isDir);
                        items.add(item);
                    });
        } catch (Exception e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "浏览目录失败: " + e.getMessage());
        }
        Path parent = dir.getParent();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("currentPath", dir.toAbsolutePath().toString());
        result.put("canGoUp", parent != null);
        result.put("parentPath", parent != null ? parent.toAbsolutePath().toString() : null);
        result.put("isRoot", parent == null);
        return ApiResult.ok(result);
    }

    /**
     * 创建子目录（供设置页面目录选择器使用）。
     *
     * @param body 请求体 {@code { parentPath: string, name: string }}
     * @return 新建目录信息
     */
    @PostMapping("/directory")
    public ApiResult<Map<String, Object>> createDirectory(@RequestBody Map<String, Object> body) {
        String parentPath = String.valueOf(body.get("parentPath"));
        String name = String.valueOf(body.get("name"));
        if (parentPath.isBlank() || name.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "parentPath 和 name 不能为空");
        }
        Path parent = Paths.get(parentPath).toAbsolutePath().normalize();
        Path newDir = parent.resolve(name).normalize();
        try {
            Files.createDirectories(newDir);
        } catch (Exception e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "创建目录失败: " + e.getMessage());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("path", newDir.toAbsolutePath().toString());
        result.put("isDirectory", true);
        return ApiResult.ok(result);
    }

    /**
     * 开始监听工作区 Office 文件变更。
     *
     * @param body 请求体 {@code { workspace: string }}
     * @return 成功信封
     */
    @PostMapping("/office-watch/start")
    public ApiResult<Void> officeWatchStart(@RequestBody Map<String, Object> body) {
        String workspace = asText(body.get("workspace"));
        if (workspace == null || workspace.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "workspace 不能为空");
        }
        officeWatchRegistry.put(workspace, Boolean.TRUE);
        return ApiResult.ok();
    }

    /**
     * 停止监听工作区 Office 文件变更。
     *
     * @param body 请求体 {@code { workspace: string }}
     * @return 成功信封
     */
    @PostMapping("/office-watch/stop")
    public ApiResult<Void> officeWatchStop(@RequestBody Map<String, Object> body) {
        String workspace = asText(body.get("workspace"));
        if (workspace != null) {
            officeWatchRegistry.remove(workspace);
        }
        return ApiResult.ok();
    }

    /**
     * 初始化工作区快照。
     *
     * @param body 请求体 {@code { workspace: string }}
     * @return 快照信息
     */
    @PostMapping("/snapshot/init")
    public ApiResult<Map<String, Object>> snapshotInit(@RequestBody Map<String, Object> body) {
        String workspace = asText(body.get("workspace"));
        Path root = resolveRoot(workspace);
        SnapshotState state = new SnapshotState(root);
        state.captureBaseline();
        snapshotRegistry.put(root.toAbsolutePath().toString(), state);
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("mode", "snapshot");
        info.put("branch", null);
        return ApiResult.ok(info);
    }

    /**
     * 比较工作区与基线差异。
     *
     * @param body 请求体 {@code { workspace: string }}
     * @return 暂存/未暂存差异
     */
    @PostMapping("/snapshot/compare")
    public ApiResult<Map<String, Object>> snapshotCompare(@RequestBody Map<String, Object> body) {
        SnapshotState state = requireSnapshot(body);
        Map<String, Object> result = state.compare();
        return ApiResult.ok(result);
    }

    /**
     * 读取基线文件内容。
     *
     * @param body 请求体 {@code { workspace: string, file_path: string }}
     * @return 基线内容或 null
     */
    @PostMapping("/snapshot/baseline")
    public ApiResult<String> snapshotBaseline(@RequestBody Map<String, Object> body) {
        SnapshotState state = requireSnapshot(body);
        String filePath = asText(body.get("file_path"));
        if (filePath == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "file_path 不能为空");
        }
        return ApiResult.ok(state.baselineContent(filePath));
    }

    /**
     * 释放快照。
     *
     * @param body 请求体 {@code { workspace: string }}
     * @return 成功信封
     */
    @PostMapping("/snapshot/dispose")
    public ApiResult<Void> snapshotDispose(@RequestBody Map<String, Object> body) {
        String workspace = asText(body.get("workspace"));
        if (workspace != null) {
            snapshotRegistry.remove(resolveRoot(workspace).toAbsolutePath().toString());
        }
        return ApiResult.ok();
    }

    /**
     * 暂存单个文件。
     *
     * @param body 请求体 {@code { workspace: string, file_path: string }}
     * @return 成功信封
     */
    @PostMapping("/snapshot/stage")
    public ApiResult<Void> snapshotStage(@RequestBody Map<String, Object> body) {
        SnapshotState state = requireSnapshot(body);
        state.stage(asText(body.get("file_path")));
        return ApiResult.ok();
    }

    /**
     * 暂存全部变更文件。
     *
     * @param body 请求体 {@code { workspace: string }}
     * @return 成功信封
     */
    @PostMapping("/snapshot/stage-all")
    public ApiResult<Void> snapshotStageAll(@RequestBody Map<String, Object> body) {
        SnapshotState state = requireSnapshot(body);
        state.stageAll();
        return ApiResult.ok();
    }

    /**
     * 取消暂存单个文件。
     *
     * @param body 请求体 {@code { workspace: string, file_path: string }}
     * @return 成功信封
     */
    @PostMapping("/snapshot/unstage")
    public ApiResult<Void> snapshotUnstage(@RequestBody Map<String, Object> body) {
        SnapshotState state = requireSnapshot(body);
        state.unstage(asText(body.get("file_path")));
        return ApiResult.ok();
    }

    /**
     * 取消暂存全部文件。
     *
     * @param body 请求体 {@code { workspace: string }}
     * @return 成功信封
     */
    @PostMapping("/snapshot/unstage-all")
    public ApiResult<Void> snapshotUnstageAll(@RequestBody Map<String, Object> body) {
        SnapshotState state = requireSnapshot(body);
        state.unstageAll();
        return ApiResult.ok();
    }

    /**
     * 丢弃工作区文件变更（还原到基线）。
     *
     * @param body 请求体 {@code { workspace: string, file_path: string, operation: string }}
     * @return 成功信封
     */
    @PostMapping("/snapshot/discard")
    public ApiResult<Void> snapshotDiscard(@RequestBody Map<String, Object> body) {
        SnapshotState state = requireSnapshot(body);
        state.restore(asText(body.get("file_path")));
        return ApiResult.ok();
    }

    /**
     * 重置单个文件（取消暂存）。
     *
     * @param body 请求体 {@code { workspace: string, file_path: string, operation: string }}
     * @return 成功信封
     */
    @PostMapping("/snapshot/reset")
    public ApiResult<Void> snapshotReset(@RequestBody Map<String, Object> body) {
        SnapshotState state = requireSnapshot(body);
        state.unstage(asText(body.get("file_path")));
        return ApiResult.ok();
    }

    /**
     * 解析基准根目录。
     *
     * @param root 覆盖目录（可为 null）
     * @return 根路径
     */
    private Path resolveRoot(String root) {
        if (root != null && !root.isBlank()) {
            return Paths.get(root).toAbsolutePath().normalize();
        }
        return Paths.get(fsRoot).toAbsolutePath().normalize();
    }

    /**
     * 依据请求体解析目标文件（{@code path} + 可选 {@code workspace}）。
     *
     * @param body 请求体
     * @return 文件绝对路径
     */
    private Path resolvePath(Map<String, Object> body) {
        String path = asText(body.get("path"));
        if (path == null || path.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "path 不能为空");
        }
        Path p = Paths.get(path);
        if (!p.isAbsolute()) {
            String workspace = asText(body.get("workspace"));
            Path base = resolveRoot(workspace);
            p = base.resolve(path);
        }
        return p.toAbsolutePath().normalize();
    }

    /**
     * 获取或创建快照状态。
     *
     * @param body 请求体
     * @return 快照状态
     */
    private SnapshotState requireSnapshot(Map<String, Object> body) {
        String workspace = asText(body.get("workspace"));
        Path root = resolveRoot(workspace);
        SnapshotState state = snapshotRegistry.get(root.toAbsolutePath().toString());
        if (state == null) {
            state = new SnapshotState(root);
            state.captureBaseline();
            snapshotRegistry.put(root.toAbsolutePath().toString(), state);
        }
        return state;
    }

    /**
     * 递归删除。
     *
     * @param path 待删除路径
     * @throws IOException IO 异常
     */
    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (Stream<Path> stream = Files.walk(path)) {
                List<Path> all = stream.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList();
                for (Path p : all) {
                    Files.deleteIfExists(p);
                }
            }
        } else {
            Files.deleteIfExists(path);
        }
    }

    /**
     * 构造扁平文件映射。
     *
     * @param name         文件名
     * @param fullPath     绝对路径
     * @param relativePath 相对路径
     * @return 映射
     */
    private Map<String, String> flatFile(String name, String fullPath, String relativePath) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("name", name);
        map.put("full_path", fullPath);
        map.put("relative_path", relativePath);
        return map;
    }

    /**
     * 安全取文本。
     *
     * @param obj 对象
     * @return 文本或 null
     */
    private static String asText(Object obj) {
        return obj == null ? null : String.valueOf(obj);
    }

    /**
     * 工作区快照状态（内存实现）。
     *
     * <p>在 {@code init} 时捕获基线文件内容，按相对路径维护暂存集合，{@code compare} 时与工作区
     * 当前内容做 diff 得到未暂存变更，暂存集合单独输出为 staged 列表。</p>
     *
     * @since 4.0.0.42
     * @author CH
     */
    private static final class SnapshotState {

        /**
         * 工作区根目录
         */
        private final Path root;

        /**
         * 基线内容（相对路径 -> 内容）
         */
        private final Map<String, String> baseline = new ConcurrentHashMap<>();

        /**
         * 已暂存的相对路径集合
         */
        private final Map<String, Boolean> staged = new ConcurrentHashMap<>();

        /**
         * 构造快照状态。
         *
         * @param root 工作区根目录
         */
        SnapshotState(Path root) {
            this.root = root.toAbsolutePath().normalize();
        }

        /**
         * 捕获当前工作区全部文件内容为基线。
         */
        void captureBaseline() {
            baseline.clear();
            if (!Files.exists(root) || !Files.isDirectory(root)) {
                return;
            }
            try (Stream<Path> stream = Files.walk(root)) {
                for (Path p : stream.filter(Files::isRegularFile).toList()) {
                    String rel = root.relativize(p).toString().replace("\\", "/");
                    try {
                        baseline.put(rel, Files.readString(p, StandardCharsets.UTF_8));
                    } catch (IOException ignored) {
                        baseline.put(rel, "");
                    }
                }
            } catch (IOException ignored) {
                // 忽略遍历异常
            }
        }

        /**
         * 比较并返回差异。
         *
         * @return 含 staged/unstaged 的映射
         */
        Map<String, Object> compare() {
            List<Map<String, String>> stagedList = new ArrayList<>();
            List<Map<String, String>> unstagedList = new ArrayList<>();
            if (Files.exists(root) && Files.isDirectory(root)) {
                try (Stream<Path> stream = Files.walk(root)) {
                    for (Path p : stream.filter(Files::isRegularFile).toList()) {
                        String rel = root.relativize(p).toString().replace("\\", "/");
                        String current;
                        try {
                            current = Files.readString(p, StandardCharsets.UTF_8);
                        } catch (IOException e) {
                            current = "";
                        }
                        boolean changed = !baseline.containsKey(rel) || !baseline.get(rel).equals(current);
                        if (!changed) {
                            continue;
                        }
                        if (staged.containsKey(rel)) {
                            stagedList.add(change(rel, "modify"));
                        } else {
                            unstagedList.add(change(rel, baseline.containsKey(rel) ? "modify" : "create"));
                        }
                    }
                } catch (IOException ignored) {
                    // 忽略
                }
            }
            for (String rel : baseline.keySet()) {
                Path p = root.resolve(rel);
                if (!Files.exists(p) && !staged.containsKey(rel)) {
                    unstagedList.add(change(rel, "delete"));
                }
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("staged", stagedList);
            result.put("unstaged", unstagedList);
            return result;
        }

        /**
         * 读取基线内容。
         *
         * @param relPath 相对路径
         * @return 内容或 null
         */
        String baselineContent(String relPath) {
            if (relPath == null) {
                return null;
            }
            String rel = relPath.replace("\\", "/");
            return baseline.get(rel);
        }

        /**
         * 暂存单个文件。
         *
         * @param relPath 相对路径
         */
        void stage(String relPath) {
            if (relPath != null) {
                staged.put(relPath.replace("\\", "/"), Boolean.TRUE);
            }
        }

        /**
         * 暂存全部变更文件。
         */
        void stageAll() {
            Object unstaged = compare().get("unstaged");
            if (unstaged instanceof List) {
                for (Object item : (List<?>) unstaged) {
                    Map<?, ?> m = (Map<?, ?>) item;
                    staged.put(String.valueOf(m.get("file_path")), Boolean.TRUE);
                }
            }
        }

        /**
         * 取消暂存单个文件。
         *
         * @param relPath 相对路径
         */
        void unstage(String relPath) {
            if (relPath != null) {
                staged.remove(relPath.replace("\\", "/"));
            }
        }

        /**
         * 取消暂存全部文件。
         */
        void unstageAll() {
            staged.clear();
        }

        /**
         * 还原工作区文件到基线内容。
         *
         * @param relPath 相对路径
         */
        void restore(String relPath) {
            if (relPath == null) {
                return;
            }
            String rel = relPath.replace("\\", "/");
            Path target = root.resolve(rel);
            try {
                if (baseline.containsKey(rel)) {
                    Files.createDirectories(target.getParent());
                    Files.writeString(target, baseline.get(rel), StandardCharsets.UTF_8);
                } else {
                    Files.deleteIfExists(target);
                }
                staged.remove(rel);
            } catch (IOException e) {
                throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "还原文件失败: " + e.getMessage());
            }
        }

        /**
         * 构造单条变更记录。
         *
         * @param relPath    相对路径
         * @param operation  操作类型
         * @return 变更映射
         */
        private Map<String, String> change(String relPath, String operation) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("file_path", root.resolve(relPath).toAbsolutePath().toString());
            m.put("relative_path", relPath);
            m.put("operation", operation);
            return m;
        }
    }
}

package com.chua.starter.ai.support.controller;

import com.chua.starter.ai.support.service.WorkshopAssetService;
import com.chua.starter.common.support.annotations.Permission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 创意工坊资产库 REST 控制器。
 *
 * <p>提供 /api/workshop/assets 和 /api/workshop/collections/* 接口，
 * 支持图片/视频上传、文本资产创建、元数据编辑、批量集合重命名。</p>
 *
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/api/workshop")
@RequiredArgsConstructor
@Tag(name = "创意工坊资产库", description = "资产 CRUD、上传、集合管理")
public class WorkshopAssetController {

    private final WorkshopAssetService assetService;

    // ─── 资产列表 ───────────────────────────────────────────────────────────────

    @Operation(summary = "列出资产", description = "分页查询资产库，支持 kind/collection/search/tag/sort 过滤")
    @GetMapping("/assets")
    public Map<String, Object> listAssets(
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String collection,
            @RequestParam(required = false, defaultValue = "false") boolean ungrouped,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false, defaultValue = "true") Boolean inLibrary,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "40") int pageSize) {

        List<?> items = assetService.listAssets(kind, collection, ungrouped, q, tag, inLibrary, sort, page, pageSize);
        long total = assetService.countAssets(kind, collection, ungrouped, q, tag, inLibrary);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total);
        return result;
    }

    // ─── 创建文本资产 ───────────────────────────────────────────────────────────

    @Operation(summary = "创建文本资产", description = "注册一个纯文本资产到资产库")
    @PostMapping("/assets")
    public Map<String, Object> createTextAsset(@RequestBody Map<String, Object> body) {
        String title = (String) body.getOrDefault("title", "未命名文本");
        String content = (String) body.get("textContent");
        String collection = (String) body.get("collection");
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) body.getOrDefault("tags", List.of());
        var asset = assetService.createTextAsset(title, content, collection, tags);
        return wrapAsset(asset);
    }

    // ─── 上传二进制资产 ─────────────────────────────────────────────────────────

    @Operation(summary = "上传资产", description = "上传图片或视频文件，返回资产记录")
    @PostMapping(value = "/assets/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadAsset(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String collection,
            @RequestParam(required = false) String tags,
            @RequestParam(required = false, defaultValue = "true") Boolean inLibrary) throws IOException {

        List<String> tagList = (tags != null && !tags.isBlank())
                ? List.of(tags.split(",")) : List.of();
        var asset = assetService.uploadAsset(file, title, collection, tagList, inLibrary);
        return wrapAsset(asset);
    }

    // ─── 更新资产元数据 ─────────────────────────────────────────────────────────

    @Operation(summary = "更新资产", description = "部分更新资产标题/集合/标签/in_library")
    @PatchMapping("/assets/{assetId}")
    public Map<String, Object> patchAsset(@PathVariable String assetId, @RequestBody Map<String, Object> body) {
        String title = (String) body.get("title");
        Object collection = body.get("collection");
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) body.get("tags");
        Boolean inLibrary = (Boolean) body.get("in_library");
        var asset = assetService.patchAsset(assetId, title,
                collection instanceof String s ? s : null, tags, inLibrary);
        return wrapAsset(asset);
    }

    // ─── 删除资产 ───────────────────────────────────────────────────────────────

    @Operation(summary = "删除资产", description = "删除资产记录及物理文件")
    @DeleteMapping("/assets/{assetId}")
    public Map<String, Object> deleteAsset(@PathVariable String assetId) {
        assetService.deleteAsset(assetId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deleted", true);
        return result;
    }

    // ─── 集合列表 ───────────────────────────────────────────────────────────────

    @Operation(summary = "列出集合", description = "返回所有非空集合名称列表")
    @GetMapping("/collections")
    public Map<String, Object> listCollections() {
        List<String> collections = assetService.listCollections();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", collections);
        result.put("success", true);
        return result;
    }

    // ─── 集合操作 ───────────────────────────────────────────────────────────────

    @Operation(summary = "重命名集合", description = "批量更新使用指定集合的资产，返回更新行数")
    @PostMapping("/collections/rename")
    public Map<String, Object> renameCollection(@RequestBody Map<String, String> body) {
        String from = body.get("from");
        String to = body.get("to");
        int updated = assetService.renameCollection(from, to == null || to.isBlank() ? null : to);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updated", updated);
        return result;
    }

    // ─── 文件服务 ───────────────────────────────────────────────────────────────

    @Operation(summary = "获取资产文件", description = "按 asset_id 返回原始文件，支持 ?thumb=1 请求缩略图")
    @GetMapping("/files/{assetId}")
    public ResponseEntity<byte[]> getAssetFile(
            @PathVariable String assetId,
            @RequestParam(required = false) String thumb,
            HttpServletRequest request) throws IOException {

        var asset = assetService.getAsset(assetId);
        if (asset == null) {
            return ResponseEntity.notFound().build();
        }

        String filePath = "thumb".equals(thumb) && asset.getThumbPath() != null
                ? asset.getThumbPath() : asset.getFilePath();
        if (filePath == null || filePath.isBlank()) {
            return ResponseEntity.notFound().build();
        }

        Path fullPath = Path.of(System.getProperty("user.dir"), "data", "workshop-files", filePath);
        if (!Files.exists(fullPath)) {
            return ResponseEntity.notFound().build();
        }

        long fileLength = Files.size(fullPath);
        String rangeHeader = request.getHeader("Range");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "thumb".equals(thumb) && asset.getThumbPath() != null ? "image/jpeg"
                        : (asset.getMime() != null ? asset.getMime() : "application/octet-stream")));

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            String[] parts = rangeHeader.substring(6).split("-");
            try {
                long start, end;
                if (parts[0].isEmpty()) {
                    start = fileLength - Long.parseLong(parts[1]);
                    end = fileLength - 1;
                } else {
                    start = Long.parseLong(parts[0]);
                    end = parts.length > 1 && !parts[1].isEmpty() ? Long.parseLong(parts[1]) : fileLength - 1;
                }
                if (start < 0 || start >= fileLength || end < start || end >= fileLength) {
                    headers.set("Content-Range", "bytes */" + fileLength);
                    return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE).headers(headers).build();
                }
                byte[] fullBytes = Files.readAllBytes(fullPath);
                byte[] bytes = Arrays.copyOfRange(fullBytes, (int) start, (int) (end + 1));
                headers.setContentLength(bytes.length);
                headers.set("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
                return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).headers(headers).body(bytes);
            } catch (NumberFormatException e) {
                return ResponseEntity.badRequest().build();
            }
        }

        byte[] bytes = Files.readAllBytes(fullPath);
        headers.setContentLength(bytes.length);
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    private Map<String, Object> wrapAsset(Object asset) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", asset);
        result.put("success", true);
        return result;
    }
}

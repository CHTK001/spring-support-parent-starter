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

import com.chua.starter.ai.support.entity.WorkshopAsset;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

        WorkshopAssetService.AssetQueryRequest req = new WorkshopAssetService.AssetQueryRequest(
                kind, collection, ungrouped, q, tag, inLibrary, sort, page, pageSize);
        List<WorkshopAsset> items = assetService.listAssets(req);
        long total = assetService.countAssets(req);

        List<Map<String, Object>> contractItems = new ArrayList<>();
        for (WorkshopAsset asset : items) {
            contractItems.add(toContractMap(asset));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", contractItems);
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

    // ─── 画布 CRUD（内存实现，保持前端 /api/workshop/canvases 契约）────────────

    private final Map<String, Map<String, Object>> canvases = new ConcurrentHashMap<>();

    @Operation(summary = "列出画布", description = "返回画布列表，按 updated_at 倒序")
    @GetMapping("/canvases")
    public Map<String, Object> listCanvases() {
        List<Map<String, Object>> items = new ArrayList<>(canvases.values());
        items.sort(Comparator.comparingLong(
                (Map<String, Object> m) -> ((Number) m.getOrDefault("updated_at", 0L)).longValue()).reversed());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("canvases", items);
        return result;
    }

    @Operation(summary = "创建画布", description = "创建空白画布，返回其元数据")
    @PostMapping("/canvases")
    public Map<String, Object> createCanvas(@RequestBody(required = false) Map<String, Object> body) {
        String id = com.chua.common.support.utils.IdUtils.uuidv7();
        long now = System.currentTimeMillis();
        String title = body != null && body.get("title") instanceof String t && !t.isBlank()
                ? t : "未命名画布";
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("nodes", List.of());
        doc.put("edges", List.of());
        Map<String, Object> canvas = new LinkedHashMap<>();
        canvas.put("canvas_id", id);
        canvas.put("title", title);
        canvas.put("thumbnail_url", null);
        canvas.put("node_count", 0);
        canvas.put("created_at", now);
        canvas.put("updated_at", now);
        canvas.put("doc", doc);
        canvases.put(id, canvas);
        return canvasMeta(canvas);
    }

    @Operation(summary = "获取画布详情", description = "返回画布元数据与完整文档")
    @GetMapping("/canvases/{canvasId}")
    public Map<String, Object> getCanvas(@PathVariable String canvasId) {
        Map<String, Object> canvas = canvases.get(canvasId);
        if (canvas == null) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put("error", "画布不存在: " + canvasId);
            return err;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("meta", canvasMeta(canvas));
        result.put("doc", canvas.get("doc"));
        return result;
    }

    @Operation(summary = "保存画布文档", description = "原子写入画布文档，返回最新 updated_at")
    @PutMapping("/canvases/{canvasId}/doc")
    public Map<String, Object> putCanvasDoc(@PathVariable String canvasId, @RequestBody Map<String, Object> body) {
        Map<String, Object> canvas = canvases.get(canvasId);
        if (canvas == null) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put("error", "画布不存在: " + canvasId);
            return err;
        }
        Object docObj = body.get("doc");
        if (docObj instanceof Map<?, ?> docMap) {
            canvas.put("doc", new LinkedHashMap<>((Map<?, ?>) docMap));
        } else {
            canvas.put("doc", new LinkedHashMap<>());
        }
        Object nodes = canvas.get("doc") instanceof Map<?, ?> d ? d.get("nodes") : null;
        int nodeCount = nodes instanceof List<?> list ? list.size() : 0;
        canvas.put("node_count", nodeCount);
        long now = System.currentTimeMillis();
        canvas.put("updated_at", now);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updated_at", now);
        return result;
    }

    @Operation(summary = "重命名画布", description = "修改画布标题")
    @PatchMapping("/canvases/{canvasId}")
    public Map<String, Object> patchCanvas(@PathVariable String canvasId, @RequestBody Map<String, Object> body) {
        Map<String, Object> canvas = canvases.get(canvasId);
        if (canvas == null) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put("error", "画布不存在: " + canvasId);
            return err;
        }
        if (body.get("title") instanceof String t && !t.isBlank()) {
            canvas.put("title", t);
        }
        canvas.put("updated_at", System.currentTimeMillis());
        return canvasMeta(canvas);
    }

    @Operation(summary = "删除画布", description = "删除画布索引记录")
    @DeleteMapping("/canvases/{canvasId}")
    public Map<String, Object> deleteCanvas(@PathVariable String canvasId) {
        canvases.remove(canvasId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deleted", true);
        return result;
    }

    private Map<String, Object> canvasMeta(Map<String, Object> canvas) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("canvas_id", canvas.get("canvas_id"));
        meta.put("title", canvas.get("title"));
        meta.put("thumbnail_url", canvas.get("thumbnail_url"));
        meta.put("node_count", canvas.get("node_count"));
        meta.put("created_at", canvas.get("created_at"));
        meta.put("updated_at", canvas.get("updated_at"));
        return meta;
    }

    // ─── 文件服务 ───────────────────────────────────────────────────────────────

    @Operation(summary = "获取资产文件", description = "按 asset_id 返回原始文件，支持 ?thumb=1 请求缩略图")
    @GetMapping("/files/{assetId}")
    public org.springframework.http.ResponseEntity<byte[]> getAssetFile(
            @PathVariable String assetId,
            @RequestParam(required = false) String thumb) {

        var asset = assetService.getAsset(assetId);
        if (asset == null) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }

        String filePath = "thumb".equals(thumb) && asset.getThumbPath() != null
                ? asset.getThumbPath() : asset.getFilePath();
        if (filePath == null || filePath.isBlank()) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }

        Path fullPath = Path.of(assetService.getStorageDir(), filePath);
        if (!Files.exists(fullPath)) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }

        long fileLength;
        byte[] bytes;
        try {
            fileLength = Files.size(fullPath);
            bytes = Files.readAllBytes(fullPath);
        } catch (IOException e) {
            return org.springframework.http.ResponseEntity.status(500).build();
        }

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        String contentType = (asset.getMime() != null && !asset.getMime().isBlank())
                ? asset.getMime() : "application/octet-stream";
        headers.setContentType(org.springframework.http.MediaType.parseMediaType(contentType));
        headers.setContentLength(bytes.length);
        headers.set("Accept-Ranges", "bytes");

        return org.springframework.http.ResponseEntity.ok().headers(headers).body(bytes);
    }

    private Map<String, Object> wrapAsset(Object asset) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", asset instanceof WorkshopAsset wa ? toContractMap(wa) : asset);
        result.put("success", true);
        return result;
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 将实体转为前端 WorkshopAsset 契约结构：
     * tags/origin 从 JSON 字符串解析为数组/对象，并补充 url / thumb_url 字段。
     */
    private Map<String, Object> toContractMap(WorkshopAsset a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("asset_id", a.getAssetId());
        m.put("kind", a.getKind());
        m.put("title", a.getTitle());
        m.put("collection", a.getCollection());
        m.put("tags", parseJsonValue(a.getTags(), List.of()));
        m.put("mime", a.getMime());
        m.put("width", a.getWidth());
        m.put("height", a.getHeight());
        m.put("bytes", a.getBytes());
        m.put("in_library", Boolean.TRUE.equals(a.getInLibrary()));
        m.put("text_content", a.getTextContent());
        m.put("origin", parseJsonValue(a.getOrigin(), null));
        m.put("url", "/api/workshop/files/" + a.getAssetId());
        m.put("thumb_url", a.getThumbPath() != null && !a.getThumbPath().isBlank()
                ? "/api/workshop/files/" + a.getAssetId() + "?thumb=1" : null);
        m.put("created_at", a.getCreatedAt());
        m.put("updated_at", a.getUpdatedAt());
        return m;
    }

    /**
     * 解析 JSON 字符串为原生 List/Map；空或解析失败回退到 fallback。
     */
    private Object parseJsonValue(String raw, Object fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return OBJECT_MAPPER.readValue(raw, Object.class);
        } catch (Exception e) {
            log.warn("[WorkshopAsset] JSON 字段解析失败, 原值: {}", raw);
            return fallback;
        }
    }
}

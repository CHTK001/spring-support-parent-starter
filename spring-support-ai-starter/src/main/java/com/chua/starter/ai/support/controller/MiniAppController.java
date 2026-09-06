package com.chua.starter.ai.support.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 小程序库 REST 控制器。
 *
 * <p>提供 /api/miniapps 全量 CRUD、画布文档读写、发布、导入验证等接口。
 * 内部以内存 Map 为元数据层，文件系统（user.dir/miniapps/{id}/）为 HTML 存储层。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/miniapps")
@ApiReturnFormatIgnore
@Tag(name = "小程序库", description = "小程序列表/创建/更新/删除/发布/导入")
public class MiniAppController {

    private static final Path ROOT = Path.of(System.getProperty("user.dir"), "miniapps");
    private final Map<String, Map<String, Object>> apps = new ConcurrentHashMap<>();
    private final ObjectMapper om = new ObjectMapper();

    // ─── 集合接口 ───────────────────────────────────────────────────────────────

    @Operation(summary = "列出小程序", description = "返回所有已发布小程序的元数据列表")
    @GetMapping
    public Map<String, Object> list() {
        List<Map<String, Object>> items = new ArrayList<>(apps.values());
        items.sort(Comparator.comparingLong(
                (Map<String, Object> m) -> ((Number) m.get("updated_at")).longValue()).reversed());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", items);
        result.put("success", true);
        return result;
    }

    @Operation(summary = "创建小程序", description = "创建并首次发布，初始化工作目录")
    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        String name = optStr(body, "name", "未命名小程序");
        String description = optStr(body, "description", "");
        String icon = optStr(body, "icon", null);
        String html = optStr(body, "html", "<!DOCTYPE html><html><head><meta charset='utf-8'><title>" + escHtml(name) + "</title></head><body><h1>" + escHtml(name) + "</h1></body></html>");
        String sourceConversationId = optStr(body, "source_conversation_id", null);

        Path dir = ROOT.resolve(id);
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("miniapp.html"), html);
        } catch (IOException e) {
            log.error("创建小程序目录失败: id={}", id, e);
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put("error", "创建失败: " + e.getMessage());
            return err;
        }

        Map<String, Object> meta = buildMeta(id, name, description, icon, sourceConversationId, html, now, now);
        apps.put(id, meta);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", meta);
        result.put("success", true);
        return result;
    }

    // ─── 单体接口 ───────────────────────────────────────────────────────────────

    @Operation(summary = "获取小程序详情", description = "按 id 查询单个小程序元数据")
    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable String id) {
        Map<String, Object> app = apps.get(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", app != null ? app : null);
        result.put("success", true);
        return result;
    }

    @Operation(summary = "更新小程序", description = "部分更新名称/描述/图标/HTML")
    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Map<String, Object> app = apps.get(id);
        if (app == null) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put("error", "小程序不存在: " + id);
            return err;
        }
        long now = System.currentTimeMillis();
        if (body.get("name") instanceof String n && !n.isBlank()) app.put("name", n);
        if (body.get("description") instanceof String d) app.put("description", d);
        if (body.get("icon") instanceof String ic) app.put("icon", ic);
        if (body.get("html") instanceof String h) {
            Path dir = ROOT.resolve(id);
            try {
                Files.createDirectories(dir);
                Files.writeString(dir.resolve("miniapp.html"), h);
            } catch (IOException e) {
                log.error("写入 miniapp.html 失败: id={}", id, e);
            }
            app.put("html_size", (long) h.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        }
        app.put("updated_at", now);
        app.put("has_unpublished_changes", hasUnpublished(id, app));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", app);
        result.put("success", true);
        return result;
    }

    @Operation(summary = "删除小程序", description = "删除元数据及文件系统上的工作目录")
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        apps.remove(id);
        Path dir = ROOT.resolve(id);
        deleteRecursively(dir);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", true);
        result.put("success", true);
        return result;
    }

    // ─── 工作目录 / 发布 ─────────────────────────────────────────────────────────

    @Operation(summary = "分配工作目录", description = "确保 miniapps/{id}/ 存在并返回绝对路径")
    @PostMapping("/{id}/workspace")
    public Map<String, Object> provisionWorkspace(@PathVariable String id) {
        Path dir = ROOT.resolve(id);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put("error", e.getMessage());
            return err;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("source_path", dir.toAbsolutePath().resolve("miniapp.html").toString());
        result.put("data", data);
        result.put("success", true);
        return result;
    }

    @Operation(summary = "发布", description = "将工作目录中的 miniapp.html 提升为已发布快照")
    @PostMapping("/{id}/publish")
    public Map<String, Object> publish(@PathVariable String id) {
        Map<String, Object> app = apps.get(id);
        if (app == null) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put("error", "小程序不存在: " + id);
            return err;
        }
        Path dir = ROOT.resolve(id);
        Path src = dir.resolve("miniapp.html");
        if (!Files.exists(src)) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put("error", "没有工作副本可发布（缺少 miniapp.html）");
            return err;
        }
        long now = System.currentTimeMillis();
        try {
            byte[] bytes = Files.readAllBytes(src);
            app.put("published_snapshot", bytes);
            app.put("published_at", now);
            app.put("html_size", (long) bytes.length);
        } catch (IOException e) {
            log.error("读取 miniapp.html 失败: id={}", id, e);
        }
        app.put("updated_at", now);
        app.put("has_unpublished_changes", false);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", app);
        result.put("success", true);
        return result;
    }

    @Operation(summary = "服务已发布内容", description = "返回已发布的 HTML 内容，供 iframe 加载（无鉴权）")
    @GetMapping(value = "/{id}/serve", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<byte[]> serve(@PathVariable String id) {
        Path src = ROOT.resolve(id).resolve("miniapp.html");
        if (!Files.exists(src)) {
            byte[] html = "<!DOCTYPE html><html><head><title>未发布</title></head><body><p>该小程序尚未发布</p></body></html>".getBytes();
            return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE).body(html);
        }
        try {
            byte[] bytes = Files.readAllBytes(src);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE)
                    .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                    .body(bytes);
        } catch (IOException e) {
            log.error("读取 miniapp.html 失败: id={}", id, e);
            return ResponseEntity.status(500).body(("读取失败: " + e.getMessage()).getBytes());
        }
    }

    // ─── 导入 ───────────────────────────────────────────────────────────────────

    @Operation(summary = "校验导入", description = "检查候选小程序的有效性，不写入任何数据")
    @PostMapping("/validate")
    public Map<String, Object> validate(@RequestBody Map<String, Object> body) {
        return doImport(body, false);
    }

    @Operation(summary = "导入小程序", description = "将候选 HTML 或路径下的 index.html 导入为新小程序")
    @PostMapping("/import")
    public Map<String, Object> importApp(@RequestBody Map<String, Object> body) {
        return doImport(body, true);
    }

    private Map<String, Object> doImport(Map<String, Object> body, boolean adopt) {
        String html = optStr(body, "html", null);
        String path = optStr(body, "path", null);
        if ((html == null || html.isBlank()) && (path == null || path.isBlank())) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("report", reportOf(List.of(finding("fatal", "source_required", "需提供 html 或 path 之一"))));
            r.put("applied_fixes", List.of());
            return r;
        }
        String source;
        try {
            if (path != null && !path.isBlank()) {
                Path p = Path.of(path);
                if (Files.isDirectory(p)) p = p.resolve("index.html");
                if (!Files.exists(p)) throw new IllegalArgumentException("文件不存在: " + path);
                source = Files.readString(p);
            } else {
                source = html;
            }
        } catch (Exception e) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("report", reportOf(List.of(finding("fatal", "read_error", e.getMessage()))));
            r.put("applied_fixes", List.of());
            return r;
        }

        List<Map<String, Object>> findings = new ArrayList<>();
        if (source.contains("<iframe") && source.matches("(?s).*<iframe[^>]*src=[\"'][^\"']*://[^\"']*[\"'].*/>.*")) {
            findings.add(finding("fatal", "cross_origin_iframe", "禁止跨域 iframe 引用"));
        }
        if (source.contains("document.cookie")) {
            findings.add(finding("warning", "cookie_access", "检测到 cookie 访问，沙箱可能禁用"));
        }
        boolean blocked = findings.stream().anyMatch(f -> "fatal".equals(f.get("severity")));
        if (blocked) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("report", reportOf(findings));
            r.put("applied_fixes", List.of());
            return r;
        }

        String name = optStr(body, "name", null);
        if (name == null || name.isBlank()) {
            int start = source.indexOf("<title>");
            if (start >= 0) {
                int end = source.indexOf("</title>", start);
                if (end > start) name = source.substring(start + 7, end).trim();
            }
        }
        if (name == null || name.isBlank()) name = "导入的小程序";

        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        Path dir = ROOT.resolve(id);
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("miniapp.html"), source);
        } catch (IOException e) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("report", reportOf(findings));
            r.put("applied_fixes", List.of());
            return r;
        }

        Map<String, Object> app = buildMeta(id, name,
                optStr(body, "description", ""), optStr(body, "icon", null), null,
                source, now, now);
        apps.put(id, app);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("report", reportOf(findings));
        r.put("applied_fixes", List.of());
        r.put("app", app);
        return r;
    }

    // ─── 私有工具 ───────────────────────────────────────────────────────────────

    private Map<String, Object> buildMeta(String id, String name, String description,
                                          String icon, String sourceConversationId,
                                          String html, long createdAt, long updatedAt) {
        Map<String, Object> app = new LinkedHashMap<>();
        app.put("miniapp_id", id);
        app.put("name", name);
        app.put("description", description);
        app.put("icon", icon);
        app.put("source_conversation_id", sourceConversationId);
        app.put("html_size", (long) html.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        app.put("published_at", null);
        app.put("has_unpublished_changes", false);
        app.put("created_at", createdAt);
        app.put("updated_at", updatedAt);
        return app;
    }

    private boolean hasUnpublished(String id, Map<String, Object> app) {
        Object publishedAt = app.get("published_at");
        if (publishedAt == null) return false;
        Path src = ROOT.resolve(id).resolve("miniapp.html");
        if (!Files.exists(src)) return false;
        try {
            long mod = Files.getLastModifiedTime(src).toMillis();
            long pub = ((Number) publishedAt).longValue();
            return mod > pub;
        } catch (IOException e) {
            return false;
        }
    }

    private Map<String, Object> finding(String severity, String ruleId, String detail) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("severity", severity);
        f.put("rule_id", ruleId);
        f.put("detail", detail);
        return f;
    }

    private Map<String, Object> reportOf(List<Map<String, Object>> findings) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("findings", findings);
        r.put("blocked", findings.stream().anyMatch(f -> "fatal".equals(f.get("severity"))));
        return r;
    }

    private static String optStr(Map<String, Object> m, String k, String def) {
        Object v = m.get(k);
        return v instanceof String s && !s.isBlank() ? s : def;
    }

    private static String escHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static void deleteRecursively(Path p) {
        if (p == null) return;
        try (Stream<Path> s = Files.walk(p)) {
            s.sorted(Comparator.reverseOrder()).forEach(q -> {
                try { Files.deleteIfExists(q); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }
}

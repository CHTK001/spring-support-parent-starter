package com.chua.starter.ai.support.manage.api;

import com.chua.common.support.ai.rag.RagClient;
import com.chua.common.support.network.client.ClientResponse;
import com.chua.common.support.network.client.HttpClientFactory;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.starter.ai.support.configuration.rag.RagClientManager;
import com.chua.starter.ai.support.configuration.rag.RagProperties;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.manage.store.SysAiConversationStore;
import com.chua.starter.ai.support.manage.ApiWebSocketPushService;
import com.chua.starter.ai.support.registry.ChatClientRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.util.stream.Stream;

/**
 * 知识库命名空间控制器（替代 Rust 后端 nomicore 的 knowledge 契约）。
 *
 * <p>提供知识库（目录型 Markdown 文档集合）的元数据管理、文件/目录浏览、绑定、标签、
 * 检索配置与跨库搜索能力。元数据、标签、绑定与检索配置均使用进程内内存存储
 * （{@code ConcurrentHashMap}），文件/目录基于 {@code java.nio.file} 在 {@code root_path}
 * 下真实读写；AI 生成类接口复用 {@link ChatClientRegistry} 解析的 {@link ChatClient} 完成。</p>
 *
 * <p>所有响应统一包裹为 {@link ApiResult}；字段一律使用 snake_case（如 {@code knowledge_base_id}），
 * 严禁遗留 {@code id} 字段。知识变更通过 {@link ApiWebSocketPushService} 推送
 * {@code knowledge.base-created / base-updated / base-deleted / binding-changed / tag-changed} 事件。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
@Slf4j
public class ApiKnowledgeController {

    /**
     * 会话存储（复用其 newId 生成 UUID 主键）
     */
    private final SysAiConversationStore conversationStore;

    /**
     * WS 事件推送服务
     */
    private final ApiWebSocketPushService pushService;

    /**
     * 全局 RAG 客户端管理器（检索能力复用）
     */
    private final RagClientManager ragClientManager;

    /**
     * RAG 配置属性（提供上传目录与检索默认值）
     */
    private final RagProperties ragProperties;

    /**
     * 全局 ChatClient 注册中心（AI 生成类接口解析对话客户端）
     */
    private final ChatClientRegistry chatClientRegistry;

    /**
     * 知识库元数据表：knowledge_base_id → 元数据
     */
    private final Map<String, Map<String, Object>> bases = new ConcurrentHashMap<>();

    /**
     * 标签表：key → 标签
     */
    private final Map<String, Map<String, Object>> tags = new ConcurrentHashMap<>();

    /**
     * 绑定表："{kind}:{target_id}" → 绑定
     */
    private final Map<String, Map<String, Object>> bindings = new ConcurrentHashMap<>();

    /**
     * 检索配置（embedding / rerank 两阶段）
     */
    private final Map<String, Object> retrievalConfig = defaultRetrievalConfig();

    // ─────────────────────────────────────────────────────────────────────────
    // 知识库 CRUD
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/bases")
    public ApiResult<List<Map<String, Object>>> listBases() {
        List<Map<String, Object>> items = bases.values().stream()
                .map(this::toBaseResponse)
                .sorted(Comparator.comparing(
                        m -> ((Number) m.getOrDefault("created_at", 0L)).longValue(),
                        Comparator.reverseOrder()))
                .toList();
        return ApiResult.ok(items);
    }

    @PostMapping("/bases")
    public ApiResult<Map<String, Object>> createBase(@RequestBody Map<String, Object> body) {
        String name = (String) body.getOrDefault("name", "");
        if (StringUtils.isBlank(name)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "知识库名称不能为空");
        }
        String baseId = conversationStore.newId();
        String description = body.get("description") == null ? "" : String.valueOf(body.get("description"));
        String rootPath = body.get("root_path") == null ? null : String.valueOf(body.get("root_path")).trim();
        @SuppressWarnings("unchecked")
        List<String> reqTags = body.get("tags") instanceof List ? (List<String>) body.get("tags") : new ArrayList<>();
        Map<String, Object> source = body.get("source") instanceof Map ? uncheckedCast(body.get("source")) : null;

        boolean managed;
        String kind;
        if (source != null) {
            // URL 来源型知识库：托管目录 + 来源配置
            kind = "web";
            managed = true;
            rootPath = Paths.get(ragProperties.getUploadDir(), "kb-" + baseId).toString();
        } else if (StringUtils.hasText(rootPath)) {
            // 外部本地目录（非托管）
            kind = "local";
            managed = false;
        } else {
            // 空白托管知识库
            kind = "blank";
            managed = true;
            rootPath = Paths.get(ragProperties.getUploadDir(), "kb-" + baseId).toString();
        }

        Path root = Paths.get(rootPath);
        if (managed) {
            try {
                Files.createDirectories(root);
            } catch (IOException e) {
                throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "创建知识库目录失败: " + e.getMessage());
            }
        }

        long now = System.currentTimeMillis();
        Map<String, Object> base = new LinkedHashMap<>();
        base.put("knowledge_base_id", baseId);
        base.put("name", name);
        base.put("description", description);
        base.put("root_path", rootPath);
        base.put("managed", managed);
        base.put("created_at", now);
        base.put("updated_at", now);
        base.put("file_count", 0L);
        base.put("total_size", 0L);
        base.put("root_exists", Files.exists(root));
        base.put("tags", new ArrayList<>(reqTags));
        base.put("kind", kind);
        if (source != null) {
            // 规整来源配置，补齐 entries 默认空列表
            Map<String, Object> src = new LinkedHashMap<>(source);
            if (!(src.get("entries") instanceof List)) {
                src.put("entries", new ArrayList<>());
            }
            base.put("source", src);
        }

        refreshStats(base);
        bases.put(baseId, base);

        Map<String, Object> result = toBaseResponse(base);
        // 快照型来源在创建时即抓取
        if (source != null && "snapshot".equals(String.valueOf(source.get("mode")))) {
            @SuppressWarnings("unchecked")
            Map<String, Object> sum = fetchSource(uncheckedCast(base.get("source")), root);
            result.put("source_fetch", sum);
        }
        pushService.push("knowledge.base-created", toBaseResponse(base));
        return ApiResult.ok(result);
    }

    @GetMapping("/bases/{knowledge_base_id}")
    public ApiResult<Map<String, Object>> getBase(@PathVariable String knowledge_base_id) {
        Map<String, Object> base = requireBase(knowledge_base_id);
        return ApiResult.ok(toBaseResponse(base));
    }

    @PutMapping("/bases/{knowledge_base_id}")
    public ApiResult<Map<String, Object>> updateBase(@PathVariable String knowledge_base_id,
                                                     @RequestBody Map<String, Object> body) {
        Map<String, Object> base = requireBase(knowledge_base_id);
        if (body.containsKey("name")) {
            base.put("name", String.valueOf(body.get("name")));
        }
        if (body.containsKey("description")) {
            base.put("description", String.valueOf(body.get("description")));
        }
        if (body.containsKey("tags") && body.get("tags") instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> newTags = (List<String>) body.get("tags");
            base.put("tags", new ArrayList<>(newTags));
        }
        base.put("updated_at", System.currentTimeMillis());
        pushService.push("knowledge.base-updated", toBaseResponse(base));
        return ApiResult.ok(toBaseResponse(base));
    }

    @DeleteMapping("/bases/{knowledge_base_id}")
    public ApiResult<Void> deleteBase(@PathVariable String knowledge_base_id,
                                      @RequestParam(required = false) String purge) {
        Map<String, Object> base = requireBase(knowledge_base_id);
        boolean doPurge = purge != null && !"false".equalsIgnoreCase(purge.trim());
        if (doPurge && Boolean.TRUE.equals(base.get("managed"))) {
            Path root = Paths.get(String.valueOf(base.get("root_path")));
            deleteRecursively(root);
        }
        bases.remove(knowledge_base_id);
        // 清理引用该库的绑定
        bindings.values().removeIf(b -> {
            @SuppressWarnings("unchecked")
            List<String> kbIds = (List<String>) b.get("kb_ids");
            return kbIds != null && kbIds.contains(knowledge_base_id);
        });
        pushService.push("knowledge.base-deleted", Map.of("knowledge_base_id", knowledge_base_id));
        return ApiResult.ok();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 检索配置
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/retrieval")
    public ApiResult<Map<String, Object>> getRetrievalConfig() {
        return ApiResult.ok(new LinkedHashMap<>(retrievalConfig));
    }

    @PutMapping("/retrieval")
    public ApiResult<Map<String, Object>> setRetrievalConfig(@RequestBody Map<String, Object> body) {
        Map<String, Object> embedding = body.get("embedding") instanceof Map ? uncheckedCast(body.get("embedding")) : null;
        Map<String, Object> rerank = body.get("rerank") instanceof Map ? uncheckedCast(body.get("rerank")) : null;
        if (embedding == null || StringUtils.isBlank(String.valueOf(embedding.get("mode")))) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "embedding 配置缺失 mode");
        }
        if (rerank == null || StringUtils.isBlank(String.valueOf(rerank.get("mode")))) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "rerank 配置缺失 mode");
        }
        retrievalConfig.put("embedding", normalizeStage(embedding));
        retrievalConfig.put("rerank", normalizeStage(rerank));
        return ApiResult.ok(new LinkedHashMap<>(retrievalConfig));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AI 生成 / 润色
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/bases/{knowledge_base_id}/autogen")
    public ApiResult<Map<String, Object>> autogenBase(@PathVariable String knowledge_base_id,
                                                      @RequestBody Map<String, Object> body) {
        Map<String, Object> base = requireBase(knowledge_base_id);
        boolean overwriteReadme = Boolean.TRUE.equals(body.get("overwrite_readme"));
        String providerId = body.get("provider_id") == null ? null : String.valueOf(body.get("provider_id"));
        String model = body.get("model") == null ? null : String.valueOf(body.get("model"));

        Path root = resolveRoot(base);
        String context = gatherContext(root, 60);
        String prompt = "你是一名技术文档助手。请基于以下知识库的文件清单与片段，"
                + "为知识库《" + base.get("name") + "》生成一段不超过 120 字的中文简介，突出其主题与用途，不要使用 Markdown 标题。\n\n"
                + "文件清单与片段:\n" + context;
        String description = aiGenerate(prompt, providerId, model);
        if (StringUtils.isBlank(description)) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "AI 生成的简介为空");
        }

        base.put("description", description);
        base.put("updated_at", System.currentTimeMillis());

        boolean readmeWritten = false;
        Path readme = root.resolve("README.md");
        if (overwriteReadme || !Files.exists(readme)) {
            String readmeContent = "# " + base.get("name") + "\n\n" + description + "\n\n## 目录概览\n\n" + context;
            try {
                Files.write(readme, readmeContent.getBytes(StandardCharsets.UTF_8));
                readmeWritten = true;
            } catch (IOException e) {
                log.warn("[ApiKnowledgeController] 写入 README.md 失败: {}", e.getMessage());
            }
        }

        refreshStats(base);
        Map<String, Object> outcome = new LinkedHashMap<>();
        outcome.put("description", description);
        outcome.put("description_updated", true);
        outcome.put("readme_written", readmeWritten);
        outcome.put("base", toBaseResponse(base));
        pushService.push("knowledge.base-updated", toBaseResponse(base));
        return ApiResult.ok(outcome);
    }

    @PostMapping("/description/generate")
    public ApiResult<Map<String, Object>> generateDescription(@RequestBody Map<String, Object> body) {
        String rootPath = body.get("root_path") == null ? null : String.valueOf(body.get("root_path")).trim();
        if (StringUtils.isBlank(rootPath)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "root_path 不能为空");
        }
        Path root = Paths.get(rootPath);
        if (!Files.isDirectory(root)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "路径不是有效目录: " + rootPath);
        }
        String name = body.get("name") == null ? "" : String.valueOf(body.get("name"));
        String providerId = body.get("provider_id") == null ? null : String.valueOf(body.get("provider_id"));
        String model = body.get("model") == null ? null : String.valueOf(body.get("model"));
        String context = gatherContext(root, 60);
        String prompt = "你是一名技术文档助手。请基于以下目录的文件清单与片段，"
                + "为知识库《" + name + "》生成一段不超过 120 字的中文简介，突出其主题与用途，不要使用 Markdown 标题。\n\n"
                + "文件清单与片段:\n" + context;
        String description = aiGenerate(prompt, providerId, model);
        if (StringUtils.isBlank(description)) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "AI 生成的简介为空");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("description", description);
        return ApiResult.ok(result);
    }

    @PostMapping("/description/polish")
    public ApiResult<Map<String, Object>> polishDescription(@RequestBody Map<String, Object> body) {
        String draft = body.get("draft") == null ? "" : String.valueOf(body.get("draft"));
        if (StringUtils.isBlank(draft)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "draft 不能为空");
        }
        String name = body.get("name") == null ? "" : String.valueOf(body.get("name"));
        String providerId = body.get("provider_id") == null ? null : String.valueOf(body.get("provider_id"));
        String model = body.get("model") == null ? null : String.valueOf(body.get("model"));
        String prompt = "你是一名技术文档助手。请在不改变原意的前提下，润色以下知识库《" + name
                + "》的简介，使其更通顺、专业，不超过 120 字，不要使用 Markdown 标题：\n\n" + draft;
        String description = aiGenerate(prompt, providerId, model);
        if (StringUtils.isBlank(description)) {
            description = draft;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("description", description);
        return ApiResult.ok(result);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 来源（URL 抓取）
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/bases/{knowledge_base_id}/refresh-source")
    public ApiResult<Map<String, Object>> refreshSource(@PathVariable String knowledge_base_id) {
        Map<String, Object> base = requireBase(knowledge_base_id);
        Map<String, Object> source = base.get("source") instanceof Map ? uncheckedCast(base.get("source")) : null;
        if (source == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "该知识库未配置来源");
        }
        Path root = resolveRoot(base);
        Map<String, Object> summary = fetchSource(source, root);
        base.put("source", source);
        base.put("updated_at", System.currentTimeMillis());
        pushService.push("knowledge.base-updated", toBaseResponse(base));
        return ApiResult.ok(summary);
    }

    @PutMapping("/bases/{knowledge_base_id}/source")
    public ApiResult<Map<String, Object>> setSource(@PathVariable String knowledge_base_id,
                                                    @RequestBody Map<String, Object> body) {
        Map<String, Object> base = requireBase(knowledge_base_id);
        Object rawSource = body.get("source");
        if (rawSource == null) {
            base.remove("source");
        } else if (rawSource instanceof Map) {
            Map<String, Object> src = new LinkedHashMap<>(uncheckedCast(rawSource));
            if (!(src.get("entries") instanceof List)) {
                src.put("entries", new ArrayList<>());
            }
            base.put("source", src);
            if ("snapshot".equals(String.valueOf(src.get("mode")))) {
                fetchSource(src, resolveRoot(base));
            }
        } else {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "source 字段格式非法");
        }
        base.put("updated_at", System.currentTimeMillis());
        pushService.push("knowledge.base-updated", toBaseResponse(base));
        return ApiResult.ok(toBaseResponse(base));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 文件 / 目录浏览
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/bases/{knowledge_base_id}/files")
    public ApiResult<List<Map<String, Object>>> listFiles(@PathVariable String knowledge_base_id,
                                                          @RequestParam(required = false) String path) {
        Path root = resolveRoot(requireBase(knowledge_base_id));
        Path dir = safeResolve(root, path);
        if (!Files.isDirectory(dir)) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "目录不存在: " + path);
        }
        List<Map<String, Object>> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir, 1)) {
            for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                files.add(fileEntry(root, p));
            }
        } catch (IOException e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "列举文件失败: " + e.getMessage());
        }
        files.sort(Comparator.comparing(m -> String.valueOf(m.get("rel_path"))));
        return ApiResult.ok(files);
    }

    @GetMapping("/bases/{knowledge_base_id}/tree")
    public ApiResult<List<Map<String, Object>>> listTree(@PathVariable String knowledge_base_id,
                                                         @RequestParam(required = false) String path) {
        Path root = resolveRoot(requireBase(knowledge_base_id));
        Path dir = safeResolve(root, path);
        if (!Files.isDirectory(dir)) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "目录不存在: " + path);
        }
        List<Map<String, Object>> tree = buildTree(root, dir, 4);
        tree.sort(Comparator.<Map<String, Object>, Boolean>comparing(m -> Boolean.TRUE.equals(m.get("is_dir")))
                .thenComparing(m -> String.valueOf(m.get("name"))));
        return ApiResult.ok(tree);
    }

    @PostMapping("/bases/{knowledge_base_id}/folder")
    public ApiResult<Map<String, Object>> createFolder(@PathVariable String knowledge_base_id,
                                                       @RequestBody Map<String, Object> body) {
        String rel = body.get("path") == null ? null : String.valueOf(body.get("path")).trim();
        if (StringUtils.isBlank(rel)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "path 不能为空");
        }
        Path root = resolveRoot(requireBase(knowledge_base_id));
        Path folder = safeResolve(root, rel);
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "创建目录失败: " + e.getMessage());
        }
        Map<String, Object> base = requireBase(knowledge_base_id);
        refreshStats(base);
        pushService.push("knowledge.base-updated", toBaseResponse(base));
        return ApiResult.ok(treeEntry(root, folder));
    }

    @DeleteMapping("/bases/{knowledge_base_id}/folder")
    public ApiResult<Void> deleteFolder(@PathVariable String knowledge_base_id,
                                        @RequestParam String path) {
        Path root = resolveRoot(requireBase(knowledge_base_id));
        Path folder = safeResolve(root, path);
        if (!Files.isDirectory(folder)) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "目录不存在: " + path);
        }
        deleteRecursively(folder);
        Map<String, Object> base = requireBase(knowledge_base_id);
        refreshStats(base);
        pushService.push("knowledge.base-updated", toBaseResponse(base));
        return ApiResult.ok();
    }

    @PostMapping("/bases/{knowledge_base_id}/tree/rename")
    public ApiResult<Map<String, Object>> renameTreeEntry(@PathVariable String knowledge_base_id,
                                                         @RequestBody Map<String, Object> body) {
        String rel = body.get("path") == null ? null : String.valueOf(body.get("path")).trim();
        String newName = body.get("new_name") == null ? null : String.valueOf(body.get("new_name")).trim();
        if (StringUtils.isBlank(rel) || StringUtils.isBlank(newName)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "path 与 new_name 均不能为空");
        }
        if (newName.contains("/") || newName.contains("\\")) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "新名称不能包含路径分隔符");
        }
        Path root = resolveRoot(requireBase(knowledge_base_id));
        Path target = safeResolve(root, rel);
        Path parent = target.getParent();
        if (parent == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "不能重命名根目录");
        }
        Path renamed = parent.resolve(newName);
        try {
            Files.move(target, renamed);
        } catch (IOException e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "重命名失败: " + e.getMessage());
        }
        Map<String, Object> base = requireBase(knowledge_base_id);
        refreshStats(base);
        pushService.push("knowledge.base-updated", toBaseResponse(base));
        return ApiResult.ok(treeEntry(root, renamed));
    }

    @GetMapping("/bases/{knowledge_base_id}/file")
    public ApiResult<Map<String, Object>> readFile(@PathVariable String knowledge_base_id,
                                                   @RequestParam String path) {
        Path root = resolveRoot(requireBase(knowledge_base_id));
        Path file = safeResolve(root, path);
        if (!Files.isRegularFile(file)) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "文件不存在: " + path);
        }
        if (!isTextFile(file)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "仅支持读取文本文件");
        }
        String content;
        try {
            content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "读取文件失败: " + e.getMessage());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rel_path", root.relativize(file).toString().replace('\\', '/'));
        result.put("content", content);
        result.put("size", fileSize(file));
        result.put("modified_at", lastModified(file));
        return ApiResult.ok(result);
    }

    @PutMapping("/bases/{knowledge_base_id}/file")
    public ApiResult<Void> writeFile(@PathVariable String knowledge_base_id,
                                     @RequestBody Map<String, Object> body) {
        String rel = body.get("path") == null ? null : String.valueOf(body.get("path")).trim();
        if (StringUtils.isBlank(rel)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "path 不能为空");
        }
        Object rawContent = body.get("content");
        String content = rawContent == null ? "" : String.valueOf(rawContent);
        Map<String, Object> base = requireBase(knowledge_base_id);
        Path root = resolveRoot(base);
        Path file = safeResolve(root, rel);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "写入文件失败: " + e.getMessage());
        }
        indexKnowledgeFile(base, rel, content);
        refreshStats(base);
        pushService.push("knowledge.base-updated", toBaseResponse(base));
        return ApiResult.ok();
    }

    @DeleteMapping("/bases/{knowledge_base_id}/file")
    public ApiResult<Void> deleteFile(@PathVariable String knowledge_base_id,
                                      @RequestParam String path) {
        Path root = resolveRoot(requireBase(knowledge_base_id));
        Path file = safeResolve(root, path);
        if (!Files.isRegularFile(file)) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "文件不存在: " + path);
        }
        try {
            Files.delete(file);
        } catch (IOException e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "删除文件失败: " + e.getMessage());
        }
        Map<String, Object> base = requireBase(knowledge_base_id);
        refreshStats(base);
        pushService.push("knowledge.base-updated", toBaseResponse(base));
        return ApiResult.ok();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 绑定
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/binding/{kind}/{target_id}")
    public ApiResult<Map<String, Object>> getBinding(@PathVariable String kind,
                                                     @PathVariable String target_id) {
        return ApiResult.ok(requireBinding(kind, target_id));
    }

    @PostMapping("/binding/{kind}/{target_id}")
    public ApiResult<Map<String, Object>> setBinding(@PathVariable String kind,
                                                     @PathVariable String target_id,
                                                     @RequestBody Map<String, Object> body) {
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("target_kind", kind);
        binding.put("target_id", target_id);
        binding.put("enabled", Boolean.TRUE.equals(body.get("enabled")));
        binding.put("writeback", Boolean.TRUE.equals(body.get("writeback")));
        binding.put("writeback_eagerness",
                body.get("writeback_eagerness") == null ? "manual" : String.valueOf(body.get("writeback_eagerness")));
        binding.put("channel_write_enabled", Boolean.TRUE.equals(body.get("channel_write_enabled")));
        binding.put("kb_ids", body.get("kb_ids") instanceof List
                ? new ArrayList<>((List<?>) body.get("kb_ids")) : new ArrayList<>());
        bindings.put(bindingKey(kind, target_id), binding);
        pushService.push("knowledge.binding-changed", new LinkedHashMap<>(binding));
        return ApiResult.ok(binding);
    }

    @GetMapping("/bases/{knowledge_base_id}/consumers")
    public ApiResult<List<Map<String, Object>>> listConsumers(@PathVariable String knowledge_base_id) {
        List<Map<String, Object>> consumers = new ArrayList<>();
        for (Map<String, Object> b : bindings.values()) {
            @SuppressWarnings("unchecked")
            List<String> kbIds = (List<String>) b.get("kb_ids");
            if (kbIds != null && kbIds.contains(knowledge_base_id)) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("target_kind", b.get("target_kind"));
                c.put("target_id", b.get("target_id"));
                c.put("enabled", b.get("enabled"));
                consumers.add(c);
            }
        }
        return ApiResult.ok(consumers);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 导入 / 导出
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/bases/{knowledge_base_id}/export")
    public ApiResult<Map<String, Object>> exportBase(@PathVariable String knowledge_base_id,
                                                     @RequestBody Map<String, Object> body) {
        Map<String, Object> base = requireBase(knowledge_base_id);
        String destPath = body.get("dest_path") == null ? null : String.valueOf(body.get("dest_path")).trim();
        if (StringUtils.isBlank(destPath)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "dest_path 不能为空");
        }
        Path root = resolveRoot(base);
        Path dest = Paths.get(destPath);
        try {
            Files.createDirectories(dest.getParent());
            zipDirectory(root, dest);
        } catch (IOException e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "导出失败: " + e.getMessage());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dest_path", dest.toString());
        return ApiResult.ok(result);
    }

    @PostMapping("/bases/import")
    public ApiResult<Map<String, Object>> importBase(@RequestBody Map<String, Object> body) {
        String srcPath = body.get("src_path") == null ? null : String.valueOf(body.get("src_path")).trim();
        if (StringUtils.isBlank(srcPath)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "src_path 不能为空");
        }
        Path src = Paths.get(srcPath);
        if (!Files.isRegularFile(src)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "源包不存在: " + srcPath);
        }
        String baseId = conversationStore.newId();
        Path root = Paths.get(ragProperties.getUploadDir(), "kb-" + baseId);
        try {
            Files.createDirectories(root);
            unzip(src, root);
        } catch (IOException e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "导入解压失败: " + e.getMessage());
        }
        long now = System.currentTimeMillis();
        Map<String, Object> base = new LinkedHashMap<>();
        base.put("knowledge_base_id", baseId);
        base.put("name", "导入知识库");
        base.put("description", "");
        base.put("root_path", root.toString());
        base.put("managed", true);
        base.put("created_at", now);
        base.put("updated_at", now);
        base.put("tags", new ArrayList<>());
        base.put("kind", "local");
        refreshStats(base);
        bases.put(baseId, base);
        pushService.push("knowledge.base-created", toBaseResponse(base));
        return ApiResult.ok(toBaseResponse(base));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 标签
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/tags")
    public ApiResult<List<Map<String, Object>>> listTags() {
        List<Map<String, Object>> items = tags.values().stream()
                .sorted(Comparator.comparing(
                        m -> ((Number) m.getOrDefault("sortOrder", 0)).intValue()))
                .toList();
        return ApiResult.ok(items);
    }

    @PostMapping("/tags")
    public ApiResult<Map<String, Object>> createTag(@RequestBody Map<String, Object> body) {
        String label = body.get("label") == null ? null : String.valueOf(body.get("label")).trim();
        if (StringUtils.isBlank(label)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "标签名称不能为空");
        }
        String key = "tag-" + conversationStore.newId();
        int maxOrder = tags.values().stream()
                .mapToInt(t -> ((Number) t.getOrDefault("sortOrder", 0)).intValue()).max().orElse(0);
        Map<String, Object> tag = new LinkedHashMap<>();
        tag.put("key", key);
        tag.put("label", label);
        tag.put("color", body.get("color"));
        tag.put("sortOrder", maxOrder + 1);
        tags.put(key, tag);
        pushService.push("knowledge.tag-changed", Map.of());
        return ApiResult.ok(tag);
    }

    @PutMapping("/tags/{key}")
    public ApiResult<Void> updateTag(@PathVariable String key,
                                     @RequestBody Map<String, Object> body) {
        Map<String, Object> tag = tags.get(key);
        if (tag == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "标签不存在: " + key);
        }
        if (body.containsKey("label")) {
            tag.put("label", String.valueOf(body.get("label")));
        }
        if (body.containsKey("color")) {
            tag.put("color", body.get("color"));
        }
        if (body.containsKey("sortOrder")) {
            tag.put("sortOrder", ((Number) body.get("sortOrder")).intValue());
        }
        pushService.push("knowledge.tag-changed", Map.of());
        return ApiResult.ok();
    }

    @DeleteMapping("/tags/{key}")
    public ApiResult<Void> deleteTag(@PathVariable String key) {
        if (tags.remove(key) == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "标签不存在: " + key);
        }
        // 从所有知识库标签列表中移除
        for (Map<String, Object> base : bases.values()) {
            @SuppressWarnings("unchecked")
            List<String> baseTags = (List<String>) base.get("tags");
            if (baseTags != null) {
                baseTags.remove(key);
            }
        }
        pushService.push("knowledge.tag-changed", Map.of());
        return ApiResult.ok();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 跨库搜索（关键字匹配）
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/search")
    public ApiResult<List<Map<String, Object>>> search(@RequestBody Map<String, Object> body) {
        String query = body.get("query") == null ? "" : String.valueOf(body.get("query"));
        if (StringUtils.isBlank(query)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "query 不能为空");
        }
        int limit = body.get("limit") == null ? 20 : ((Number) body.get("limit")).intValue();
        @SuppressWarnings("unchecked")
        List<String> kbIds = body.get("kbIds") instanceof List ? (List<String>) body.get("kbIds") : new ArrayList<>();

        List<Map<String, Object>> scope = new ArrayList<>();
        if (kbIds.isEmpty()) {
            scope.addAll(bases.values());
        } else {
            for (String id : kbIds) {
                Map<String, Object> b = bases.get(id);
                if (b != null) {
                    scope.add(b);
                }
            }
        }

        List<Map<String, Object>> hits = new ArrayList<>();
        for (Map<String, Object> base : scope) {
            hits.addAll(searchInBase(base, query));
        }
        hits.sort(Comparator.comparing(m -> ((Number) m.get("score")).doubleValue(), Comparator.reverseOrder()));
        if (hits.size() > limit) {
            hits = hits.subList(0, limit);
        }
        return ApiResult.ok(hits);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 私有辅助方法
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 获取知识库元数据（不存在抛 NOT_FOUND）。
     */
    private Map<String, Object> requireBase(String knowledgeBaseId) {
        Map<String, Object> base = bases.get(knowledgeBaseId);
        if (base == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "知识库不存在: " + knowledgeBaseId);
        }
        return base;
    }

    /**
     * 解析知识库根目录（目录缺失抛 NOT_FOUND）。
     */
    private Path resolveRoot(Map<String, Object> base) {
        Path root = Paths.get(String.valueOf(base.get("root_path")));
        if (!Files.exists(root)) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "知识库目录不存在: " + root);
        }
        return root;
    }

    /**
     * 在根目录下安全解析相对路径，防止路径穿越。
     */
    private Path safeResolve(Path root, String rel) {
        Path normalizedRoot = root.normalize();
        Path resolved = rel == null || rel.isBlank()
                ? normalizedRoot
                : root.resolve(rel.replace('\\', '/')).normalize();
        if (!resolved.startsWith(normalizedRoot)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "非法路径: " + rel);
        }
        return resolved;
    }

    /**
     * 重算文件数量、总大小与目录存在性，并写回元数据。
     */
    private void refreshStats(Map<String, Object> base) {
        Path root = Paths.get(String.valueOf(base.get("root_path")));
        long[] stats = computeStats(root);
        base.put("file_count", stats[0]);
        base.put("total_size", stats[1]);
        base.put("root_exists", Files.exists(root));
    }

    /**
     * 遍历目录统计文件数与总字节数。
     */
    private long[] computeStats(Path root) {
        long[] s = {0L, 0L};
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            return s;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                s[0]++;
                try {
                    s[1] += Files.size(p);
                } catch (IOException ignored) {
                    // 单文件大小读取失败不影响整体统计
                }
            }
        } catch (IOException ignored) {
            // 目录遍历失败返回已统计部分
        }
        return s;
    }

    /**
     * 转换为前端响应形态（补充实时统计字段）。
     */
    private Map<String, Object> toBaseResponse(Map<String, Object> base) {
        return new LinkedHashMap<>(base);
    }

    /**
     * 默认检索配置（embedding 本地、rerank 关闭）。
     */
    private static Map<String, Object> defaultRetrievalConfig() {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> emb = new LinkedHashMap<>();
        emb.put("mode", "local");
        Map<String, Object> re = new LinkedHashMap<>();
        re.put("mode", "none");
        m.put("embedding", emb);
        m.put("rerank", re);
        return m;
    }

    /**
     * 规整检索阶段配置（保留 mode / provider_id / model）。
     */
    private Map<String, Object> normalizeStage(Map<String, Object> stage) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mode", String.valueOf(stage.get("mode")));
        if (stage.get("provider_id") != null) {
            out.put("provider_id", String.valueOf(stage.get("provider_id")));
        }
        if (stage.get("model") != null) {
            out.put("model", String.valueOf(stage.get("model")));
        }
        return out;
    }

    /**
     * 抓取 URL 来源条目到 snapshots/ 目录，返回抓取汇总。
     */
    private Map<String, Object> fetchSource(Map<String, Object> source, Path root) {
        Object rawEntries = source.get("entries");
        List<?> entries = rawEntries instanceof List ? (List<?>) rawEntries : new ArrayList<>();
        int fetched = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        Path snapDir = root.resolve("snapshots");
        try {
            Files.createDirectories(snapDir);
        } catch (IOException e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "创建快照目录失败: " + e.getMessage());
        }
        int i = 0;
        for (Object o : entries) {
            if (!(o instanceof Map)) {
                continue;
            }
            Map<String, Object> e = uncheckedCast(o);
            String url = e.get("url") == null ? null : String.valueOf(e.get("url"));
            String title = e.get("title") == null ? null : String.valueOf(e.get("title"));
            if (StringUtils.isBlank(url)) {
                failed++;
                errors.add("条目缺少 url");
                continue;
            }
            try {
                ClientResponse resp = HttpClientFactory.getClient().get(url);
                if (resp == null || !resp.isSuccess()) {
                    failed++;
                    errors.add(url + ": HTTP 抓取失败");
                    continue;
                }
                byte[] bodyBytes = resp.getBody();
                if (bodyBytes == null || bodyBytes.length == 0) {
                    failed++;
                    errors.add(url + ": 内容为空");
                    continue;
                }
                String text = new String(bodyBytes, StandardCharsets.UTF_8)
                        .replaceAll("<[^>]+>", " ")
                        .replaceAll("\\s+", " ")
                        .trim();
                String name = (title != null && !title.isBlank()) ? title : url.substring(url.lastIndexOf('/') + 1);
                if (!name.contains(".")) {
                    name = "page_" + i + ".md";
                }
                Files.write(snapDir.resolve(sanitize(name)), text.getBytes(StandardCharsets.UTF_8));
                fetched++;
            } catch (Exception ex) {
                failed++;
                errors.add(url + ": " + ex.getMessage());
            }
            i++;
        }
        source.put("lastFetchedAt", System.currentTimeMillis());
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("fetched", fetched);
        summary.put("failed", failed);
        summary.put("errors", errors);
        summary.put("last_fetched_at", source.get("lastFetchedAt"));
        return summary;
    }

    /**
     * 文件名净化，去除非法字符与路径分隔符。
     */
    private String sanitize(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    /**
     * 收集目录上下文（文件名清单 + 前几个文件片段）用于 AI 生成。
     */
    private String gatherContext(Path root, int maxFiles) {
        if (!Files.exists(root)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int count = 0;
        try (Stream<Path> walk = Files.walk(root, 3)) {
            for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile).limit(maxFiles)::iterator) {
                String rel = root.relativize(p).toString().replace('\\', '/');
                sb.append("- ").append(rel).append("\n");
                if (count < 3 && isTextFile(p)) {
                    try {
                        String content = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                        if (content.length() > 300) {
                            content = content.substring(0, 300);
                        }
                        sb.append("  ").append(content.replace("\n", " ").trim()).append("\n");
                    } catch (IOException ignored) {
                        // 读取片段失败忽略
                    }
                }
                count++;
            }
        } catch (IOException ignored) {
            // 遍历失败忽略
        }
        return sb.toString();
    }

    /**
     * 调用 AI 对话客户端生成文本（优先指定 provider/model，否则默认客户端）。
     */
    private String aiGenerate(String prompt, String providerId, String model) {
        ChatClient client = resolveClient(providerId, model);
        if (client == null) {
            throw new ApiException(ApiErrorCode.NO_COMPLETER, "未配置可用的 AI 对话服务");
        }
        try {
            String reply = client.chatSync(prompt);
            return reply == null ? "" : reply.trim();
        } catch (Exception e) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "AI 生成失败: " + e.getMessage());
        }
    }

    /**
     * 按 provider / model 解析 ChatClient。
     */
    private ChatClient resolveClient(String providerId, String model) {
        if (StringUtils.hasText(providerId) && chatClientRegistry.getByProvider(providerId) != null) {
            return chatClientRegistry.getByProvider(providerId);
        }
        if (StringUtils.hasText(model) && chatClientRegistry.getByModel(model) != null) {
            return chatClientRegistry.getByModel(model);
        }
        return chatClientRegistry.getDefault();
    }

    /**
     * 构造单文件条目。
     */
    private Map<String, Object> fileEntry(Path root, Path file) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rel_path", root.relativize(file).toString().replace('\\', '/'));
        m.put("size", fileSize(file));
        m.put("modified_at", lastModified(file));
        return m;
    }

    /**
     * 构造目录树条目（含子目录递归）。
     */
    private Map<String, Object> treeEntry(Path root, Path entry) {
        boolean isDir = Files.isDirectory(entry);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", entry.getFileName() == null ? "" : entry.getFileName().toString());
        m.put("rel_path", root.relativize(entry).toString().replace('\\', '/'));
        m.put("is_dir", isDir);
        m.put("is_file", !isDir);
        m.put("size", isDir ? null : fileSize(entry));
        m.put("modified_at", lastModified(entry));
        return m;
    }

    /**
     * 递归构建目录树（限制深度）。目录条目携带 children。
     */
    private List<Map<String, Object>> buildTree(Path root, Path dir, int depth) {
        List<Map<String, Object>> entries = new ArrayList<>();
        try (Stream<Path> list = Files.list(dir)) {
            for (Path p : (Iterable<Path>) list.sorted()::iterator) {
                Map<String, Object> e = treeEntry(root, p);
                if (Files.isDirectory(p) && depth > 0) {
                    e.put("children", buildTree(root, p, depth - 1));
                }
                entries.add(e);
            }
        } catch (IOException ignored) {
            // 目录列举失败返回已收集部分
        }
        return entries;
    }

    /**
     * 将知识库文件最佳努力上传至全局 RAG 客户端（用于后续语义检索），失败静默忽略。
     */
    private void indexKnowledgeFile(Map<String, Object> base, String relPath, String content) {
        try {
            RagClient client = ragClientManager.current();
            if (client == null) {
                return;
            }
            client.uploadDocument(String.valueOf(base.get("knowledge_base_id")) + "/" + relPath,
                    content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.debug("[ApiKnowledgeController] RAG 索引文件失败(忽略): {}", e.getMessage());
        }
    }

    /**
     * 递归删除目录。
     */
    private void deleteRecursively(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            List<Path> all = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path p : all) {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // 单条目删除失败忽略
                }
            }
        } catch (IOException ignored) {
            // 遍历失败忽略
        }
    }

    /**
     * 判断是否为可读取文本文件（按扩展名粗筛）。
     */
    private boolean isTextFile(Path file) {
        String name = file.getFileName() == null ? "" : file.getFileName().toString().toLowerCase();
        return name.endsWith(".md") || name.endsWith(".txt") || name.endsWith(".markdown")
                || name.endsWith(".json") || name.endsWith(".csv") || name.endsWith(".log")
                || name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".toml")
                || name.endsWith(".java") || name.endsWith(".kt") || name.endsWith(".py")
                || name.endsWith(".js") || name.endsWith(".ts") || name.endsWith(".tsx")
                || name.endsWith(".jsx") || name.endsWith(".go") || name.endsWith(".rs")
                || name.endsWith(".c") || name.endsWith(".cpp") || name.endsWith(".h")
                || name.endsWith(".html") || name.endsWith(".htm") || name.endsWith(".xml")
                || name.endsWith(".rst") || name.endsWith(".text") || name.endsWith(".sh");
    }

    /**
     * 文件大小（字节），读取失败返回 0。
     */
    private long fileSize(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return 0L;
        }
    }

    /**
     * 文件最后修改时间（epoch ms），读取失败返回 null。
     */
    private Long lastModified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 获取绑定（缺省返回禁用空绑定）。
     */
    private Map<String, Object> requireBinding(String kind, String targetId) {
        Map<String, Object> binding = bindings.get(bindingKey(kind, targetId));
        if (binding != null) {
            return binding;
        }
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("target_kind", kind);
        empty.put("target_id", targetId);
        empty.put("enabled", false);
        empty.put("writeback", false);
        empty.put("writeback_eagerness", "manual");
        empty.put("channel_write_enabled", false);
        empty.put("kb_ids", new ArrayList<>());
        return empty;
    }

    /**
     * 绑定存储键。
     */
    private String bindingKey(String kind, String targetId) {
        return kind + ":" + targetId;
    }

    /**
     * 在单个知识库内基于关键字匹配检索命中片段。
     */
    private List<Map<String, Object>> searchInBase(Map<String, Object> base, String query) {
        Path root = Paths.get(String.valueOf(base.get("root_path")));
        List<Map<String, Object>> hits = new ArrayList<>();
        if (!Files.exists(root)) {
            return hits;
        }
        String[] terms = query.toLowerCase().split("\\s+");
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                if (!isTextFile(p)) {
                    continue;
                }
                String rel = root.relativize(p).toString().replace('\\', '/');
                String content;
                try {
                    byte[] bytes = Files.readAllBytes(p);
                    if (bytes.length > 200_000) {
                        content = new String(bytes, 0, 200_000, StandardCharsets.UTF_8);
                    } else {
                        content = new String(bytes, StandardCharsets.UTF_8);
                    }
                } catch (IOException e) {
                    continue;
                }
                int score = 0;
                for (String t : terms) {
                    if (!t.isEmpty()) {
                        score += countOccurrences(content.toLowerCase(), t);
                    }
                }
                if (score <= 0) {
                    continue;
                }
                Map<String, Object> hit = new LinkedHashMap<>();
                hit.put("kb_id", base.get("knowledge_base_id"));
                hit.put("kb_name", base.get("name"));
                hit.put("rel_path", rel);
                hit.put("heading", extractHeading(content, rel));
                hit.put("snippet", extractSnippet(content, terms));
                hit.put("score", (double) score);
                hits.add(hit);
            }
        } catch (IOException ignored) {
            // 遍历失败忽略
        }
        return hits;
    }

    /**
     * 统计子串出现次数。
     */
    private int countOccurrences(String text, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    /**
     * 提取片段标题（首个 Markdown 标题或文件名）。
     */
    private String extractHeading(String content, String relPath) {
        for (String line : content.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                return trimmed.replaceFirst("^#+\\s*", "");
            }
        }
        int slash = relPath.lastIndexOf('/');
        return slash >= 0 ? relPath.substring(slash + 1) : relPath;
    }

    /**
     * 提取首个命中词附近的片段。
     */
    private String extractSnippet(String content, String[] terms) {
        String lower = content.toLowerCase();
        int pos = -1;
        for (String t : terms) {
            if (!t.isEmpty()) {
                int idx = lower.indexOf(t);
                if (idx != -1 && (pos == -1 || idx < pos)) {
                    pos = idx;
                }
            }
        }
        if (pos == -1) {
            return content.length() > 200 ? content.substring(0, 200) : content;
        }
        int start = Math.max(0, pos - 80);
        int end = Math.min(content.length(), pos + 160);
        return content.substring(start, end).replaceAll("\\s+", " ").trim();
    }

    /**
     * 打包目录为 zip。
     */
    private void zipDirectory(Path sourceDir, Path zipFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            try (Stream<Path> walk = Files.walk(sourceDir)) {
                for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                    String entryName = sourceDir.relativize(p).toString().replace('\\', '/');
                    zos.putNextEntry(new ZipEntry(entryName));
                    Files.copy(p, zos);
                    zos.closeEntry();
                }
            }
        }
    }

    /**
     * 解压 zip 到目标目录（防路径穿越）。
     */
    private void unzip(Path zipFile, Path destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path resolved = destDir.resolve(entry.getName()).normalize();
                if (!resolved.startsWith(destDir.normalize())) {
                    throw new IOException("非法 zip 条目: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(resolved);
                } else {
                    Files.createDirectories(resolved.getParent());
                    Files.copy(zis, resolved);
                }
                zis.closeEntry();
            }
        }
    }

    /**
     * 类型擦除安全的 Map 转换。
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> uncheckedCast(Object obj) {
        return (Map<String, Object>) obj;
    }
}

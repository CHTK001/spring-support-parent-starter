package com.chua.starter.ai.support.manage.api;

import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;

import com.chua.common.support.ai.rag.RagClient;
import com.chua.common.support.ai.rag.RagDocument;
import com.chua.common.support.ai.rag.RagResponse;
import com.chua.common.support.ai.rag.RagDocument;
import com.chua.common.support.ai.splitter.TextChunk;
import com.chua.common.support.ai.splitter.TextSplitter;
import com.chua.common.support.ai.splitter.TextSplitterProvider;
import com.chua.common.support.network.client.ClientResponse;
import com.chua.common.support.network.client.HttpClientFactory;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.vector.VectorStorage;
import com.chua.starter.ai.support.configuration.rag.RagClientManager;
import com.chua.starter.ai.support.configuration.rag.RagProperties;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.chua.starter.ai.support.model.WebPageContent;
import com.chua.starter.ai.support.service.KnowledgeBaseStore;
import com.chua.starter.ai.support.service.KnowledgeGraphService;
import com.chua.starter.ai.support.service.impl.KnowledgeGraphExtractionService;
import com.chua.starter.ai.support.service.impl.WebPageCollectorService;
import jakarta.annotation.PostConstruct;
import com.chua.common.support.storage.metadata.Metadata;
import com.chua.common.support.storage.result.GetObjectResult;
import com.chua.common.support.storage.result.ObjectResult;
import com.chua.common.support.storage.result.PutObjectResult;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.manage.knowledge.KnowledgeFileStorage;
import com.chua.starter.ai.support.manage.store.SysAiConversationStore;
import com.chua.starter.ai.support.manage.ApiWebSocketPushService;
import com.chua.starter.ai.support.registry.ChatClientRegistry;
import com.chua.starter.ai.support.util.AiMemoryOwnership;
import com.chua.starter.ai.support.util.AiUserContext;
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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.util.stream.Stream;

/**
 * 知识库命名空间控制器（替代 Rust 后端 nomicore 的 知识 契约）。
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
 * <p>隔离口径：知识库与标签带创建人的 {@code user_id}，本人可读写，管理员可读他人记录但不可改写或删除，
 * 他人一律 403；文件、目录、导入导出等落盘出口同样走归属校验，避免借他人知识库读到其磁盘目录。
 * 绑定以「登录用户标识 + 种类 + 目标标识」为键存放，各用户互不可见。检索配置是全体用户共用的全局默认，
 * 因此读取开放、写入只允许管理员。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
@Slf4j
public class ApiKnowledgeController {

    /**
     * 会话存储（复用其 新标识 生成 UUID 主键）
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

    /** 全局设置：读取工作目录基路径（system.workspaceBaseDir），用于按账号做目录隔离 */
    private final SysAiGlobalSettingService globalSettingService;

    /**
     * 全局 对话客户端 注册中心（AI 生成类接口解析对话客户端）
     */
    private final ChatClientRegistry chatClientRegistry;

    /**
     * 知识点关系抽取服务：上传后异步抽取实体关系写知识图谱
     */
    private final KnowledgeGraphExtractionService knowledgeGraphExtractionService;

    /**
     * 知识图谱服务：删除知识点/知识库时同步回收图谱关联
     */
    private final KnowledgeGraphService knowledgeGraphService;

    /**
     * 知识库文件存储：字节读写走 spring-support-filesystem-starter 的 FileStorage SPI
     */
    private final KnowledgeFileStorage knowledgeFileStorage;

    /**
     * 网页采集服务：知识库按网址抓取正文（含 SSRF 防护）
     */
    private final WebPageCollectorService webPageCollectorService;

    /**
     * 知识库元数据持久化：启动时把 {@code sys_ai_knowledge_base} 全量载入 {@link #bases}，
     * 之后所有写操作同步落库（写穿缓存）。
     *
     * <p>本字段存在的原因是 {@link #bases} 原本是纯内存 Map，后端重启会让知识库
     * 列表连同文件树一起消失。</p>
     */
    private final KnowledgeBaseStore knowledgeBaseStore;

    /**
     * 分片默认值：与前端「最大分块/重叠/最小分块」输入框的默认值一致
     */
    private static final int DEFAULT_CHUNK_MAX = 1000;

    /**
     * 默认重叠长度
     */
    private static final int DEFAULT_CHUNK_OVERLAP = 100;

    /**
     * 默认最小分片长度
     */
    private static final int DEFAULT_CHUNK_MIN = 50;

    /**
     * 分片长度上限：超过这个值按经验召回会明显变差，同时单次向量化成本飙升
     */
    private static final int CHUNK_MAX_LIMIT = 8000;

    /**
     * 分片长度下限：过小的分片几乎没有语义
     */
    private static final int CHUNK_MIN_LIMIT = 16;

    /**
     * 知识库元数据表：知识_基础_标识 → 元数据（记录内带创建人 用户标识）
     *
     * <p>本 Map 现在是 {@link #knowledgeBaseStore} 的读缓存，不再是唯一数据源。</p>
     */
    private final Map<String, Map<String, Object>> bases = new ConcurrentHashMap<>();

    /**
     * 标签表：Key → 标签（记录内带创建人 用户标识）
     */
    private final Map<String, Map<String, Object>> tags = new ConcurrentHashMap<>();

    /**
     * 绑定表："{用户标识}:{种类}:{Target_标识}" → 绑定
     */
    private final Map<String, Map<String, Object>> bindings = new ConcurrentHashMap<>();

    /**
     * 检索配置（嵌入 / rerank 两阶段）
     */
    private final Map<String, Object> retrievalConfig = defaultRetrievalConfig();

    /**
     * 一次「列目录」请求内缓存的「相对路径 → 处理状态」映射。
     *
     * <p>{@code treeEntry} 会被每个文件调用一次，而状态要问向量库；
     * 不缓存的话 30 个文件就是 30 次 {@code listDocuments}。请求结束后由
     * {@code listTree} 清空，所以不会跨请求读到过期数据。</p>
     *
     * <p>值是前端分栏用的状态串：{@code completed} / {@code failed} / {@code uploaded}。
     * 早先只存「已索引路径集合」，无法表达失败态，导致「失败」分栏恒为 0、
     * 抽取失败的文件还被算进「已索引」。</p>
     */
    private Map<String, String> relPathStatusCache;

    /**
     * 启动时把持久化的知识库元数据载入内存缓存。
     *
     * <p>载入失败（表未迁移、库未连通）时只告警不抛异常：让服务照常启动，
     * 退化为「知识库列表为空」，好过整个应用起不来。</p>
     */
    @PostConstruct
    public void loadKnowledgeBases() {
        try {
            List<Map<String, Object>> rows = knowledgeBaseStore.loadAll();
            for (Map<String, Object> row : rows) {
                Object id = row.get("knowledge_base_id");
                if (id != null) {
                    bases.put(String.valueOf(id), row);
                }
            }
            log.info("知识库元数据已载入内存缓存: {} 条", bases.size());
        } catch (Exception e) {
            log.warn("知识库元数据载入失败，本次启动按空库处理: {}", e.getMessage());
        }
    }

    /**
     * 把内存中的知识库记录写穿到数据库。
     *
     * <p>落库失败只告警不抛异常：元数据丢失应当降级为「本次改动未持久化」，
     * 而不是让用户的建库/改名请求直接失败——内存里已经生效，重启后才丢。</p>
     *
     * @param base 知识库记录
     */
    private void persistBase(Map<String, Object> base) {
        try {
            knowledgeBaseStore.update(base);
        } catch (Exception e) {
            log.warn("知识库元数据落库失败（改动仅在内存生效，重启后会丢失）: kb={}, err={}",
                    base.get("knowledge_base_id"), e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 知识库 CRUD
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/bases")
    /**
     * 列表基础。
     * @return 列表基础的结果
     */
    public ApiResult<List<Map<String, Object>>> listBases() {
        List<Map<String, Object>> items = AiMemoryOwnership.visibleToCurrentUser(bases.values(), true).stream()
                .map(this::toBaseResponse)
                .sorted(Comparator.comparing(
                        m -> ((Number) m.getOrDefault("created_at", 0L)).longValue(),
                        Comparator.reverseOrder()))
                .toList();
        return ApiResult.ok(items);
    }

    @PostMapping("/bases")
    /**
     * 创建基础。
     * @param body 主体
     * @return 创建base的结果
     */
    public ApiResult<Map<String, Object>> createBase(@RequestBody Map<String, Object> body) {
        String name = (String) body.getOrDefault("name", "");
        if (StringUtils.isBlank(name)) {
            /**
             * api异常。
             * @param ApiErrorCode.INVALID_PARAM api错误编码.invalid参数
             * @param "知识库名称不能为空" "知识库名称不能为空"
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "知识库名称不能为空");
        }
        String baseId = conversationStore.newId();
        String description = body.get("description") == null ? "" : String.valueOf(body.get("description"));
        String rootPath = body.get("root_path") == null ? null : String.valueOf(body.get("root_path")).trim();
        @SuppressWarnings("unchecked")
        List<String> reqTags = body.get("tags") instanceof List ? (List<String>) body.get("tags") : new ArrayList<>();
        Map<String, Object> source = body.get("source") instanceof Map ? uncheckedCast(body.get("source")) : null;
        boolean isPublic = Boolean.parseBoolean(String.valueOf(body.getOrDefault("is_public", false)));
        // 分片配置：之前这三个字段前端一直在传、后端却从不读取，导致界面上配了也没效果
        // （实测 1955 字节的文档 chunkCount 恒为 1）。这里读取并做范围校验。
        int chunkMax = chunkInt(body, "chunk_max_size", DEFAULT_CHUNK_MAX);
        int chunkOverlap = chunkInt(body, "chunk_overlap_size", DEFAULT_CHUNK_OVERLAP);
        int chunkMin = chunkInt(body, "chunk_min_size", DEFAULT_CHUNK_MIN);

        boolean managed;
        String kind;
        if (source != null) {
            // URL 来源型知识库：托管目录 + 来源配置
            kind = "web";
            managed = true;
            rootPath = resolveManagedRoot(baseId);
        } else if (StringUtils.hasText(rootPath)) {
            // 外部本地目录（非托管）
            kind = "local";
            managed = false;
            assertExternalRoot(rootPath);
            // 统一存绝对路径：相对路径依赖进程 CWD，历史上因此出现过「同一个库两套结果」
            // （不同工作目录的实例对 ./rag-uploads 解析到不同位置，一个 200 一个 404）。
            rootPath = Paths.get(rootPath).toAbsolutePath().normalize().toString();
        } else {
            // 空白托管知识库
            kind = "blank";
            managed = true;
            rootPath = resolveManagedRoot(baseId);
        }

        Path root = Paths.get(rootPath);
        if (managed) {
            try {
                Files.createDirectories(root);
            } catch (IOException e) {
                /**
                 * api异常。
                 * @param ApiErrorCode.INTERNAL_ERROR api错误编码.内部错误
                 * @param e.getMessage( e.获取消息(
                 * @return api异常的结果
                 */
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
        base.put("is_public", isPublic);
        base.put("chunk_max_size", chunkMax);
        base.put("chunk_overlap_size", chunkOverlap);
        base.put("chunk_min_size", chunkMin);
        if (source != null) {
            // 规整来源配置，补齐 entries 默认空列表
            Map<String, Object> src = new LinkedHashMap<>(source);
            if (!(src.get("entries") instanceof List)) {
                src.put("entries", new ArrayList<>());
            }
            base.put("source", src);
        }

        AiMemoryOwnership.stampOwner(base);
        /**
         * refreshstats。
         * @param base 基础
         */
        refreshStats(base);
        bases.put(baseId, base);
        // 建库后立即落库：否则重启即丢，用户会以为创建失败
        try {
            knowledgeBaseStore.insert(base);
        } catch (Exception e) {
            log.warn("知识库元数据落库失败（本次仅在内存生效，重启后会丢失）: kb={}, err={}", baseId, e.getMessage());
        }

        Map<String, Object> result = toBaseResponse(base);
        // 快照型来源在创建时即抓取
        if (source != null && "snapshot".equals(String.valueOf(source.get("mode")))) {
            Map<String, Object> sum = fetchSource(base, root);
            result.put("source_fetch", sum);
            // 抓取会改变文件数与目录占用，抓完再落一次库把缓存值刷准
            persistBase(base);
        }
        pushService.push("knowledge.base-created", toBaseResponse(base));
        return ApiResult.ok(result);
    }

    @GetMapping("/bases/{knowledge_base_id}")
    /**
     * 获取基础。
     * @param knowledge_base_id 知识baseid
     * @return 获取base的结果
     */
    public ApiResult<Map<String, Object>> getBase(@PathVariable String knowledge_base_id) {
        Map<String, Object> base = requireBase(knowledge_base_id, false);
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
        if (body.containsKey("is_public")) {
            base.put("is_public", Boolean.parseBoolean(String.valueOf(body.get("is_public"))));
        }
        // 分片参数：改后只影响**后续**上传/重建索引的文件，已入库的分片不会被重切
        if (body.containsKey("chunk_max_size")) {
            base.put("chunk_max_size", chunkInt(body, "chunk_max_size", DEFAULT_CHUNK_MAX));
        }
        if (body.containsKey("chunk_overlap_size")) {
            int overlap = chunkInt(body, "chunk_overlap_size", DEFAULT_CHUNK_OVERLAP);
            int max = chunkInt(base, "chunk_max_size", DEFAULT_CHUNK_MAX);
            // 重叠必须小于分片大小，否则切分器无法推进
            base.put("chunk_overlap_size", overlap >= max ? Math.max(0, max / 4) : overlap);
        }
        if (body.containsKey("chunk_min_size")) {
            base.put("chunk_min_size", chunkInt(body, "chunk_min_size", DEFAULT_CHUNK_MIN));
        }
        base.put("updated_at", System.currentTimeMillis());
        persistBase(base);
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
            /**
             * 删除recursively。
             * @param root 根
             */
            deleteRecursively(root);
        }
        bases.remove(knowledge_base_id);
        // 同步删掉持久化记录，否则重启后这个库会「复活」
        try {
            knowledgeBaseStore.delete(knowledge_base_id);
        } catch (Exception e) {
            log.warn("知识库元数据删库失败（重启后该库会重新出现）: kb={}, err={}", knowledge_base_id, e.getMessage());
        }
        // 按知识库ID前缀回收其全部向量，避免知识库已删但向量残留继续参与召回
        purgeVectorsByPrefix(knowledge_base_id);
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
    /**
     * 获取retrieval配置。
     * @return 获取retrieval配置的结果
     */
    public ApiResult<Map<String, Object>> getRetrievalConfig() {
        return ApiResult.ok(new LinkedHashMap<>(retrievalConfig));
    }

    @PutMapping("/retrieval")
    @Permission("admin")
    /**
     * 设置retrieval配置（全局默认，仅管理员可改）。
     * @param body 主体
     * @return 设置retrieval配置的结果
     */
    public ApiResult<Map<String, Object>> setRetrievalConfig(@RequestBody Map<String, Object> body) {
        Map<String, Object> embedding = body.get("embedding") instanceof Map ? uncheckedCast(body.get("embedding")) : null;
        Map<String, Object> rerank = body.get("rerank") instanceof Map ? uncheckedCast(body.get("rerank")) : null;
        if (embedding == null || StringUtils.isBlank(String.valueOf(embedding.get("mode")))) {
            /**
             * api异常。
             * @param ApiErrorCode.INVALID_PARAM api错误编码.invalid参数
             * @param mode" mode"
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "embedding 配置缺失 mode");
        }
        if (rerank == null || StringUtils.isBlank(String.valueOf(rerank.get("mode")))) {
            /**
             * api异常。
             * @param ApiErrorCode.INVALID_PARAM api错误编码.invalid参数
             * @param mode" mode"
             * @return api异常的结果
             */
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
            /**
             * api异常。
             * @param ApiErrorCode.UPSTREAM_ERROR api错误编码.upstream错误
             * @param 生成的简介为空" 生成的简介为空"
             * @return api异常的结果
             */
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

        /**
         * refreshstats。
         * @param base 基础
         */
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
    /**
     * generatedescription。
     * @param body 主体
     * @return generateDescription的结果
     */
    public ApiResult<Map<String, Object>> generateDescription(@RequestBody Map<String, Object> body) {
        String rootPath = body.get("root_path") == null ? null : String.valueOf(body.get("root_path")).trim();
        if (StringUtils.isBlank(rootPath)) {
            /**
             * api异常。
             * @param ApiErrorCode.INVALID_PARAM api错误编码.invalid参数
             * @param 不能为空" 不能为空"
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "root_path 不能为空");
        }
        Path root = Paths.get(rootPath);
        assertExternalRoot(rootPath);
        if (!Files.isDirectory(root)) {
            /**
             * api异常。
             * @param ApiErrorCode.INVALID_PARAM api错误编码.invalid参数
             * @param rootPath 根路径
             * @return api异常的结果
             */
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
            /**
             * api异常。
             * @param ApiErrorCode.UPSTREAM_ERROR api错误编码.upstream错误
             * @param 生成的简介为空" 生成的简介为空"
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "AI 生成的简介为空");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("description", description);
        return ApiResult.ok(result);
    }

    @PostMapping("/description/polish")
    /**
     * polishdescription。
     * @param body 主体
     * @return polishDescription的结果
     */
    public ApiResult<Map<String, Object>> polishDescription(@RequestBody Map<String, Object> body) {
        String draft = body.get("draft") == null ? "" : String.valueOf(body.get("draft"));
        if (StringUtils.isBlank(draft)) {
            /**
             * api异常。
             * @param ApiErrorCode.INVALID_PARAM api错误编码.invalid参数
             * @param 不能为空" 不能为空"
             * @return api异常的结果
             */
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
    /**
     * refresh源。
     * @param knowledge_base_id 知识baseid
     * @return refresh源的结果
     */
    public ApiResult<Map<String, Object>> refreshSource(@PathVariable String knowledge_base_id) {
        Map<String, Object> base = requireBase(knowledge_base_id);
        Map<String, Object> source = base.get("source") instanceof Map ? uncheckedCast(base.get("source")) : null;
        if (source == null) {
            /**
             * api异常。
             * @param ApiErrorCode.INVALID_PARAM api错误编码.invalid参数
             * @param "该知识库未配置来源" "该知识库未配置来源"
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "该知识库未配置来源");
        }
        Path root = resolveRoot(base);
        Map<String, Object> summary = fetchSource(base, root);
        base.put("source", source);
        base.put("updated_at", System.currentTimeMillis());
        persistBase(base);
        pushService.push("knowledge.base-updated", toBaseResponse(base));
        return ApiResult.ok(summary);
    }

    /**
     * 按需生成 / 补全某个知识点的关系图谱。
     *
     * <p>用于两种场景：① 知识点是网页采集来的，当初没开关系提取（或抽取失败），事后补一次；
     * ② 原文改了关系，需要让 AI 重新抽取。抽取走 {@link KnowledgeGraphExtractionService}，
     * 结果按「合并」语义落库：只补本次抽到、此前没有的关联，已有的原样保留，
     * 反复调用不会丢数据（早前实现是先清后写，导致抽得少的一次覆盖抽得多的那次）。
     * 实体按<b>名称</b>唯一（类型只是可升级的属性），关系按 (源, 目标, 类型) 唯一，
     * 两者都幂等，所以同内容重抽不会让节点或边无理由变多。</p>
     *
     * <p>{@code reset=true} 时改为「先清空该知识点的全部关联再抽」，用于原文被改过、
     * 或想让图谱回到只反映当前文本的干净状态。默认 false，即安全的增量补充。</p>
     *
     * <p>放在本控制器而不是图谱控制器，是因为只有这里有知识库目录的解析与安全路径拼接能力。</p>
     *
     * @param knowledge_base_id 知识库ID
     * @param docPath            知识点相对路径
     * @param reset              是否先清空该知识点的已有图谱关联再抽取
     * @return 抽取汇总：entity_count / relation_count / elapsed_ms
     */
    @PostMapping("/bases/{knowledge_base_id}/graph/generate")
    public ApiResult<Map<String, Object>> generateGraph(@PathVariable String knowledge_base_id,
                                                         @RequestParam(value = "doc_path", required = false) String docPath,
                                                         @RequestParam(value = "reset", required = false,
                                                                 defaultValue = "false") boolean reset) {
        Map<String, Object> base = requireBase(knowledge_base_id);
        Path root = resolveRoot(base);
        long start = System.currentTimeMillis();
        List<Map<String, Object>> done = new ArrayList<>();
        int entities = 0;
        int relations = 0;
        if (StringUtils.isBlank(docPath)) {
            // 未指定知识点：对该知识库下全部文本文件跑一遍
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path p : walk.filter(Files::isRegularFile).sorted().toList()) {
                    if (!isTextFile(p)) {
                        continue;
                    }
                    String rel = root.relativize(p).toString().replace('\\', '/');
                    String text = readTextQuietly(p);
                    if (text == null || text.isBlank()) {
                        continue;
                    }
                    done.add(singleGraphGenerate(base, rel, text, reset));
                }
            } catch (Exception e) {
                throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "遍历知识库失败: " + e.getMessage());
            }
        } else {
            String rel = docPath.trim();
            Path file = safeResolve(root, rel);
            if (!Files.isRegularFile(file)) {
                throw new ApiException(ApiErrorCode.NOT_FOUND, "知识点不存在: " + rel);
            }
            String text = readTextQuietly(file);
            if (text == null) {
                throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "知识点内容读取失败: " + rel);
            }
            done.add(singleGraphGenerate(base, rel, text, reset));
        }
        for (Map<String, Object> item : done) {
            entities += toIntSafe(item.get("entity_count"), 0);
            relations += toIntSafe(item.get("relation_count"), 0);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("knowledge_base_id", knowledge_base_id);
        result.put("files", done.size());
        result.put("entity_count", entities);
        result.put("relation_count", relations);
        result.put("reset", reset);
        // reset 是「收口」语义，顺手把历史遗留的同名节点与重复边合并掉，
        // 让图谱回到只反映当前文本的干净状态（不传 reset 的增量补充不做全库扫描）
        if (reset) {
            result.put("merged_entities", knowledgeGraphService.collapseDuplicateGraph(knowledge_base_id));
        }
        result.put("elapsed_ms", System.currentTimeMillis() - start);
        return ApiResult.ok(result);
    }

    /**
     * 对单个知识点跑一次关系抽取并落库。
     *
     * <p>{@code reset=true} 时先清空该知识点已有的全部图谱关联（含孤立实体/关系回收），
     * 让图谱回到「只反映当前文本」的干净状态；默认 false 走合并语义，只补不删。</p>
     *
     * @param base  知识库元数据
     * @param rel   知识点相对路径
     * @param text  知识点正文
     * @param reset 是否先清空该知识点的已有关联
     * @return 该知识点的抽取结果摘要
     */
    private Map<String, Object> singleGraphGenerate(Map<String, Object> base, String rel, String text,
                                                    boolean reset) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("doc_path", rel);
        String kbId = String.valueOf(base.get("knowledge_base_id"));
        if (reset) {
            knowledgeGraphService.deleteByKnowledgePoint(kbId, rel);
        }
        var extraction = knowledgeGraphExtractionService.extractAndStore(
                kbId,
                base.get("name") == null ? null : String.valueOf(base.get("name")),
                rel, text);
        int entityCount = extraction == null || extraction.entities() == null
                ? 0 : (int) extraction.entities().stream().filter(Objects::nonNull).count();
        int relationCount = extraction == null || extraction.relations() == null
                ? 0 : (int) extraction.relations().stream().filter(Objects::nonNull).count();
        item.put("entity_count", entityCount);
        item.put("relation_count", relationCount);
        return item;
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
                fetchSource(base, resolveRoot(base));
            }
        } else {
            /**
             * api异常。
             * @param ApiErrorCode.INVALID_PARAM api错误编码.invalid参数
             * @param 字段格式非法" 字段格式非法"
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "source 字段格式非法");
        }
        base.put("updated_at", System.currentTimeMillis());
        persistBase(base);
        pushService.push("knowledge.base-updated", toBaseResponse(base));
        return ApiResult.ok(toBaseResponse(base));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 文件 / 目录浏览
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/bases/{knowledge_base_id}/files")
    public ApiResult<List<Map<String, Object>>> listFiles(@PathVariable String knowledge_base_id,
                                                          @RequestParam(required = false) String path) {
        Path root = resolveRoot(requireBase(knowledge_base_id, false));
        Path dir = safeResolve(root, path);
        if (!Files.isDirectory(dir)) {
            /**
             * api异常。
             * @param ApiErrorCode.NOT_FOUND api错误编码.notfound
             * @param path 路径
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.NOT_FOUND, "目录不存在: " + path);
        }
        List<Map<String, Object>> files = new ArrayList<>();
        /**
         * 尝试。
         * @param 1 1
         */
        try (Stream<Path> walk = Files.walk(dir, 1)) {
            for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                files.add(fileEntry(root, p));
            }
        } catch (IOException e) {
            /**
             * api异常。
             * @param ApiErrorCode.INTERNAL_ERROR api错误编码.内部错误
             * @param e.getMessage( e.获取消息(
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "列举文件失败: " + e.getMessage());
        }
        files.sort(Comparator.comparing(m -> String.valueOf(m.get("rel_path"))));
        return ApiResult.ok(files);
    }

    @GetMapping("/bases/{knowledge_base_id}/tree")
    public ApiResult<List<Map<String, Object>>> listTree(@PathVariable String knowledge_base_id,
                                                         @RequestParam(required = false) String path) {
        Path root = resolveRoot(requireBase(knowledge_base_id, false));
        Path dir = safeResolve(root, path);
        if (!Files.isDirectory(dir)) {
            /**
             * api异常。
             * @param ApiErrorCode.NOT_FOUND api错误编码.notfound
             * @param path 路径
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.NOT_FOUND, "目录不存在: " + path);
        }
        List<Map<String, Object>> tree = buildTree(root, dir, 4);
        relPathStatusCache = null;
        tree.sort(Comparator.<Map<String, Object>, Boolean>comparing(m -> Boolean.TRUE.equals(m.get("is_dir")))
                .thenComparing(m -> String.valueOf(m.get("name"))));
        return ApiResult.ok(tree);
    }

    @PostMapping("/bases/{knowledge_base_id}/folder")
    public ApiResult<Map<String, Object>> createFolder(@PathVariable String knowledge_base_id,
                                                       @RequestBody Map<String, Object> body) {
        String rel = body.get("path") == null ? null : String.valueOf(body.get("path")).trim();
        if (StringUtils.isBlank(rel)) {
            /**
             * api异常。
             * @param ApiErrorCode.INVALID_PARAM api错误编码.invalid参数
             * @param 不能为空" 不能为空"
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "path 不能为空");
        }
        Path root = resolveRoot(requireBase(knowledge_base_id));
        Path folder = safeResolve(root, rel);
        if (!knowledgeFileStorage.mkdir(root.toString(), rel)) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "创建目录失败: " + rel + " -> " + folder);
        }
        Map<String, Object> base = requireBase(knowledge_base_id);
        /**
         * refreshstats。
         * @param base 基础
         */
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
            /**
             * api异常。
             * @param ApiErrorCode.NOT_FOUND api错误编码.notfound
             * @param path 路径
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.NOT_FOUND, "目录不存在: " + path);
        }
        /**
         * 删除recursively。
         * @param folder 文件夹
         */
        deleteRecursively(folder);
        Map<String, Object> base = requireBase(knowledge_base_id);
        // 按「知识库ID/目录」前缀回收该目录下所有文件的向量
        purgeVectorsByPrefix(knowledge_base_id + "/" + path);
        /**
         * refreshstats。
         * @param base 基础
         */
        refreshStats(base);
        pushService.push("knowledge.base-updated", toBaseResponse(base));
        return ApiResult.ok();
    }

    /**
     * 查询某个知识点实际切出来的分片，供界面展示「这份文档被切成了什么」。
     *
     * <p>前端 {@code getKBFileChunks} 此前标注「后端暂未提供接口」，界面上只能看到
     * {@code chunkCount} 数字、看不到分片内容。这里按入库时用的同一套分片器
     * （{@link #splitterOf}）对原文重新切分，保证看到的就是入库时那一份切法；
     * 读的是磁盘上的原文而不是向量库，因此不依赖检索接口。</p>
     *
     * @param knowledge_base_id 知识库ID
     * @param path              知识点相对路径
     * @return 分片列表：index / size / start_offset / end_offset / text
     */
    @GetMapping("/bases/{knowledge_base_id}/chunks")
    public ApiResult<List<Map<String, Object>>> listFileChunks(@PathVariable String knowledge_base_id,
                                                               @RequestParam("path") String path) {
        Map<String, Object> base = requireBase(knowledge_base_id, false);
        Path root = resolveRoot(base);
        Path file = safeResolve(root, path);
        if (!Files.isRegularFile(file)) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "知识点不存在: " + path);
        }
        String text = readTextQuietly(file);
        if (text == null) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "知识点内容读取失败: " + path);
        }
        TextSplitter splitter = splitterOf(base);
        List<TextChunk> chunks = splitter == null
                ? TextSplitterProvider.create("sentence", DEFAULT_CHUNK_MAX, DEFAULT_CHUNK_OVERLAP).split(text)
                : splitter.split(text);
        List<Map<String, Object>> out = new ArrayList<>(chunks.size());
        for (TextChunk c : chunks) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("index", c.index());
            item.put("size", c.text() == null ? 0 : c.text().length());
            item.put("start_offset", c.startOffset());
            item.put("end_offset", c.endOffset());
            item.put("type", c.type());
            item.put("text", c.text());
            out.add(item);
        }
        return ApiResult.ok(out);
    }

    @PostMapping("/bases/{knowledge_base_id}/tree/rename")
    public ApiResult<Map<String, Object>> renameTreeEntry(@PathVariable String knowledge_base_id,
                                                         @RequestBody Map<String, Object> body) {
        String rel = body.get("path") == null ? null : String.valueOf(body.get("path")).trim();
        String newName = body.get("new_name") == null ? null : String.valueOf(body.get("new_name")).trim();
        if (StringUtils.isBlank(rel) || StringUtils.isBlank(newName)) {
            /**
             * api异常。
             * @param ApiErrorCode.INVALID_PARAM api错误编码.invalid参数
             * @param 均不能为空" 均不能为空"
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "path 与 new_name 均不能为空");
        }
        if (newName.contains("/") || newName.contains("\\")) {
            /**
             * api异常。
             * @param ApiErrorCode.INVALID_PARAM api错误编码.invalid参数
             * @param "新名称不能包含路径分隔符" "新名称不能包含路径分隔符"
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "新名称不能包含路径分隔符");
        }
        Path root = resolveRoot(requireBase(knowledge_base_id));
        Path target = safeResolve(root, rel);
        Path parent = target.getParent();
        if (parent == null) {
            /**
             * api异常。
             * @param ApiErrorCode.INVALID_PARAM api错误编码.invalid参数
             * @param "不能重命名根目录" "不能重命名根目录"
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "不能重命名根目录");
        }
        Path renamed = parent.resolve(newName);
        try {
            Files.move(target, renamed);
        } catch (IOException e) {
            /**
             * api异常。
             * @param ApiErrorCode.INTERNAL_ERROR api错误编码.内部错误
             * @param e.getMessage( e.获取消息(
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "重命名失败: " + e.getMessage());
        }
        Map<String, Object> base = requireBase(knowledge_base_id);
        // 改名会改变向量标识（旧路径向量残留、新路径无向量），此处回收并重建索引
        int lastSlash = rel.lastIndexOf('/');
        String newRel = lastSlash >= 0 ? rel.substring(0, lastSlash + 1) + newName : newName;
        reindexVectorsAfterRename(knowledge_base_id, rel, newRel);
        /**
         * refreshstats。
         * @param base 基础
         */
        refreshStats(base);
        pushService.push("knowledge.base-updated", toBaseResponse(base));
        return ApiResult.ok(treeEntry(root, renamed));
    }

    @GetMapping("/bases/{knowledge_base_id}/file")
    public ApiResult<Map<String, Object>> readFile(@PathVariable String knowledge_base_id,
                                                   @RequestParam String path) {
        Path root = resolveRoot(requireBase(knowledge_base_id, false));
        // 空 path 以前经 safeResolve 落到根目录、再由 isRegularFile 判否返回 404；
        // 现在字节读取走 SPI，必须在入口显式挡住空值，否则组件会抛 IllegalArgumentException 变 500
        if (StringUtils.isBlank(path)) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "文件不存在: " + path);
        }
        Path file = safeResolve(root, path);
        // 存在性与字节读取都走文件存储 SPI；越界校验仍由 safeResolve 给 400 业务提示
        GetObjectResult got = knowledgeFileStorage.get(root.toString(), path);
        if (got == null || ObjectResult.ResultCode.FAILURE == got.getResultCode()
                || got.getInputStream() == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "文件不存在: " + path);
        }
        if (!isTextFile(file)) {
            /**
             * api异常。
             * @param ApiErrorCode.INVALID_PARAM api错误编码.invalid参数
             * @param "仅支持读取文本文件" "仅支持读取文本文件"
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "仅支持读取文本文件");
        }
        String content;
        try (InputStream in = got.getInputStream()) {
            content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            /**
             * api异常。
             * @param ApiErrorCode.INTERNAL_ERROR api错误编码.内部错误
             * @param e.getMessage( e.获取消息(
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "读取文件失败: " + e.getMessage());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rel_path", root.relativize(file).toString().replace('\\', '/'));
        result.put("content", content);
        Metadata meta = got.getMetadata();
        result.put("size", meta != null ? meta.getSize() : content.length());
        result.put("modified_at", meta != null ? meta.getLastModified() : 0L);
        return ApiResult.ok(result);
    }

    @PutMapping("/bases/{knowledge_base_id}/file")
    public ApiResult<Void> writeFile(@PathVariable String knowledge_base_id,
                                     @RequestBody Map<String, Object> body) {
        String rel = body.get("path") == null ? null : String.valueOf(body.get("path")).trim();
        if (StringUtils.isBlank(rel)) {
            /**
             * api异常。
             * @param ApiErrorCode.INVALID_PARAM api错误编码.invalid参数
             * @param 不能为空" 不能为空"
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "path 不能为空");
        }
        Object rawContent = body.get("content");
        String content = rawContent == null ? "" : String.valueOf(rawContent);
        Map<String, Object> base = requireBase(knowledge_base_id);
        Path root = resolveRoot(base);
        Path file = safeResolve(root, rel);
        // 落盘改走 spring-support-filesystem-starter 的 FileStorage SPI（对象键 = 相对根目录的路径），
        // 不再直接 Files.createDirectories + Files.writeString
        PutObjectResult put = knowledgeFileStorage.writeText(root.toString(), rel, content);
        if (put == null || ObjectResult.ResultCode.FAILURE == put.getResultCode()) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "写入文件失败: " + rel + " -> " + file
                    + (put == null ? "" : " (" + put.getMessage() + ")"));
        }
        /**
         * 索引知识文件。
         * @param base 基础
         * @param rel rel
         * @param content 内容
         */
        indexKnowledgeFile(base, rel, content);
        /**
         * refreshstats。
         * @param base 基础
         */
        refreshStats(base);
        pushService.push("knowledge.base-updated", toBaseResponse(base));
        return ApiResult.ok();
    }

    @DeleteMapping("/bases/{knowledge_base_id}/file")
    public ApiResult<Void> deleteFile(@PathVariable String knowledge_base_id,
                                      @RequestParam String path) {
        Path root = resolveRoot(requireBase(knowledge_base_id));
        // 同 readFile：空 path 以前是 404，挡住避免走到 SPI 的空值校验
        if (StringUtils.isBlank(path)) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "文件不存在: " + path);
        }
        Path file = safeResolve(root, path);
        if (!Files.isRegularFile(file)) {
            /**
             * api异常。
             * @param ApiErrorCode.NOT_FOUND api错误编码.notfound
             * @param path 路径
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.NOT_FOUND, "文件不存在: " + path);
        }
        // 删除同样走文件存储 SPI；这里是文件删除接口，目录删除另有 DELETE /folder
        if (!knowledgeFileStorage.delete(root.toString(), path)) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "删除文件失败: " + path);
        }
        Map<String, Object> base = requireBase(knowledge_base_id);
        // 按「知识库ID/相对路径」前缀精确回收该文件产生的向量
        purgeVectorsByPrefix(knowledge_base_id + "/" + path);
        /**
         * refreshstats。
         * @param base 基础
         */
        refreshStats(base);
        pushService.push("knowledge.base-updated", toBaseResponse(base));
        return ApiResult.ok();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 库内问答（RAG 检索 + 大模型作答 + 引用溯源）
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 基于某个知识库提问：向量检索取回相关分片，交大模型作答，并返回可点击的引用。
     *
     * <p>与 {@code POST /search} 的区别：那个是<b>关键字匹配</b>，只回片段不出答案；
     * 本端点走 {@link RagClient#query(String, int, double)}，一次拿到答案与引用源。</p>
     *
     * <p><b>刻意不调 {@code temperature()} / {@code system()}</b>：这些是
     * {@code RagClient} 上的链式 setter，<b>原地修改并返回 this</b>，而
     * {@code RagClientManager} 存的是进程级共享单例。在这里调会把<b>全应用</b>的
     * 对话参数一起改掉（前面已经因此把全应用钉成贪心解码并回退过一次）。
     * 所以只用无副作用的 {@code query(query, topK, threshold)} 重载。</p>
     *
     * <p>引用能还原成文件路径，是因为入库时 {@code docId} 固定为
     * {@code <knowledge_base_id>/<相对路径>}（见 {@link #indexKnowledgeFile}），
     * 这里剥掉前缀即可拿到 {@code doc_path}，前端拿它去读原文预览。</p>
     *
     * <p>注意向量库检索时可能<b>不回传 metadata</b>（jvector 只回 score），
     * 此时 {@code RagPipeline} 靠 {@code <docId>_<块号>} 的拼接规则从 chunkId 反推 docId。
     * 换言之入库时若改用不带路径的随机 docId，引用就会退化成一个无法定位文件的
     * {@code fileName}，前端只能显示 chunkId 且点不开原文——这是曾经的实际故障。</p>
     *
     * @param knowledge_base_id 知识库ID
     * @param body 请求体：query（必填）、top_k（缺省 6）、similarity（缺省 0.35）
     * @return answer / citations / top_k / elapsed_ms
     */
    @PostMapping("/bases/{knowledge_base_id}/ask")
    public ApiResult<Map<String, Object>> ask(@PathVariable String knowledge_base_id,
                                               @RequestBody Map<String, Object> body) {
        String query = body == null || body.get("query") == null ? "" : String.valueOf(body.get("query"));
        if (StringUtils.isBlank(query)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "query 不能为空");
        }
        Map<String, Object> base = requireBase(knowledge_base_id, false);
        int topK = clampInt(toIntSafe(body.get("top_k"), 6), 1, 20);
        double similarity = body.get("similarity") instanceof Number num
                ? Math.max(0.0D, Math.min(1.0D, num.doubleValue()))
                : 0.35D;

        long start = System.currentTimeMillis();
        RagClient client = ragClientManager.current();
        RagResponse response;
        try {
            response = client.query(query, topK, similarity);
        } catch (RuntimeException e) {
            // 失败注入映射：外部 embedding SDK 的异常经 RagPipeline 包装后只剩
            // 「Node execution failed: embedQuery」，前端无法分辨余额不足/密钥无效/限流/维度不符。
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, friendlyEmbeddingError(e));
        }

        String prefix = knowledge_base_id + "/";
        List<Map<String, Object>> citations = new ArrayList<>();
        if (response != null && response.sources() != null) {
            int index = 0;
            for (RagResponse.Source source : response.sources()) {
                if (source == null) {
                    continue;
                }
                Map<String, Object> meta = source.metadata();
                String docId = source.documentId() == null ? "" : source.documentId();
                String docPath = docId.startsWith(prefix) ? docId.substring(prefix.length()) : docId;
                String fileName = meta == null ? null : String.valueOf(meta.getOrDefault("fileName", ""));
                index++;
                Map<String, Object> citation = new LinkedHashMap<>(8);
                citation.put("index", index);
                citation.put("doc_id", docId);
                citation.put("doc_path", docPath);
                citation.put("file_name", fileName == null || fileName.isBlank()
                        ? fileNameOf(docPath) : fileName);
                citation.put("score", round4(source.score()));
                citation.put("snippet", snippet(source.content(), 400));
                citations.add(citation);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>(6);
        result.put("knowledge_base_id", knowledge_base_id);
        result.put("query", query);
        result.put("answer", response == null || response.answer() == null ? "" : response.answer());
        result.put("citations", citations);
        result.put("top_k", topK);
        result.put("elapsed_ms", System.currentTimeMillis() - start);
        log.info("[Knowledge] 库内问答 kb={} topK={} 命中引用={} 耗时={}ms",
                knowledge_base_id, topK, citations.size(), result.get("elapsed_ms"));
        return ApiResult.ok(result);
    }

    /**
     * 把整数夹到区间内。
     *
     * @param value 原始值
     * @param min 下界
     * @param max 上界
     * @return 夹取后的值
     */
    private int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 保留四位小数，避免浮点尾数在 JSON 里拖一长串。
     *
     * @param value 原始值
     * @return 四位小数的值
     */
    private double round4(double value) {
        return Math.round(value * 10000.0D) / 10000.0D;
    }

    /**
     * 取引用片段的前若干字符，超长补省略号。
     *
     * @param content 原文
     * @param max 保留长度
     * @return 截断后的片段
     */
    private String snippet(String content, int max) {
        if (content == null) {
            return "";
        }
        String flat = content.replaceAll("\\s+", " ").trim();
        return flat.length() <= max ? flat : flat.substring(0, max) + "…";
    }

    /**
     * 从相对路径里取文件名。
     *
     * @param docPath 相对路径
     * @return 文件名；路径为空时返回空串
     */
    private String fileNameOf(String docPath) {
        if (docPath == null || docPath.isBlank()) {
            return "";
        }
        int idx = docPath.lastIndexOf('/');
        return idx >= 0 ? docPath.substring(idx + 1) : docPath;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 绑定
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 获取绑定（通过路径变量，适用于无斜杠的 标识）。
     */
    @GetMapping("/binding/{kind}/{target_id}")
    public ApiResult<Map<String, Object>> getBinding(@PathVariable String kind,
                                                      @PathVariable String target_id) {
        return ApiResult.ok(requireBinding(kind, target_id));
    }

    /**
     * 获取绑定（通过查询参数，支持 Target_标识 中包含斜杠的路径）。
     */
    @GetMapping("/binding")
    public ApiResult<Map<String, Object>> getBindingByQuery(@RequestParam String kind,
                                                             @RequestParam String target_id) {
        return ApiResult.ok(requireBinding(kind, target_id));
    }

    /**
     * 设置绑定（通过查询参数，支持 Target_标识 中包含斜杠的路径）。
     */
    @PostMapping("/binding")
    public ApiResult<Map<String, Object>> setBindingByQuery(@RequestParam String kind,
                                                             @RequestParam String target_id,
                                                             @RequestBody Map<String, Object> body) {
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("target_kind", kind);
        binding.put("target_id", target_id);
        binding.put("enabled", Boolean.TRUE.equals(body.get("enabled")));
        binding.put("writeback", Boolean.TRUE.equals(body.get("writeback")));
        binding.put("writeback_eagerness",
                body.get("writeback_eagerness") == null ? "manual" : String.valueOf(body.get("writeback_eagerness")));
        binding.put("channel_write_enabled", Boolean.TRUE.equals(body.get("channel_write_enabled")));
        List<Object> kbIds = body.get("kb_ids") instanceof List
                ? new ArrayList<>((List<?>) body.get("kb_ids")) : new ArrayList<>();
        for (Object kbId : kbIds) {
            requireBase(String.valueOf(kbId), false);
        }
        binding.put("kb_ids", kbIds);
        AiMemoryOwnership.stampOwner(binding);
        bindings.put(bindingKey(kind, target_id), binding);
        pushService.push("knowledge.binding-changed", new LinkedHashMap<>(binding));
        return ApiResult.ok(binding);
    }

    @GetMapping("/bases/{knowledge_base_id}/consumers")
    /**
     * 列表consumers。
     * @param knowledge_base_id 知识baseid
     * @return 列表consumers的结果
     */
    public ApiResult<List<Map<String, Object>>> listConsumers(@PathVariable String knowledge_base_id) {
        requireBase(knowledge_base_id, false);
        List<Map<String, Object>> consumers = new ArrayList<>();
        for (Map<String, Object> b : AiMemoryOwnership.visibleToCurrentUser(bindings.values())) {
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
            /**
             * api异常。
             * @param ApiErrorCode.INVALID_PARAM api错误编码.invalid参数
             * @param 不能为空" 不能为空"
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "dest_path 不能为空");
        }
        Path root = resolveRoot(base);
        assertExternalRoot(destPath);
        Path dest = Paths.get(destPath);
        try {
            Files.createDirectories(dest.getParent());
            /**
             * 压缩目录。
             * @param root 根
             * @param dest dest
             */
            zipDirectory(root, dest);
        } catch (IOException e) {
            /**
             * api异常。
             * @param ApiErrorCode.INTERNAL_ERROR api错误编码.内部错误
             * @param e.getMessage( e.获取消息(
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "导出失败: " + e.getMessage());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dest_path", dest.toString());
        return ApiResult.ok(result);
    }

    @PostMapping("/bases/import")
    /**
     * 导入基础。
     * @param body 主体
     * @return 导入base的结果
     */
    public ApiResult<Map<String, Object>> importBase(@RequestBody Map<String, Object> body) {
        String srcPath = body.get("src_path") == null ? null : String.valueOf(body.get("src_path")).trim();
        if (StringUtils.isBlank(srcPath)) {
            /**
             * api异常。
             * @param ApiErrorCode.INVALID_PARAM api错误编码.invalid参数
             * @param 不能为空" 不能为空"
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "src_path 不能为空");
        }
        Path src = Paths.get(srcPath);
        if (!Files.isRegularFile(src)) {
            /**
             * api异常。
             * @param ApiErrorCode.INVALID_PARAM api错误编码.invalid参数
             * @param srcPath src路径
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "源包不存在: " + srcPath);
        }
        String baseId = conversationStore.newId();
        Path root = Paths.get(resolveManagedRoot(baseId));
        try {
            Files.createDirectories(root);
            /**
             * unzip。
             * @param src src
             * @param root 根
             */
            unzip(src, root);
        } catch (IOException e) {
            /**
             * api异常。
             * @param ApiErrorCode.INTERNAL_ERROR api错误编码.内部错误
             * @param e.getMessage( e.获取消息(
             * @return api异常的结果
             */
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
        AiMemoryOwnership.stampOwner(base);
        /**
         * refreshstats。
         * @param base 基础
         */
        refreshStats(base);
        bases.put(baseId, base);
        // 导入出来的库同样要落库，否则重启即丢
        try {
            knowledgeBaseStore.insert(base);
        } catch (Exception e) {
            log.warn("导入知识库元数据落库失败（本次仅在内存生效，重启后会丢失）: kb={}, err={}", baseId, e.getMessage());
        }
        pushService.push("knowledge.base-created", toBaseResponse(base));
        return ApiResult.ok(toBaseResponse(base));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 标签
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/tags")
    /**
     * 列表标签。
     * @return 列表标签的结果
     */
    public ApiResult<List<Map<String, Object>>> listTags() {
        List<Map<String, Object>> items = AiMemoryOwnership.visibleToCurrentUser(tags.values()).stream()
                .sorted(Comparator.comparing(
                        m -> ((Number) m.getOrDefault("sortOrder", 0)).intValue()))
                .toList();
        return ApiResult.ok(items);
    }

    @PostMapping("/tags")
    /**
     * 创建标签。
     * @param body 主体
     * @return 创建标签的结果
     */
    public ApiResult<Map<String, Object>> createTag(@RequestBody Map<String, Object> body) {
        String label = body.get("label") == null ? null : String.valueOf(body.get("label")).trim();
        if (StringUtils.isBlank(label)) {
            /**
             * api异常。
             * @param ApiErrorCode.INVALID_PARAM api错误编码.invalid参数
             * @param "标签名称不能为空" "标签名称不能为空"
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "标签名称不能为空");
        }
        String key = "tag-" + conversationStore.newId();
        int maxOrder = AiMemoryOwnership.visibleToCurrentUser(tags.values()).stream()
                .mapToInt(t -> ((Number) t.getOrDefault("sortOrder", 0)).intValue()).max().orElse(0);
        Map<String, Object> tag = new LinkedHashMap<>();
        tag.put("key", key);
        tag.put("label", label);
        tag.put("color", body.get("color"));
        tag.put("sortOrder", maxOrder + 1);
        AiMemoryOwnership.stampOwner(tag);
        tags.put(key, tag);
        pushService.push("knowledge.tag-changed", Map.of());
        return ApiResult.ok(tag);
    }

    @PutMapping("/tags/{key}")
    public ApiResult<Void> updateTag(@PathVariable String key,
                                     @RequestBody Map<String, Object> body) {
        Map<String, Object> tag = requireTag(key, true);
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
    /**
     * 删除标签。
     * @param key Key
     * @return 删除标签的结果
     */
    public ApiResult<Void> deleteTag(@PathVariable String key) {
        requireTag(key, true);
        tags.remove(key);
        // 仅从本人的知识库标签列表中移除（他人列表不动）
        String ownerId = AiUserContext.requireUserId();
        for (Map<String, Object> base : bases.values()) {
            if (!ownerId.equals(String.valueOf(base.get(AiMemoryOwnership.USER_ID_FIELD)))) {
                continue;
            }
            @SuppressWarnings("unchecked")
            List<String> baseTags = (List<String>) base.get("tags");
            if (baseTags != null) {
                baseTags.remove(key);
            }
        }
        pushService.push("knowledge.tag-changed", Map.of());
        return ApiResult.ok();
    }

    /**
     * 获取标签并校验归属（不存在抛 NOT_FOUND）。
     * @param key Key
     * @param writable 是否要求写权限
     * @return requireTag的结果
     */
    private Map<String, Object> requireTag(String key, boolean writable) {
        Map<String, Object> tag = tags.get(key);
        if (tag == null) {
            /**
             * api异常。
             * @param ApiErrorCode.NOT_FOUND api错误编码.notfound
             * @param key Key
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.NOT_FOUND, "标签不存在: " + key);
        }
        return AiMemoryOwnership.requireOwned(tag, writable, "标签");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 列表页统计（一次请求拿全量汇总 + 每库计数）
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 知识库列表页的统计汇总。
     *
     * <p>列表页要展示「知识库数 / 文档总量 / 实体节点 / 关系连线」以及每张卡片上的
     * 实体数、关系数、文档数。{@code toBaseResponse} 只是把存储的 map 原样拷出，
     * 不带这些计数；而逐个库去调图谱与文件列表就是 N+1 次请求（实测 5 个库要 10 次）。
     * 所以这里一次算完返回。</p>
     *
     * <p>只统计<b>当前用户可见</b>的知识库，口径与 {@code /search} 一致，
     * 避免通过统计泄露他人私有库的规模。</p>
     *
     * @return totals（四个汇总数）+ per_base（每库的 file/entity/relation 计数）
     */
    @GetMapping("/bases/overview")
    public ApiResult<Map<String, Object>> overview() {
        List<Map<String, Object>> visible = new ArrayList<>(AiMemoryOwnership.visibleToCurrentUser(bases.values()));
        Map<String, Map<String, Object>> perBase = new LinkedHashMap<>();
        int totalFiles = 0;
        int totalEntities = 0;
        int totalRelations = 0;
        for (Map<String, Object> base : visible) {
            String id = String.valueOf(base.get("knowledge_base_id"));
            int files = 0;
            try {
                Path root = resolveRoot(base);
                if (Files.isDirectory(root)) {
                    try (Stream<Path> walk = Files.walk(root)) {
                        files = (int) walk.filter(Files::isRegularFile).count();
                    }
                }
            } catch (Exception e) {
                // 单个库统计失败不该让整页统计挂掉，记 0 并继续
                log.debug("[Knowledge] 统计库 {} 的文件数失败: {}", id, e.getMessage());
            }
            int[] graph = knowledgeGraphService.countKnowledgeBaseGraph(id);
            Map<String, Object> one = new LinkedHashMap<>(4);
            one.put("file_count", files);
            one.put("entity_count", graph[0]);
            one.put("relation_count", graph[1]);
            perBase.put(id, one);
            totalFiles += files;
            totalEntities += graph[0];
            totalRelations += graph[1];
        }
        Map<String, Object> totals = new LinkedHashMap<>(4);
        totals.put("kb_count", visible.size());
        totals.put("file_count", totalFiles);
        totals.put("entity_count", totalEntities);
        totals.put("relation_count", totalRelations);
        Map<String, Object> result = new LinkedHashMap<>(2);
        result.put("totals", totals);
        result.put("per_base", perBase);
        return ApiResult.ok(result);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 跨库搜索（关键字匹配）
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/search")
    /**
     * 搜索。
     * @param body 主体
     * @return 搜索的结果
     */
    public ApiResult<List<Map<String, Object>>> search(@RequestBody Map<String, Object> body) {
        String query = body.get("query") == null ? "" : String.valueOf(body.get("query"));
        if (StringUtils.isBlank(query)) {
            /**
             * api异常。
             * @param ApiErrorCode.INVALID_PARAM api错误编码.invalid参数
             * @param 不能为空" 不能为空"
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "query 不能为空");
        }
        int limit = body.get("limit") == null ? 20 : ((Number) body.get("limit")).intValue();
        @SuppressWarnings("unchecked")
        List<String> kbIds = body.get("kbIds") instanceof List ? (List<String>) body.get("kbIds") : new ArrayList<>();

        List<Map<String, Object>> scope = new ArrayList<>();
        if (kbIds.isEmpty()) {
            scope.addAll(AiMemoryOwnership.visibleToCurrentUser(bases.values()));
        } else {
            for (String id : kbIds) {
                scope.add(requireBase(id, false));
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
     * 获取知识库元数据并校验归属（缺省按写权限收口）。
     * @param knowledgeBaseId 知识baseid
     * @return requireBase的结果
     */
    private Map<String, Object> requireBase(String knowledgeBaseId) {
        return requireBase(knowledgeBaseId, true);
    }

    /**
     * 获取知识库元数据并校验归属。
     * @param knowledgeBaseId 知识baseid
     * @param writable 是否要求写权限（只读出口传 假，管理员可读他人知识库）
     * @return requireBase的结果
     */
    private Map<String, Object> requireBase(String knowledgeBaseId, boolean writable) {
        Map<String, Object> base = bases.get(knowledgeBaseId);
        if (base == null) {
            /**
             * api异常。
             * @param ApiErrorCode.NOT_FOUND api错误编码.notfound
             * @param knowledgeBaseId 知识baseid
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.NOT_FOUND, "知识库不存在: " + knowledgeBaseId);
        }
        if (writable) {
            return AiMemoryOwnership.requireOwned(base, true, "知识库");
        }
        return AiMemoryOwnership.requireReadable(base, "知识库");
    }

    /**
     * 计算托管知识库的根目录：&lt;工作目录基路径&gt;/&lt;当前账号&gt;/rag/&lt;上传目录名&gt;/kb-&lt;库ID&gt;。
     *
     * <p>账号段用于**数据隔离**：同一上传目录下每个账号各自一棵子树，互不可见。
     * 工作目录基路径取全局设置 {@code system.workspaceBaseDir}；未配置时回落到上传目录配置本身
     * （行为与老版本一致），避免升级后既有知识库全部找不到文件。</p>
     *
     * @param baseId 知识库 ID
     * @return 托管根目录
     */
    private String resolveManagedRoot(String baseId) {
        return managedBase().resolve("kb-" + baseId).toString();
    }

    /**
     * 工作目录基路径；未配置时为 {@code null}。
     *
     * @return 工作目录基路径，或 null
     */
    private Path workspaceBasePath() {
        String base = globalSettingService.getValue("system", "workspaceBaseDir").orElse(null);
        if (!StringUtils.hasText(base)) {
            return null;
        }
        return Paths.get(base).toAbsolutePath().normalize();
    }

    /**
     * 当前账号的托管上传目录基路径。
     *
     * <p>有工作目录基路径时返回 {@code <基路径>/<账号>/rag/<上传目录名>}；
     * 未配置时返回上传目录本身。</p>
     *
     * @return 托管上传目录基路径
     */
    private Path managedBase() {
        String uploadDir = StringUtils.hasText(ragProperties.getUploadDir()) ? ragProperties.getUploadDir() : "./rag-uploads";
        Path workspace = workspaceBasePath();
        if (workspace == null) {
            return Paths.get(uploadDir).toAbsolutePath().normalize();
        }
        Path uploadPath = Paths.get(uploadDir).normalize();
        String leaf = uploadPath.getNameCount() == 0
                ? "rag-uploads"
                : uploadPath.getName(uploadPath.getNameCount() - 1).toString();
        return workspace.resolve(safeSegment(AiUserContext.currentUserId())).resolve("rag").resolve(leaf);
    }

    /**
     * 把账号标识净化为可安全用作单个目录名的片段。
     *
     * @param raw 原始账号标识
     * @return 只含字母数字与 . _ - 的片段；空值回落 anonymous
     */
    private String safeSegment(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "anonymous";
        }
        String cleaned = raw.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.isEmpty() ? "anonymous" : cleaned;
    }

    /**
     * 判断目标路径是否落在「任意账号的托管 RAG 目录」内，即 {@code <工作目录基路径>/<账号>/rag/...}。
     *
     * <p>必须按**所有账号**判而不是只判当前账号：否则 A 账号可以把 root_path 指到
     * {@code <基路径>/B/rag/...} 去读 B 的数据，隔离就白做了。</p>
     *
     * @param target 目标绝对路径
     * @return true 表示落在托管 RAG 区内，必须拒绝
     */
    private boolean insideAnyAccountRag(Path target) {
        Path workspace = workspaceBasePath();
        if (workspace == null || !target.startsWith(workspace)) {
            return false;
        }
        Path relative = workspace.relativize(target);
        return relative.getNameCount() >= 2 && "rag".equals(relative.getName(1).toString());
    }

    /**
     * 校验客户端自报的外部目录：托管上传目录归各自创建人，不允许挂载为他人知识库目录。
     * @param rootPath 根路径
     */
    private void assertExternalRoot(String rootPath) {
        Path target = Paths.get(rootPath).toAbsolutePath().normalize();
        Path upload = Paths.get(ragProperties.getUploadDir()).toAbsolutePath().normalize();
        if (insideAnyAccountRag(target) || target.startsWith(upload)) {
            /**
             * api异常。
             * @param ApiErrorCode.INVALID_PARAM api错误编码.invalid参数
             * @param rootPath 根路径
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "root_path 不能指向托管上传目录: " + rootPath);
        }
    }

    /**
     * 把 RAG 检索失败的原因链翻译成可操作的业务提示。
     *
     * <p>外部 embedding SDK 的异常（OpenAI 系 402/401/429 等）经 {@code RagPipeline} 包装后，
     * 报文只剩「Node execution failed: embedQuery [node=embedQuery]」，前端只能看到
     * {@code INTERNAL_ERROR}，无法判断到底是余额不足、密钥失效、被限流还是维度不符。
     * 这里按报文特征识别常见几类，并给出下一步动作。</p>
     *
     * @param error 原始异常（可为 null）
     * @return 面向用户的提示，尾部保留原始首行便于排查
     */
    private String friendlyEmbeddingError(Throwable error) {
        String raw = error == null || error.getMessage() == null ? "" : error.getMessage();
        String deepest = "";
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (StringUtils.hasText(t.getMessage())) {
                deepest = t.getMessage();
            }
        }
        String all = (raw + " " + deepest).toLowerCase();
        if (all.contains("insufficient") || all.contains("balance") || all.contains("402")
                || all.contains("欠费") || all.contains("余额")) {
            return "向量模型调用被拒：账户余额不足。请到「设置中心 → 默认模型 → RAG」更换 Embedding 密钥或提供方，"
                    + "或改用本地 onnx 引擎（免凭证）。原始信息: " + raw;
        }
        if (all.contains("401") || all.contains("unauthorized") || all.contains("invalid api key")
                || all.contains("invalid_api_key")) {
            return "向量模型调用被拒：API Key 无效或已过期。请到「设置中心 → 默认模型 → RAG → Embedding密钥」更新。原始信息: " + raw;
        }
        if (all.contains("429") || all.contains("rate limit") || all.contains("too many")) {
            return "向量模型调用被限流（429）。请稍后重试，或更换提供方。原始信息: " + raw;
        }
        if (all.contains("模型未注册")) {
            return "本地 embedding 模型未注册：" + deepest
                    + "。请到「设置中心 → 默认模型 → RAG → Embedding模型」填写注册表里的模型 id（如 bge-large-zh-embedding）。";
        }
        if (all.contains("dimension") || all.contains("维度") || all.contains("shape")) {
            return "向量维度不匹配：更换过 embedding 模型但向量库仍是旧维度。请用当前模型重新入库，或改用同维度模型。原始信息: " + raw;
        }
        return "知识库检索失败：embedding 调用未成功。请检查「设置中心 → 默认模型 → RAG」的引擎 / 模型 / 地址 / 密钥。原始信息: " + raw;
    }

    /**
     * 解析知识库根目录（目录缺失抛 NOT_FOUND）。
     * @param base 基础
     * @return resolve根的结果
     */
    private Path resolveRoot(Map<String, Object> base) {
        Path root = Paths.get(String.valueOf(base.get("root_path")));
        if (!Files.exists(root)) {
            /**
             * api异常。
             * @param ApiErrorCode.NOT_FOUND api错误编码.notfound
             * @param root 根
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.NOT_FOUND, "知识库目录不存在: " + root);
        }
        return root;
    }

    /**
     * 在根目录下安全解析相对路径，防止路径穿越。
     * @param root 根
     * @param rel rel
     * @return safeResolve的结果
     */
    private Path safeResolve(Path root, String rel) {
        Path normalizedRoot = root.normalize();
        Path resolved = rel == null || rel.isBlank()
                ? normalizedRoot
                : root.resolve(rel.replace('\\', '/')).normalize();
        if (!resolved.startsWith(normalizedRoot)) {
            /**
             * api异常。
             * @param ApiErrorCode.INVALID_PARAM api错误编码.invalid参数
             * @param rel rel
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "非法路径: " + rel);
        }
        return resolved;
    }

    /**
     * 重算文件数量、总大小与目录存在性，并写回元数据。
     * @param base 基础
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
     * @param root 根
     * @return computeStats的结果
     */
    private long[] computeStats(Path root) {
        long[] s = {0L, 0L};
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            return s;
        }
        /**
         * 尝试。
         * @param Files.walk(root 文件.walk(根
         */
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
     * @param base 基础
     * @return 转为base响应的结果
     */
    private Map<String, Object> toBaseResponse(Map<String, Object> base) {
        return new LinkedHashMap<>(base);
    }

    /**
     * 默认检索配置（嵌入 本地、rerank 关闭）。
     * @return 默认retrieval配置的结果
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
     * 规整检索阶段配置（保留 mode / 提供者_标识 / 模型）。
     * @param stage Stage
     * @return normalizeStage的结果
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
     * @param source 源
     * @param root 根
     * @return 获取源的结果
     */
    private Map<String, Object> fetchSource(Map<String, Object> base, Path root) {
        Map<String, Object> source = uncheckedCast(base.get("source"));
        Object rawEntries = source.get("entries");
        List<?> entries = rawEntries instanceof List ? (List<?>) rawEntries : new ArrayList<>();
        int fetched = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        Path snapDir = root.resolve("snapshots");
        try {
            Files.createDirectories(snapDir);
        } catch (IOException e) {
            /**
             * api异常。
             * @param ApiErrorCode.INTERNAL_ERROR api错误编码.内部错误
             * @param e.getMessage( e.获取消息(
             * @return api异常的结果
             */
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
            int maxPages = e.get("max_pages") == null ? 1 : toIntSafe(e.get("max_pages"), 1);
            if (StringUtils.isBlank(url)) {
                failed++;
                errors.add("条目缺少 url");
                continue;
            }
            try {
                // 按 max_pages 展开同域链接；1 表示只抓入口页
                List<WebPageContent> pages = maxPages > 1
                        ? webPageCollectorService.fetchCrawl(url, maxPages)
                        : List.of(webPageCollectorService.fetch(url));
                if (pages.isEmpty()) {
                    failed++;
                    errors.add(url + ": 未取到任何页面");
                    continue;
                }
                for (WebPageContent page : pages) {
                    String pageTitle = StringUtils.hasText(title) ? title : page.title();
                    String name = StringUtils.hasText(pageTitle) ? pageTitle : page.url().substring(page.url().lastIndexOf('/') + 1);
                    if (!name.contains(".")) {
                        name = "page_" + i + ".md";
                    }
                    String relPath = "snapshots/" + sanitize(name);
                    String text = page.text();
                    Files.write(snapDir.resolve(sanitize(name)), text.getBytes(StandardCharsets.UTF_8));
                    // 关键：抓下来的网页必须进向量库，否则「采集」只落盘、前端文件树能看到，
                    // 但语义检索永远搜不到——早前就是漏了这一步。
                    indexKnowledgeFile(base, relPath, text);
                    fetched++;
                }
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
     * 把任意对象安全转成 int，无法转换时返回默认值。
     *
     * @param value      原始值
     * @param defaultVal 默认值
     * @return 转换结果
     */
    private int toIntSafe(Object value, int defaultVal) {
        if (value == null) {
            return defaultVal;
        }
        try {
            if (value instanceof Number num) {
                return num.intValue();
            }
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    /**
     * 文件名净化，去除非法字符与路径分隔符。
     * @param name 名称
     * @return sanitize的结果
     */
    private String sanitize(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    /**
     * 收集目录上下文（文件名清单 + 前几个文件片段）用于 AI 生成。
     * @param root 根
     * @param maxFiles 最大文件
     * @return gather上下文的结果
     */
    private String gatherContext(Path root, int maxFiles) {
        if (!Files.exists(root)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int count = 0;
        /**
         * 尝试。
         * @param 3 3
         */
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
     * 调用 AI 对话客户端生成文本（优先指定 提供者/模型，否则默认客户端）。
     * @param prompt 提示符
     * @param providerId 提供者标识
     * @param model 模型
     * @return AIgenerate的结果
     */
    private String aiGenerate(String prompt, String providerId, String model) {
        ChatClient client = resolveClient(providerId, model);
        if (client == null) {
            /**
             * api异常。
             * @param ApiErrorCode.NO_COMPLETER api错误编码.nocompleter
             * @param 对话服务" 对话服务"
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.NO_COMPLETER, "未配置可用的 AI 对话服务");
        }
        try {
            String reply = client.chatSync(prompt);
            return reply == null ? "" : reply.trim();
        } catch (Exception e) {
            /**
             * api异常。
             * @param ApiErrorCode.UPSTREAM_ERROR api错误编码.upstream错误
             * @param e.getMessage( e.获取消息(
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "AI 生成失败: " + e.getMessage());
        }
    }

    /**
     * 按 提供者 / 模型 解析 对话客户端。
     * @param providerId 提供者标识
     * @param model 模型
     * @return resolve客户端的结果
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
     * @param root 根
     * @param file 文件
     * @return 文件entry的结果
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
     * @param root 根
     * @param entry entry
     * @return 树entry的结果
     */
    private Map<String, Object> treeEntry(Path root, Path entry) {
        boolean isDir = Files.isDirectory(entry);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", entry.getFileName() == null ? "" : entry.getFileName().toString());
        String relPath = root.relativize(entry).toString().replace('\\', '/');
        m.put("rel_path", relPath);
        m.put("is_dir", isDir);
        m.put("is_file", !isDir);
        m.put("size", isDir ? null : fileSize(entry));
        m.put("modified_at", lastModified(entry));
        // 处理状态：目录无意义；文件按「向量库里是否已有它的分片」判定。
        // 之前这里不返回状态，前端只好在 toKBFile 里硬编码 processingStatus='uploaded'，
        // 于是「已索引/解析中/失败」三个分栏恒为 0/全部/0；而树组件的点击分支
        // 又只在 completed||failed 时才 emit('view')——两处硬编码撞在一起，
        // 导致文件预览永远打不开。
        if (!isDir) {
            m.put("processing_status", relPathStatuses().getOrDefault(relPath, "uploaded"));
        }
        return m;
    }

    /**
     * 列出「相对路径 → 处理状态」的映射，状态串与前端分栏一致。
     *
     * <p>没有专门的索引状态表，判断依据只能是向量库：{@link RagClient#listDocuments}
     * 能列出已入库文档，而 docId 固定为 {@code <知识库ID>/<相对路径>}（见 {@link #indexKnowledgeFile}），
     * 剥掉第一段前缀即得相对路径。</p>
     *
     * <p>必须带上文档自身的 {@code status}：{@link RagDocument} 的取值是
     * {@code READY / PROCESSING / FAILED}，只有 READY 才算「已索引」，FAILED 要落到
     * 「失败」分栏。早先只按「路径是否出现在文档表里」判断，等于把抽取失败的文件
     * 也算成已索引，于是「失败」分栏永远是 0。</p>
     *
     * <p>同一路径可能有多条记录（重复入库）：{@code listDocuments} 按创建时间倒序，
     * 因此用 {@code putIfAbsent} 保留最新的那条。</p>
     *
     * <p><b>已知局限</b>：{@code listDocuments} 读的是进程内存里的文档登记表，
     * 进程重启后为空，而向量是持久化的。此时本方法会返回空映射（全部显示为「已上传」），
     * 属于<b>展示层的保守降级</b>——不会谎称已索引，但也不会谎称未索引失败。</p>
     *
     * <p>失败一律降级为空映射（当作全未索引），不抛异常——索引状态只用于展示，
     * 不该让「列目录」这个基础操作整体失败。结果缓存到本次「列目录」请求内，
     * 避免每个文件都去问一次向量库。</p>
     *
     * @return 相对路径到状态串（completed / failed / uploaded）的映射
     */
    private Map<String, String> relPathStatuses() {
        if (relPathStatusCache != null) {
            return relPathStatusCache;
        }
        try {
            RagClient client = ragClientManager.current();
            // 页码从 1 开始（RagClient#listDocuments 契约）；传 0 会让 (page-1)*pageSize 变负，
            // subList 抛 IndexOutOfBoundsException，被下方 catch 吞掉后全部文件都显示未索引。
            List<RagDocument> docs = client.listDocuments(1, 10000);
            Map<String, String> out = new HashMap<>();
            for (RagDocument doc : docs == null ? List.<RagDocument>of() : docs) {
                String id = doc == null ? null : doc.id();
                if (id == null || !id.contains("/")) {
                    continue;
                }
                out.putIfAbsent(id.substring(id.indexOf('/') + 1), toDisplayStatus(doc.status()));
            }
            relPathStatusCache = out;
        } catch (Exception e) {
            // 用 warn 而不是 debug：这里一旦降级成空映射，页面上所有文件都会显示「已上传」，
            // 现象与真正的「未索引」完全相同。此前传入非法页码导致 subList 抛异常被静默吞掉，
            // 排查时只能看到状态不对、看不到任何日志。
            log.warn("[Knowledge] 读取索引文档列表失败，按全未索引处理: {}", e.getMessage(), e);
            relPathStatusCache = Map.of();
        }
        return relPathStatusCache;
    }

    /**
     * 把向量库侧的文档状态翻译成前端分栏用的状态串。
     *
     * @param docStatus {@link RagDocument#status()}，取值 READY / PROCESSING / FAILED
     * @return completed（已索引）/ failed（失败）/ uploaded（解析中或尚未入库）
     */
    private static String toDisplayStatus(String docStatus) {
        if ("FAILED".equalsIgnoreCase(docStatus)) {
            return "failed";
        }
        if ("READY".equalsIgnoreCase(docStatus)) {
            return "completed";
        }
        return "uploaded";
    }

    /**
     * 递归构建目录树（限制深度）。目录条目携带 children。
     * @param root 根
     * @param dir dir
     * @param depth 深度
     * @return 构建树的结果
     */
    private List<Map<String, Object>> buildTree(Path root, Path dir, int depth) {
        List<Map<String, Object>> entries = new ArrayList<>();
        /**
         * 尝试。
         * @param Files.list(dir 文件.列表(dir
         */
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
     * @param base 基础
     * @param relPath rel路径
     * @param content 内容
     */
    /**
     * 按知识库配置构造分片器。
     *
     * <p>刻意<b>不</b>去改 {@code RagClientSetting.getTextSplitter()}：那是进程级共享实例，
     * 改了会影响所有知识库以及并发的其它请求（与 {@code ChatClient.temperature()} 同一类陷阱）。
     * 这里每次调用现造一个轻量分割器，随本次上传传入。</p>
     *
     * @param base 知识库元数据
     * @return 分片器；配置非法或创建失败时返回 null，由 RAG 客户端回退到全局分片器
     */
    private TextSplitter splitterOf(Map<String, Object> base) {
        try {
            int max = chunkInt(base, "chunk_max_size", DEFAULT_CHUNK_MAX);
            int overlap = chunkInt(base, "chunk_overlap_size", DEFAULT_CHUNK_OVERLAP);
            // 重叠不能大于分片大小，否则切分器无法推进
            if (overlap >= max) {
                overlap = Math.max(0, max / 4);
            }
            return TextSplitterProvider.create("sentence", max, overlap);
        } catch (Exception e) {
            log.warn("构造分片器失败，回退全局分片器: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从请求体或知识库记录里取分片整数，缺失/非法时用默认值，并夹到安全区间。
     *
     * @param src        请求体或知识库 Map
     * @param key        字段名
     * @param fallback   缺省值
     * @return 合法分片整数
     */
    private int chunkInt(Map<String, Object> src, String key, int fallback) {
        Object v = src == null ? null : src.get(key);
        if (v == null) {
            return fallback;
        }
        int n;
        try {
            n = v instanceof Number num ? num.intValue() : Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
        if (n < CHUNK_MIN_LIMIT) {
            return CHUNK_MIN_LIMIT;
        }
        return Math.min(n, CHUNK_MAX_LIMIT);
    }

    private void indexKnowledgeFile(Map<String, Object> base, String relPath, String content) {
        String knowledgeBaseId = String.valueOf(base.get("knowledge_base_id"));
        try {
            RagClient client = ragClientManager.current();
            if (client == null) {
                return;
            }
            // docId 必须是 <知识库ID>/<相对路径>：检索时向量库可能不回传 metadata，
            // RagPipeline 只能按 <docId>_<块号> 从 chunkId 反推 docId，
            // docId 不带路径的话引用溯源就退化成无法定位文件的 chunkId。
            // fileName 单独传纯文件名——它决定文本抽取器与 fileType，不能塞路径。
            client.uploadDocumentWithId(knowledgeBaseId + "/" + relPath,
                    fileNameOf(relPath), content.getBytes(StandardCharsets.UTF_8), splitterOf(base));
        } catch (Exception e) {
            // 索引失败不阻断文件写入（文件本身已落盘，检索侧只是查不到），
            // 但必须让管理员看得见：早前用 debug 记录，导致向量库写入彻底失败时
            // 线上只表现为「引用 0 条」，无从定位。
            log.warn("[ApiKnowledgeController] RAG 索引失败(文件已保存但检索不到) docId={}/{}, 原因: {}",
                    knowledgeBaseId, relPath, e.getMessage(), e);
        }
        // 向量化之后异步抽取实体关系写知识图谱（人物/地点/事件），由开关
        // rag_relation_extract_enabled 控制（默认开启）。放到异步是因为上传接口本身
        // 要等向量化完成才返回，再串一次 LLM 往返会明显拉长响应；抽取失败也不影响入库。
        knowledgeGraphExtractionService.submitAsync(knowledgeBaseId,
                base.get("name") == null ? null : String.valueOf(base.get("name")),
                relPath, content);
    }

    /**
     * 按前缀清理全局 RAG 客户端中属于该前缀的向量与文档（最佳努力，失败静默忽略）。
     *
     * <p>前缀语义为「知识库ID」或「知识库ID/相对路径」，由 {@link #indexKnowledgeFile}
     * 传给 pipeline 的文档名决定：删除知识库时用 {@code knowledge_base_id}，
     * 删除单文件 / 目录时用 {@code knowledge_base_id + "/" + 相对路径}。</p>
     *
     * <p><strong>注意</strong>：向量标识本身是 {@code <随机UUID>_<切片序号>}，
     * 并不携带知识库信息，所以真正生效的是「按文档名反查删除」这一步；
     * 前缀直删只对「标识即逻辑路径」的实现有效，保留作为兜底。</p>
     *
     * <p>不清理会导致向量随知识库删除而残留：向量库存量只增不减，
     * 重建同名知识库后旧向量仍参与召回。</p>
     *
     * @param idPrefix 文档名 / 向量标识前缀
     */
    private void purgeVectorsByPrefix(String idPrefix) {
        // 同步清理知识图谱关联。前缀语义与向量完全一致（知识库ID 或 知识库ID/相对路径），
        // 放在最前面是因为图谱清理不依赖向量客户端——下面 client == null 时会提前 return。
        purgeGraphByPrefix(idPrefix);
        try {
            RagClient client = ragClientManager.current();
            if (client == null) {
                return;
            }
            // 1) 先按标识前缀直删：对「向量标识即逻辑路径」的实现有效
            VectorStorage storage = client.getSetting() == null
                    ? null : client.getSetting().getVectorStorage();
            if (storage != null) {
                int removed = storage.removeByIdPrefix(idPrefix);
                if (removed > 0) {
                    log.info("[ApiKnowledgeController] 按标识前缀清理向量 {} 条, 前缀={}", removed, idPrefix);
                }
            } else {
                log.debug("[ApiKnowledgeController] 无可用向量库，跳过前缀清理, 前缀={}", idPrefix);
            }
        } catch (UnsupportedOperationException e) {
            log.debug("[ApiKnowledgeController] 当前向量库不支持按前缀删除, 前缀={}: {}", idPrefix, e.getMessage());
        } catch (Exception e) {
            log.debug("[ApiKnowledgeController] 前缀清理失败(忽略), 前缀={}: {}", idPrefix, e.getMessage());
        }
        // 2) 再按文档名反查删除：向量标识为 <随机UUID>_<切片序号>，不含逻辑路径，
        //    故前缀直删对现网无效；文档记录里的 fileName 才是「知识库ID/相对路径」，
        //    借 deleteDocument 由 pipeline 连带回收其向量。
        purgeDocumentsByNamePrefix(idPrefix);
    }

    /**
     * 按前缀清理知识图谱关联。
     *
     * <p>前缀语义与向量清理保持一致：不含 {@code /} 视为整个知识库（删该库全部图谱
     * 关联），含 {@code /} 视为单个知识点（删该知识点关联）。删除后会回收不再被任何
     * 知识点引用的孤立实体与关系，避免图上留下指向空节点的悬空边。</p>
     *
     * @param idPrefix 知识库ID 或 知识库ID/相对路径
     */
    private void purgeGraphByPrefix(String idPrefix) {
        if (StringUtils.isBlank(idPrefix)) {
            return;
        }
        try {
            int slash = idPrefix.indexOf('/');
            if (slash < 0) {
                knowledgeGraphService.deleteByKnowledgeBase(idPrefix);
            } else {
                knowledgeGraphService.deleteByKnowledgePoint(
                        idPrefix.substring(0, slash), idPrefix.substring(slash + 1));
            }
        } catch (Exception e) {
            log.debug("[ApiKnowledgeController] 知识图谱关联清理失败(忽略), 前缀={}: {}",
                    idPrefix, e.getMessage());
        }
    }

    /**
     * 按文档名（{@code knowledge_base_id} 或 {@code knowledge_base_id/相对路径} 前缀）
     * 反查并删除 RAG 文档，由 pipeline 连带回收对应向量。
     *
     * <p>向量标识形如 {@code <随机UUID>_<切片序号>}，<strong>不携带</strong>知识库信息，
     * 因此无法用向量库的前缀删除定位；知识库与向量的唯一关联是文档记录里的
     * {@code fileName}。删除知识库 / 单文件 / 目录 / 改名时都必须走这条路径。</p>
     *
     * @param namePrefix 文档名前缀
     */
    private void purgeDocumentsByNamePrefix(String namePrefix) {
        try {
            RagClient client = ragClientManager.current();
            if (client == null || StringUtils.isBlank(namePrefix)) {
                return;
            }
            int deleted = 0;
            // 分页遍历，避免单页截断导致漏删
            final int pageSize = 100;
            for (int page = 1; page <= 50; page++) {
                List<RagDocument> batch = client.listDocuments(page, pageSize);
                if (batch == null || batch.isEmpty()) {
                    break;
                }
                List<RagDocument> matched = batch.stream()
                        .filter(doc -> doc != null && doc.fileName() != null
                                && doc.fileName().startsWith(namePrefix))
                        .toList();
                for (RagDocument doc : matched) {
                    if (client.deleteDocument(doc.id())) {
                        deleted++;
                    } else {
                        log.warn("[ApiKnowledgeController] 删除文档失败(忽略), id={}", doc.id());
                    }
                }
                if (batch.size() < pageSize) {
                    break;
                }
            }
            if (deleted > 0) {
                log.info("[ApiKnowledgeController] 按文档名清理知识库文档 {} 个, 前缀={}", deleted, namePrefix);
            }
        } catch (Exception e) {
            log.debug("[ApiKnowledgeController] 文档名清理失败(忽略), 前缀={}: {}", namePrefix, e.getMessage());
        }
    }

    /**
     * 重命名后重建向量索引：先回收旧路径下的全部向量，再按新路径重新入库。
     *
     * <p>向量标识为 {@code <随机UUID>_<切片序号>}，改名后旧文档对应的向量不会自动跟随，
     * 若不处理会出现「旧路径向量残留 + 新路径无向量」：检索既召回过期内容，
     * 又永远召不到新名字的文件。目录改名时以旧文档名（{@code 旧路径/}）为前缀
     * 清理整棵子树，再递归重建子树内全部文本文件。</p>
     *
     * @param knowledge_base_id 知识库ID
     * @param oldRel           改名前的相对路径
     * @param newRel           改名后的相对路径
     */
    private void reindexVectorsAfterRename(String knowledge_base_id, String oldRel, String newRel) {
        // 目录改名时以「旧路径/」为前缀才能覆盖子树内所有文件
        purgeVectorsByPrefix(knowledge_base_id + "/" + oldRel);
        try {
            Map<String, Object> base = requireBase(knowledge_base_id);
            Path root = resolveRoot(base);
            Path target = safeResolve(root, newRel);
            if (Files.isRegularFile(target)) {
                indexKnowledgeFile(base, newRel, readTextQuietly(target));
                return;
            }
            if (!Files.isDirectory(target)) {
                return;
            }
            try (Stream<Path> walk = Files.walk(target)) {
                for (Path p : walk.filter(Files::isRegularFile).sorted().toList()) {
                    if (!isTextFile(p)) {
                        continue;
                    }
                    String relNew = root.relativize(p).toString().replace('\\', '/');
                    String text = readTextQuietly(p);
                    if (text != null) {
                        indexKnowledgeFile(base, relNew, text);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[ApiKnowledgeController] 重命名后重建向量索引失败(忽略): {}", e.getMessage());
        }
    }

    /**
     * 静默读取文本文件内容，失败时返回 空（供最佳努力的后台索引使用）。
     *
     * @param file 文件
     * @return 文件文本；非文本文件或读取失败时返回 空
     */
    private String readTextQuietly(Path file) {
        try {
            if (!isTextFile(file)) {
                return null;
            }
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 递归删除目录。
     * @param path 路径
     */
    private void deleteRecursively(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        /**
         * 尝试。
         * @param Files.walk(path 文件.walk(路径
         */
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
     * @param file 文件
     * @return 是否文本文件的结果
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
     * @param file 文件
     * @return 文件大小的结果
     */
    private long fileSize(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return 0L;
        }
    }

    /**
     * 文件最后修改时间（轮次 ms），读取失败返回 空。
     * @param file 文件
     * @return 最后一个modified的结果
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
     * @param kind 种类
     * @param targetId Targetid
     * @return requireBinding的结果
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
     * 绑定存储键（按登录用户隔离，防止他人读写同一目标的绑定）。
     * @param kind 种类
     * @param targetId Targetid
     * @return bindingKey的结果
     */
    private String bindingKey(String kind, String targetId) {
        return AiUserContext.requireUserId() + ":" + kind + ":" + targetId;
    }

    /**
     * 在单个知识库内基于关键字匹配检索命中片段。
     * @param base 基础
     * @param query 查询
     * @return 搜索入base的结果
     */
    private List<Map<String, Object>> searchInBase(Map<String, Object> base, String query) {
        Path root = Paths.get(String.valueOf(base.get("root_path")));
        List<Map<String, Object>> hits = new ArrayList<>();
        if (!Files.exists(root)) {
            return hits;
        }
        String[] terms = query.toLowerCase().split("\\s+");
        /**
         * 尝试。
         * @param Files.walk(root 文件.walk(根
         */
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
     * @param text 文本
     * @param sub sub
     * @return 数量occurrences的结果
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
     * @param content 内容
     * @param relPath rel路径
     * @return extractHeading的结果
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
     * @param content 内容
     * @param terms terms
     * @return extractSnippet的结果
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
     * 打包目录为 压缩。
     * @param sourceDir 源dir
     * @param zipFile 压缩文件
     */
    private void zipDirectory(Path sourceDir, Path zipFile) throws IOException {
        /**
         * 尝试。
         * @param ZipOutputStream(Files.newOutputStream(zipFile 压缩输出流(文件.新输出流(压缩文件
         */
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            /**
             * 尝试。
             * @param Files.walk(sourceDir 文件.walk(源dir
             */
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
     * 解压 压缩 到目标目录（防路径穿越）。
     * @param zipFile 压缩文件
     * @param destDir destdir
     */
    private void unzip(Path zipFile, Path destDir) throws IOException {
        /**
         * 尝试。
         * @param ZipInputStream(Files.newInputStream(zipFile 压缩输入流(文件.新输入流(压缩文件
         */
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path resolved = destDir.resolve(entry.getName()).normalize();
                if (!resolved.startsWith(destDir.normalize())) {
                    /**
                     * io异常。
                     * @param entry.getName( entry.获取名称(
                     * @return io异常的结果
                     */
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
     * 类型擦除安全的 映射 转换。
     * @param obj obj
     * @return uncheckedCast的结果
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> uncheckedCast(Object obj) {
        return (Map<String, Object>) obj;
    }
}

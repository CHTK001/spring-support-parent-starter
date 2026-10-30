package com.chua.starter.ai.support.controller;

import com.chua.common.support.ai.rag.RagClient;
import com.chua.common.support.ai.rag.RagClientSetting;
import com.chua.common.support.ai.rag.RagDocument;
import com.chua.common.support.ai.rag.RagResponse;
import com.chua.common.support.ai.rag.VectorService;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.network.client.ClientResponse;
import com.chua.common.support.network.client.HttpClientFactory;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.configuration.rag.RagClientManager;
import com.chua.common.support.vector.VectorStorage;
import com.chua.starter.ai.support.configuration.rag.RagProperties;
import com.chua.starter.common.support.annotations.Permission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
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

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * RAG REST 控制器。
 *
 * <p>基于 common-starter 的 {@link RagClient} / {@link RagDocument} 实现文档全生命周期管理，
 * 并借助 {@link RagClientManager} 获取当前全局 RAG 客户端，支持配置热更新。</p>
 *
 * @author CH
 * @since 4.0.0
 */
@Slf4j
@RestController
@RequestMapping("/v2/rag")
@Tag(name = "RAG 知识库", description = "RAG 文档管理、问答和配置接口")
public class RagController {

    /**
     * 全局 RAG 客户端管理器
    */
    private final RagClientManager ragClientManager;

    /**
     * RAG 配置属性
    */
    private final RagProperties ragProperties;

    /**
     * 创建 RAG 控制器实例。
     *
     * @param ragClientManager 全局 RAG 客户端管理器
     * @param ragProperties    RAG 配置属性
     */
    public RagController(RagClientManager ragClientManager, RagProperties ragProperties) {
        this.ragClientManager = ragClientManager;
        this.ragProperties = ragProperties;
    }

    /**
     * 获取当前全局 RAG 客户端。
     *
     * @return 当前 RAG 客户端；尚未初始化时为 null
     */
    private RagClient ragClient() {
        return ragClientManager.current();
    }

    // ── 文档管理 ─────────────────────────────────────────

    /**
     * 上传文档并自动索引到知识库。
     *
     * <p>先做非空与 MIME 白名单校验，再交由 RAG 客户端解析、切分并写入向量存储。</p>
     *
     * @param file      上传的文件
     * @param libraryId 目标知识库 ID，为空时使用默认知识库
     * @return 索引结果；校验或索引失败时返回错误信息
     */
    @Permission("admin")
    @Operation(summary = "上传文档", description = "上传文件并自动索引到知识库")
    @PostMapping(value = "/document/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ReturnResult<RagDocument> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String libraryId) {
        if (file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            return ReturnResult.error("文件名不能为空");
        }
        // 文件类型白名单校验，防止上传恶意可执行文件
        String contentType = file.getContentType();
        if (contentType != null && !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            return ReturnResult.error("不支持的文件类型: " + contentType);
        }
        try {
            RagDocument doc = ragClient().uploadDocument(fileName, file.getBytes());
            if ("FAILED".equals(doc.status())) {
                return ReturnResult.error(doc.errorMessage());
            }
            return ReturnResult.ok(doc);
        } catch (IOException e) {
            log.error("[RagController] 上传文件失败: {}", e.getMessage());
            return ReturnResult.error("文件读取失败: " + e.getMessage());
        }
    }

    /**
     * 分页查询已上传文档。
     *
     * <p>分页参数做上下界收敛（page ≥ 1，1 ≤ pageSize ≤ 100）；总数取
     * {@code documentCount} 而非当前页条数，保证前端分页正确。</p>
     *
     * @param page      页码，从 1 开始
     * @param pageSize  每页条数，最大 100
     * @param libraryId 知识库 ID，为空时查询全部
     * @return 含 records / total / page / pageSize 的分页结果
     */
    @Permission("admin")
    @Operation(summary = "文档列表", description = "分页查询已上传文档")
    @GetMapping("/document/list")
    public ReturnResult<Map<String, Object>> listDocuments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String libraryId) {
        if (page < 1) {
            page = 1;
        }
        if (pageSize < 1) {
            pageSize = 20;
        }
        if (pageSize > 100) {
            pageSize = 100;
        }

        List<RagDocument> docs = ragClient().listDocuments(page, pageSize);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", docs);
        // 使用真实总数（documentCount），而非当前页 docs.size()，保证前端分页正确
        result.put("total", ragClient().documentCount());
        result.put("page", page);
        result.put("pageSize", pageSize);
        return ReturnResult.ok(result);
    }

    /**
     * 删除文档及其向量索引。
     *
     * @param id 文档 ID
     * @return 删除成功返回 true；文档不存在时返回错误信息
     */
    @Permission("admin")
    @Operation(summary = "删除文档", description = "删除文档及其向量索引")
    @DeleteMapping("/document/{id}")
    public ReturnResult<Boolean> deleteDocument(@PathVariable String id) {
        boolean deleted = ragClient().deleteDocument(id);
        return deleted ? ReturnResult.ok(true) : ReturnResult.error("文档不存在");
    }

    /**
     * 预览文档原始文本内容（仅限文本文件）。
     *
     * @param id 文档 ID
     * @return 文档文本内容；不可读取时返回错误信息
     */
    @Permission("admin")
    @Operation(summary = "文档内容预览", description = "读取文档原始文本内容（仅限文本文件）")
    @GetMapping("/document/{id}/content")
    public ReturnResult<String> getDocumentContent(@PathVariable String id) {
        String content = ragClient().readDocumentContent(id);
        if (content == null || content.isEmpty()) {
            return ReturnResult.error("文档内容不可读取或文件不存在");
        }
        return ReturnResult.ok(content);
    }

    /**
     * 读取来源图片的原始字节（供前端渲染缩略图 / 预览）。
     * <p>图片类文档入库后，查询响应的 {@code sources[].imageUrl} 指向本端点，
     * 前端据此展示命中来源的原图。文本类文档访问本端点返回 404。</p>
     *
     * @param id 文档 ID（图片类文档）
     * @return 图片字节流，Content-Type 按扩展名设置；非图片或不存在时返回 404
     */
    @Permission("admin")
    @Operation(summary = "来源图片", description = "读取图片类文档的原始字节（供 RAG 查询来源渲染）")
    @GetMapping("/document/{id}/image")
    public org.springframework.http.ResponseEntity<byte[]> getDocumentImage(@PathVariable String id) {
        byte[] data = ragClient().readDocumentBytes(id);
        if (data == null || data.length == 0) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }
        String contentType = resolveImageContentType(id);
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentLength(data.length);
        return new org.springframework.http.ResponseEntity<>(data, headers, 200);
    }

    /**
     * 按文档 ID 前缀推断图片 Content-Type。
     *
     * @param docId 文档 ID
     * @return MIME 类型（默认 image/png）
     */
    private String resolveImageContentType(String docId) {
        String lower = docId == null ? "" : docId.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".bmp")) {
            return "image/bmp";
        }
        return "image/png";
    }

    // ── 问答 ─────────────────────────────────────────────

    /**
     * 基于已索引文档进行 RAG 问答。
     *
     * @param body 请求体，包含 query（必填）、topK（可选）、threshold（可选，0~1）
     * @return 含 answer 与 sources（documentId / content / score）的响应
     */
    @Permission("admin")
    @Operation(summary = "RAG 问答", description = "基于已索引文档进行问答，返回答案和引用来源")
    @PostMapping("/query")
    public ReturnResult<Map<String, Object>> query(
            @RequestBody Map<String, Object> body) {
        String query = body.getOrDefault("query", "").toString();
        if (StringUtils.isBlank(query)) {
            return ReturnResult.error("查询内容不能为空");
        }

 // 安全解析 topk，非法值回退默认配置，避免 数字格式化异常
        int topK = parsePositiveInt(body.get("topK"), ragProperties.getDefaultTopK());
 // 安全解析 阈值，限制在 0~1 区间
        double threshold = parseThreshold(body.get("threshold"), resolveDefaultThreshold());

        try {
            RagResponse response = ragClient().query(query, topK, threshold);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("answer", response.answer());
            resp.put("sources", response.sources().stream().map(s -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("documentId", s.documentId());
                item.put("content", s.content());
                item.put("score", s.score());
                // 图片来源标记 + 图片回读地址（前端据此渲染缩略图 / 预览）
                item.put("isImage", s.isImage());
                if (s.isImage()) {
                    item.put("imageUrl", "/v2/rag/document/" + s.documentId() + "/content");
                }
                return item;
            }).toList());
            return ReturnResult.ok(resp);
        } catch (Exception e) {
            log.error("[RagController] 问答失败: {}", e.getMessage());
            return ReturnResult.error("问答失败: " + e.getMessage());
        }
    }

    // ── 状态与管理 ───────────────────────────────────────

    /**
     * 获取 RAG 服务运行时诊断信息。
     *
     * <p>与 {@code GET /v2/rag/status}（面向控制台的可用性探测）分开：这里输出引擎、上传目录、
     * 生效参数与文档数，用于排障；两者共用同一路径会触发 Spring 的 Ambiguous handler methods。</p>
     *
     * @return 含引擎、上传目录、默认 topK / 阈值、客户端类型、文档数与向量库实际实例的状态信息
     */
    @Operation(summary = "运行时诊断", description = "获取 RAG 客户端的生效参数与文档数（排障用）")
    @GetMapping("/runtime-status")
    public ReturnResult<Map<String, Object>> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        RagClient client = ragClient();
        RagClientSetting runtime = client != null ? client.getSetting() : null;
        int topK = runtime != null ? runtime.getTopK() : ragProperties.getDefaultTopK();
        double threshold = resolveDefaultThreshold();
        String uploadDir = runtime != null ? runtime.getUploadDir() : ragProperties.getUploadDir();
        // 向量库同时回报「配置名」与「实际实例」：配置名可能被拼写错误或降级，
        // 实际实例类名 + 维度 + 存量条数才是判断 SPI 是否真的生效的依据
        VectorStorage storage = runtime != null ? runtime.getVectorStorage() : null;
        // 实际生效的引擎取自 ragClientManager 的运行期描述（读 DB 配置），而不是 RagProperties 的
        // 代码默认值：后者在已配置真实引擎时仍报 memory，导致排障时误判。
        Map<String, String> runtimeDesc = ragClientManager.describe();
        String configuredEngine = runtimeDesc.get("embedding_model");
        String engineName = StringUtils.isBlank(configuredEngine)
                ? ragProperties.getEmbeddingEngine() : configuredEngine;
        status.put("engine", engineName);
        status.put("llmProvider", "global (使用全局 LLM 配置)");
        status.put("uploadDir", uploadDir);
        status.put("defaultTopK", topK);
        status.put("defaultSimilarityThreshold", threshold);
        status.put("clientType", client instanceof com.chua.common.support.ai.rag.RagPipeline ? "pipeline" : "memory");
        status.put("ragClientReady", ragClient() != null);
        status.put("documentCount", ragClient() != null ? ragClient().documentCount() : 0);
        status.put("vectorStorage", runtimeDesc.get("vector_storage"));
        status.put("vectorStorageImpl", storage != null ? storage.getClass().getSimpleName() : null);
        status.put("vectorStorageDimension", storage != null ? storage.dimension() : null);
        status.put("vectorStorageSize", storage != null ? storage.size() : null);
        // 占位 embedding 不具备语义：其伪向量对任意两条不同文本的余弦恒在 0.63~0.69，
        // 与默认阈值 0.7 重叠，导致语义检索恒定 0 命中。这不是配置错误，但必须显式告知，
        // 否则排障时只能看到「0 命中」而无任何线索。
        boolean placeholder = "memory".equalsIgnoreCase(engineName);
        status.put("semanticRetrievalAvailable", !placeholder);
        if (placeholder) {
            status.put("engineWarning",
                    "当前 embedding 引擎为占位实现 memory，不具备语义能力：任意两条不同文本的余弦恒在 0.63~0.69，"
                            + "与当前阈值 " + threshold + " 重叠，语义检索将恒定 0 命中。"
                            + "如需真实语义召回，请在「设置中心 → 默认模型 → RAG」选择 Embedding 模型，"
                            + "引擎会由所选模型自动推导，不需要手动指定。");
        }
        return ReturnResult.ok(status);
    }

    /**
     * 清空向量存储并重新索引全部文档。
     *
     * @return 重新索引的文档数量；失败时返回错误信息
     */
    @Permission("admin")
    @Operation(summary = "重置索引", description = "清空向量存储并重新索引所有文档")
    @PostMapping("/reset")
    public ReturnResult<Integer> reset() {
        try {
            int count = ragClient().reindex();
            return ReturnResult.ok(count, "重新索引完成，共 " + count + " 个文档");
        } catch (Exception e) {
            return ReturnResult.error("重新索引失败: " + e.getMessage());
        }
    }

 // ── 嵌入 引擎发现 ─────────────────────────────────

    /**
     * 列出所有可用的 Embedding 引擎。
     *
     * <p>引擎来源有两处，取并集：</p>
     * <ol>
     *   <li>{@code VectorService} SPI 扩展（远端/自定义实现，当前部署下可能为空）</li>
     *   <li>{@link RagClientManager} 的本地推理引擎集合（{@code memory}/{@code onnx}/
     *       {@code bge}/{@code minilm}/{@code wemm}）——这些是 RAG 实际能直接使用的引擎，
     *       也是 {@code /v2/rag/runtime-status} 里 {@code engine} 字段的取值来源</li>
     * </ol>
     *
     * <p>只查 SPI 会得到空列表：该 SPI 没有任何实现类，引擎实际由本地白名单构建。</p>
     *
     * @return 引擎列表（value / label 形式），按名称排序
     */
    @Operation(summary = "Embedding 引擎列表", description = "获取所有可用的 Embedding 引擎（本地推理引擎 + VectorService SPI 扩展）")
    @GetMapping("/embedding-engines")
    public ReturnResult<List<Map<String, String>>> listEmbeddingEngines() {
        Set<String> engines = new TreeSet<>(ServiceProvider.of(VectorService.class).getExtensions());
        engines.addAll(ragClientManager.localEmbeddingEngines());
        List<Map<String, String>> result = new ArrayList<>(engines.size());
        for (String name : engines) {
            Map<String, String> item = new LinkedHashMap<>(4);
            item.put("value", name);
            item.put("label", name);
            item.put("local", String.valueOf(ragClientManager.localEmbeddingEngines().contains(name)));
            result.add(item);
        }
        return ReturnResult.ok(result);
    }

    // ── 向量浏览 ───────────────────────────────────────────

    /**
     * 分页查看已存储的向量数据（不返回原始向量值）。
     *
     * @param page     页码，从 1 开始
     * @param pageSize 每页条数，最大 100
     * @return 含 records / total / page / pageSize 的分页结果
     */
    @Operation(summary = "向量列表", description = "分页查看已存储的向量数据（不含原始向量值）")
    @GetMapping("/vector/list")
    public ReturnResult<Map<String, Object>> listVectors(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            if (page < 1) {
                page = 1;
            }
            if (pageSize < 1) {
                pageSize = 20;
            }
            if (pageSize > 100) {
                pageSize = 100;
            }

            Map<String, Object> result = new LinkedHashMap<>();
            // VectorStorage SPI 只提供 size()/search()，没有枚举全部向量的能力，
            // 因此 records 无法真实填充；total 取自向量库实际存量，不再硬编码 0。
            RagClient client = ragClient();
            RagClientSetting runtime = client != null ? client.getSetting() : null;
            VectorStorage storage = runtime != null ? runtime.getVectorStorage() : null;
            int total = storage != null ? storage.size() : 0;
            result.put("records", Collections.emptyList());
            result.put("total", total);
            result.put("page", page);
            result.put("pageSize", pageSize);
            result.put("enumerable", false);
            result.put("note", "VectorStorage SPI 未提供枚举接口，records 恒为空；请改用 /v2/rag/vector/search 查看命中向量");
            return ReturnResult.ok(result);
        } catch (Exception e) {
            return ReturnResult.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 按文本检索相似向量（仅做向量检索，不调用 LLM）。
     *
     * <p>阈值支持两种传参方式，优先级为 请求参数 threshold 高于 JSON 请求体 threshold，
     * 均未提供时才回退全局默认相似度阈值 {@code rag.defaultSimilarityThreshold}。
     * 阈值限制在 0~1 区间，非法或越界时回退默认值，保证调用方可用阈值排查召回问题。</p>
     *
     * @param request   可选的 JSON 请求体，含 threshold 字段
     * @param query     检索文本，不能为空
     * @param topK      返回条数
     * @param threshold 可选的相似度阈值，不传则使用全局默认值
     * @return 命中的向量条目（documentId / score / content）
     */
    @Operation(summary = "向量搜索", description = "按文本检索相似向量（仅搜索，不走 LLM）")
    @GetMapping("/vector/search")
    public ReturnResult<List<Map<String, Object>>> searchVectors(
            @RequestBody(required = false) Map<String, Object> request,
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int topK,
            @RequestParam(required = false) Double threshold) {
        if (query.isBlank()) {
            return ReturnResult.error("查询内容不能为空");
        }
        // 夹取 topK，避免非法值透传到向量库
        if (topK < 1) {
            topK = 1;
        }
        if (topK > 100) {
            topK = 100;
        }
        try {
            // 向量搜索阈值默认取运行期生效值（与 /v2/rag/query 同源），
            // 避免与 /v2/rag/runtime-status 报告的阈值口径不一致
            double defThreshold = resolveDefaultThreshold();
            // 阈值优先级：显式请求参数 > JSON 请求体 > 全局默认值
            Object rawThreshold = threshold;
            if (rawThreshold == null && request != null) {
                rawThreshold = request.get("threshold");
            }
            double finalThreshold = parseThreshold(rawThreshold, defThreshold);
            // 纯检索：不调用 LLM，故上游不可用/限流时仍能校验召回效果
            RagResponse response = ragClient().search(query, topK, finalThreshold);
            List<Map<String, Object>> items = response.sources().stream().map(s -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("documentId", s.documentId());
                item.put("score", s.score());
                item.put("content", s.content());
                return item;
            }).toList();
            return ReturnResult.ok(items);
        } catch (Exception e) {
            return ReturnResult.error("搜索失败: " + e.getMessage());
        }
    }

    /**
     * 从指定 URL 抓取网页内容并添加为 RAG 文档。
     *
     * <p>先做 SSRF 防护校验（协议白名单 + 内网地址拦截），抓取后去除 HTML 标签并按上限截断。</p>
     *
     * @param request URL 抓取请求，包含目标 url
     * @return 索引结果；URL 非法或抓取失败时返回错误信息
     */
    @Permission("admin")
    @Operation(summary = "URL 抓取文档", description = "从指定 URL 抓取网页内容并添加为 RAG 文档")
    @PostMapping("/document/url")
    public ReturnResult<RagDocument> addDocumentByUrl(@RequestBody com.chua.starter.ai.support.model.UrlDocumentRequest request) {
        String url = request.url();
        if (StringUtils.isBlank(url)) {
            return ReturnResult.error("URL 不能为空");
        }
        // SSRF 防护：协议白名单 + 内网地址拦截
        try {
            if (!isSafeUrl(url)) {
                return ReturnResult.error("URL 不合法：仅允许 http/https 公网地址");
            }
        } catch (Exception e) {
            log.error("[RagController] URL 校验失败: {}", e.getMessage());
            return ReturnResult.error("URL 不合法: " + e.getMessage());
        }
        try {
 // 复用 common-starter 统一 HTTP 客户端（带连接池/超时管理）
            ClientResponse response = HttpClientFactory.getClient().get(url);
            if (response == null || !response.isSuccess()) {
                int code = response != null ? response.getStatusCode() : -1;
                return ReturnResult.error("URL 抓取失败，HTTP 状态码: " + code);
            }
            byte[] contentBytes = response.getBody();
            if (contentBytes == null || contentBytes.length == 0) {
                return ReturnResult.error("URL 内容为空");
            }
            // 去除 HTML 标签后作为文本内容
            String content = new String(contentBytes, StandardCharsets.UTF_8)
                    .replaceAll("<[^>]+>", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
            if (content.length() > URL_CONTENT_MAX_LENGTH) {
                content = content.substring(0, URL_CONTENT_MAX_LENGTH) + "...";
            }
            // 使用文件名：URL 的最后一段，无扩展名时回退
            String fileName = url.substring(url.lastIndexOf('/') + 1);
            if (fileName.isBlank() || !fileName.contains(".")) {
                fileName = "webpage_" + System.currentTimeMillis() + ".txt";
            }
            RagDocument doc = ragClient().uploadDocument(fileName, content.getBytes(StandardCharsets.UTF_8));
            if ("FAILED".equals(doc.status())) {
                return ReturnResult.error(doc.errorMessage());
            }
            return ReturnResult.ok(doc);
        } catch (Exception e) {
            log.error("[RagController] URL 抓取失败: {}", e.getMessage());
            return ReturnResult.error("URL 抓取失败: " + e.getMessage());
        }
    }

    /**
     * URL 抓取内容最大长度。
     */
    private static final int URL_CONTENT_MAX_LENGTH = 2000;

    /**
     * 合法协议白名单。
     */
    private static final Set<String> SAFE_PROTOCOLS = Set.of("http", "https");

    /**
     * 文档上传允许的 MIME 类型白名单，防止上传可执行文件等危险内容。
     */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "text/plain", "text/markdown", "text/html", "text/css", "text/xml",
            "application/json", "application/pdf", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    );

    /**
     * SSRF 防护：校验 URL 协议与解析后的目标地址。 <p>仅允许 http/https 协议且目标主机解析为非内网、非回环、非链路本地的公网地址， 防止服务端被利用访问内网资源。</p>
     * @param url 待校验的 URL
     * @return 合法返回 true，非法抛出异常
     * @throws Exception 异常
     * @throws URISyntaxException URL 格式非法
     * @throws java.net.UnknownHostException 主机无法解析
    */
    private boolean isSafeUrl(String url) throws Exception {
        URI uri = new URI(url);
        String protocol = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!SAFE_PROTOCOLS.contains(protocol)) {
            return false;
        }
        String host = uri.getHost();
        if (StringUtils.isBlank(host)) {
            return false;
        }
        // 域名解析为全部 IP 逐个校验，任一命中内网即拒绝
        InetAddress[] addresses = InetAddress.getAllByName(host);
        for (InetAddress address : addresses) {
            if (isInternalAddress(address)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断 IP 地址是否为内网/回环/链路本地等不可信地址。
     *
     * @param address IP 地址
     * @return 命中内网等不可信地址返回 true
     */
    private boolean isInternalAddress(InetAddress address) {
        if (address == null || !(address instanceof Inet4Address)
                && !(address instanceof Inet6Address)) {
            return true;
        }
        if (address.isLoopbackAddress() || address.isAnyLocalAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
            return true;
        }
        // 兼容部分 JVM 上下文：多播地址不可作为抓取目标
        return address.isMulticastAddress();
    }

    /**
     * 安全解析正整数。
     *
     * <p>避免对用户输入直接调用 {@code Integer.parseInt} 导致 NumberFormatException。</p>
     *
     * @param value  原始值（可能是 字符串/数字/空）
     * @param defVal 解析失败时返回的默认值
     * @return 解析后的正整数值，非法时返回默认值
     */
    private int parsePositiveInt(Object value, int defVal) {
        if (value == null) {
            return defVal;
        }
        try {
            int parsed = (value instanceof Number num) ? num.intValue() : Integer.parseInt(value.toString());
            return parsed > 0 ? parsed : defVal;
        } catch (NumberFormatException e) {
            log.warn("[RagController] topK 参数非法: {}", value);
            return defVal;
        }
    }

    /**
     * 安全解析相似度阈值，限制在 0~1 区间。
     *
     * @param value  原始值（可能是 字符串/数字/空）
     * @param defVal 解析失败时返回的默认值
     * @return 解析后的阈值，非法或越界时返回默认值
     */
    private double parseThreshold(Object value, double defVal) {
        if (value == null) {
            return defVal;
        }
        try {
            double parsed = (value instanceof Number num) ? num.doubleValue() : Double.parseDouble(value.toString());
            if (parsed < 0 || parsed > 1) {
                return defVal;
            }
            return parsed;
        } catch (NumberFormatException e) {
            log.warn("[RagController] threshold 参数非法: {}", value);
            return defVal;
        }
    }

    /**
     * 解析当前生效的默认相似度阈值。
     *
     * <p>优先取运行期 {@link RagClient} 上真实生效的值（来自 {@code sys_ai_global_setting}
     * 的 {@code rag_similarity_threshold}），客户端尚未就绪时才回退代码默认配置。
     * 早前 {@code /v2/rag/query} 与 {@code /v2/rag/vector/search} 都直接用
     * {@code ragProperties} 的代码默认值，导致运维在管理端调整阈值后不传参的调用
     * 仍按旧阈值执行，且与 {@code /v2/rag/runtime-status} 报告的值不一致。</p>
     *
     * @return 生效中的默认相似度阈值
     */
    private double resolveDefaultThreshold() {
        RagClient client = ragClient();
        RagClientSetting runtime = client != null ? client.getSetting() : null;
        if (runtime != null) {
            return runtime.getSimilarityThreshold();
        }
        return ragProperties.getDefaultSimilarityThreshold();
    }
}


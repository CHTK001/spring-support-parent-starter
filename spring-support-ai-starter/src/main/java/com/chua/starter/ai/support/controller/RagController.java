package com.chua.starter.ai.support.controller;

import com.chua.common.support.ai.rag.RagClient;
import com.chua.common.support.ai.rag.RagDocument;
import com.chua.common.support.ai.rag.RagResponse;
import com.chua.common.support.ai.rag.VectorService;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.network.client.ClientResponse;
import com.chua.common.support.network.client.HttpClientFactory;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.configuration.rag.RagClientManager;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * RAG REST 控制器——基于 common-starter 的 RagClient + RagDocumentLifeCycle 真实实现。
 * <p>使用 {@link RagClientManager} 获取当前全局 RAG 客户端，支持配置热更新。</p>
 *
 * @author CH
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

    public RagController(RagClientManager ragClientManager, RagProperties ragProperties) {
        this.ragClientManager = ragClientManager;
        this.ragProperties = ragProperties;
    }

    /**
     * 获取当前全局 RAG 客户端（可能为 null）。
     */
    private RagClient ragClient() {
        return ragClientManager.current();
    }

    // ── 文档管理 ─────────────────────────────────────────

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

    @Permission("admin")
    @Operation(summary = "文档列表", description = "分页查询已上传文档")
    @GetMapping("/document/list")
    public ReturnResult<Map<String, Object>> listDocuments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String libraryId) {
        if (page < 1) page = 1;
        if (pageSize < 1) pageSize = 20;
        if (pageSize > 100) pageSize = 100;

        List<RagDocument> docs = ragClient().listDocuments(page, pageSize);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", docs);
        // 使用真实总数（documentCount），而非当前页 docs.size()，保证前端分页正确
        result.put("total", ragClient().documentCount());
        result.put("page", page);
        result.put("pageSize", pageSize);
        return ReturnResult.ok(result);
    }

    @Permission("admin")
    @Operation(summary = "删除文档", description = "删除文档及其向量索引")
    @DeleteMapping("/document/{id}")
    public ReturnResult<Boolean> deleteDocument(@PathVariable String id) {
        boolean deleted = ragClient().deleteDocument(id);
        return deleted ? ReturnResult.ok(true) : ReturnResult.error("文档不存在");
    }

    @Permission("admin")
    @Operation(summary = "文档内容预览", description = "读取文档原始文本内容（仅限文本文件）")
    @GetMapping("/document/{id}/content")
    public ReturnResult<String> getDocumentContent(@PathVariable String id) {
        String content = ragClient().readDocumentContent(id);
        if (content == null) {
            return ReturnResult.error("文档内容不可读取或文件不存在");
        }
        return ReturnResult.ok(content);
    }

    // ── 问答 ─────────────────────────────────────────────

    @Permission("admin")
    @Operation(summary = "RAG 问答", description = "基于已索引文档进行问答，返回答案和引用来源")
    @PostMapping("/query")
    public ReturnResult<Map<String, Object>> query(
            @RequestBody Map<String, Object> body) {
        String query = body.getOrDefault("query", "").toString();
        if (StringUtils.isBlank(query)) {
            return ReturnResult.error("查询内容不能为空");
        }

        // 安全解析 topK，非法值回退默认配置，避免 NumberFormatException
        int topK = parsePositiveInt(body.get("topK"), ragProperties.getDefaultTopK());
        // 安全解析 threshold，限制在 0~1 区间
        double threshold = parseThreshold(body.get("threshold"), ragProperties.getDefaultSimilarityThreshold());

        try {
            RagResponse response = ragClient().query(query, topK, threshold);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("answer", response.answer());
            resp.put("sources", response.sources().stream().map(s -> Map.of(
                    "documentId", s.documentId(),
                    "content", s.content(),
                    "score", s.score()
            )).toList());
            return ReturnResult.ok(resp);
        } catch (Exception e) {
            log.error("[RagController] 问答失败: {}", e.getMessage());
            return ReturnResult.error("问答失败: " + e.getMessage());
        }
    }

    // ── 状态与管理 ───────────────────────────────────────

    @Operation(summary = "服务状态", description = "获取 RAG 服务运行状态")
    @GetMapping("/status")
    public ReturnResult<Map<String, Object>> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("engine", ragProperties.getEmbeddingEngine());
        status.put("llmProvider", "global (使用全局 LLM 配置)");
        status.put("uploadDir", ragProperties.getUploadDir());
        status.put("defaultTopK", ragProperties.getDefaultTopK());
        status.put("defaultSimilarityThreshold", ragProperties.getDefaultSimilarityThreshold());
        status.put("ragClientReady", ragClient() != null);
        status.put("documentCount", ragClient() != null ? ragClient().documentCount() : 0);
        return ReturnResult.ok(status);
    }

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

    // ── Embedding 引擎发现 ─────────────────────────────────

    @Operation(summary = "Embedding 引擎列表", description = "获取所有可用的 Embedding 引擎（VectorService SPI 实现）")
    @GetMapping("/embedding-engines")
    public ReturnResult<List<Map<String, String>>> listEmbeddingEngines() {
        Set<String> engines = ServiceProvider.of(VectorService.class).getExtensions();
        List<Map<String, String>> result = engines.stream().map(name -> {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("value", name);
            item.put("label", name);
            return item;
        }).toList();
        return ReturnResult.ok(result);
    }

    // ── 向量浏览 ───────────────────────────────────────────

    @Operation(summary = "向量列表", description = "分页查看已存储的向量数据（不含原始向量值）")
    @GetMapping("/vector/list")
    public ReturnResult<Map<String, Object>> listVectors(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            if (page < 1) page = 1;
            if (pageSize < 1) pageSize = 20;
            if (pageSize > 100) pageSize = 100;

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("records", Collections.emptyList());
            result.put("total", 0);
            result.put("page", page);
            result.put("pageSize", pageSize);
            return ReturnResult.ok(result);
        } catch (Exception e) {
            return ReturnResult.error("查询失败: " + e.getMessage());
        }
    }

    @Operation(summary = "向量搜索", description = "按文本检索相似向量（仅搜索，不走 LLM）")
    @GetMapping("/vector/search")
    public ReturnResult<List<Map<String, Object>>> searchVectors(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int topK) {
        if (query.isBlank()) {
            return ReturnResult.error("查询内容不能为空");
        }
        try {
            // 向量搜索阈值默认取配置值，避免硬编码
            double threshold = ragProperties.getDefaultSimilarityThreshold();
            RagResponse response = ragClient().query(query, topK, threshold);
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

    @Permission("admin")
    @Operation(summary = "URL 抓取文档", description = "从指定 URL 抓取网页内容并添加为 RAG 文档")
    @PostMapping("/document/url")
    public ReturnResult<RagDocument> addDocumentByUrl(@RequestBody com.chua.starter.ai.support.model.UrlDocumentRequest request) {
        String url = request.getUrl();
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
     * SSRF 防护：校验 URL 协议与解析后的目标地址。
     *
     * <p>仅允许 http/https 协议且目标主机解析为非内网、非回环、非链路本地的公网地址，
     * 防止服务端被利用访问内网资源。</p>
     *
     * @param url 待校验的 URL
     * @return 合法返回 true，非法抛出异常
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
     * @param value  原始值（可能是 String/Number/null）
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
     * @param value  原始值（可能是 String/Number/null）
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
}




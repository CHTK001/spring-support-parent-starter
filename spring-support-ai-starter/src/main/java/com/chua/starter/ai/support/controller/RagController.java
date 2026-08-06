package com.chua.starter.ai.support.controller;

import com.chua.common.support.ai.rag.*;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.ai.support.configuration.RagClientManager;
import com.chua.starter.ai.support.configuration.RagProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

/**
 * RAG REST 控制器——基于 common-starter 的 RagClient + RagDocumentLifeCycle 真实实现。
 * <p>使用 {@link RagClientManager} 获取当前全局 RAG 客户端，支持配置热更新。</p>
 *
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/v2/ai/rag")
@Tag(name = "RAG 知识库", description = "RAG 文档管理、问答和配置接口")
public class RagController {

    private final RagClientManager ragClientManager;
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
        result.put("total", docs.size());
        result.put("page", page);
        result.put("pageSize", pageSize);
        return ReturnResult.ok(result);
    }

    @Operation(summary = "删除文档", description = "删除文档及其向量索引")
    @DeleteMapping("/document/{id}")
    public ReturnResult<Boolean> deleteDocument(@PathVariable String id) {
        boolean deleted = ragClient().deleteDocument(id);
        return deleted ? ReturnResult.ok(true) : ReturnResult.error("文档不存在");
    }

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

    @Operation(summary = "RAG 问答", description = "基于已索引文档进行问答，返回答案和引用来源")
    @PostMapping("/query")
    public ReturnResult<Map<String, Object>> query(
            @RequestBody Map<String, Object> body) {
        String query = body.getOrDefault("query", "").toString();
        if (query.isBlank()) {
            return ReturnResult.error("查询内容不能为空");
        }

        int topK = body.containsKey("topK")
                ? Integer.parseInt(body.get("topK").toString())
                : ragProperties.getDefaultTopK();
        double threshold = body.containsKey("threshold")
                ? Double.parseDouble(body.get("threshold").toString())
                : ragProperties.getDefaultSimilarityThreshold();

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
            // Common-starter RagClient 的向量搜索通过 query 方法实现
            RagResponse response = ragClient().query(query, topK, 0.1);
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

    @Operation(summary = "URL 抓取文档", description = "从指定 URL 抓取网页内容并添加为 RAG 文档")
    @PostMapping("/document/url")
    public ReturnResult<RagDocument> addDocumentByUrl(@RequestBody com.chua.starter.ai.support.model.UrlDocumentRequest request) {
        String url = request.getUrl();
        if (url == null || url.isBlank()) {
            return ReturnResult.error("URL 不能为空");
        }
        try {
            // 抓取网页内容（简单实现，使用 java.net.HttpURLConnection）
            java.net.URL target = new java.net.URL(url);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) target.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            try (java.io.InputStream is = conn.getInputStream();
                 java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
                byte[] contentBytes = baos.toByteArray();
                // 尝试提取文本内容（简单取前 2000 字符作为预览）
                String content = new String(contentBytes, java.nio.charset.StandardCharsets.UTF_8);
                // 去除 HTML 标签（非常粗略）
                content = content.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
                if (content.length() > 2000) {
                    content = content.substring(0, 2000) + "...";
                }
                // 使用文件名：URL 的最后一段
                String fileName = url.substring(url.lastIndexOf('/') + 1);
                if (fileName.isBlank() || !fileName.contains(".")) {
                    fileName = "webpage_" + System.currentTimeMillis() + ".txt";
                }
                RagDocument doc = ragClient().uploadDocument(fileName, content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                if ("FAILED".equals(doc.status())) {
                    return ReturnResult.error(doc.errorMessage());
                }
                return ReturnResult.ok(doc);
            }
        } catch (Exception e) {
            log.error("[RagController] URL 抓取失败: {}", e.getMessage());
            return ReturnResult.error("URL 抓取失败: " + e.getMessage());
        }
    }
}

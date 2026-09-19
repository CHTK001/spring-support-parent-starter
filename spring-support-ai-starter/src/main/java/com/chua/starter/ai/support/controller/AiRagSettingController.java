package com.chua.starter.ai.support.controller;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.embedding.EmbeddingClient;
import com.chua.common.support.ai.splitter.TextSplitterProvider;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.vector.VectorStorageProvider;
import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.starter.ai.support.model.AiModelDTO;
import com.chua.starter.ai.support.service.settings.AiModelService;
import com.chua.starter.common.support.annotations.Permission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * RAG 配置控制器 —— 提供 RAG 配置页所需的下拉数据源。
 * <p>
 * 返回五类选项：
 * <ul>
 *   <li>embeddingModels — 向量模型（EmbeddingClient SPI 提供商 + deeplearning 向量模型）</li>
 *   <li>ocrModels — OCR 识别模型（deeplearning ModelRegistry 中文字识别类模型）</li>
 *   <li>llmModels — LLM 模型（ai_model 表，能力含 chat）</li>
 *   <li>vectorStorages — 向量数据库（VectorStorageProvider SPI）</li>
 *   <li>splitters — 文本切片策略（TextSplitterProvider SPI）</li>
 * </ul>
 *
 * @since 2026-08-05
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/v2/rag")
@RequiredArgsConstructor
@Tag(name = "RAG 配置", description = "RAG 配置下拉数据源")
public class AiRagSettingController {

    /**
     * AI 模型服务
     */
    private final AiModelService aiModelService;

    /**
     * 获取 RAG 配置选项。
     *
     * @return 五类下拉数据源
     */
    @Permission("admin")
    @Operation(summary = "获取 RAG 配置选项", description = "向量模型、OCR 模型、LLM 模型、向量数据库、文本切片策略选项")
    @GetMapping("/options")
    public ReturnResult<Map<String, Object>> options() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("embeddingModels", listEmbeddingModels());
        result.put("ocrModels", listOcrModels());
        result.put("llmModels", listLlmModels());
        result.put("vectorStorages", listVectorStorages());
        result.put("splitters", listSplitters());
        return ReturnResult.ok(result);
    }

    /**
     * 向量模型列表：嵌入客户端 SPI 提供商 + deeplearning 向量模型。
     *
     * @return 模型名列表
     */
    private List<String> listEmbeddingModels() {
        TreeSet<String> names = new TreeSet<>();

 // 1. 嵌入客户端 SPI 提供商（openai/siliconflow/sensetime/GitHub/gitee/内存）
        try {
            ServiceProvider.of(EmbeddingClient.class).getExtensions()
                    .forEach(name -> names.add(name.toLowerCase()));
        } catch (Exception e) {
            log.warn("[RagSetting] 枚举 EmbeddingClient SPI 失败: {}", e.getMessage());
        }

 // 2. deeplearning 向量模型（模型标识 含 嵌入，或输出 float[] 的文本特征模型）
        try {
            ModelRegistry.discoverAll();
            Collection<ModelRegistry.Entry> entries = ModelRegistry.getAll();
            for (ModelRegistry.Entry entry : entries) {
                String id = entry.modelId() != null ? entry.modelId().toLowerCase() : "";
                if (id.contains("embedding")) {
                    names.add(entry.modelId());
                    continue;
                }
                Class<?> output = entry.outputType();
                if (output != null && float[].class.isAssignableFrom(output)
                        && (id.contains("clip-text") || id.contains("bge") || id.contains("mini")
                        || id.contains("text-feature") || id.contains("feature"))) {
                    names.add(entry.modelId());
                }
            }
        } catch (Throwable e) {
            log.debug("[RagSetting] deeplearning 向量模型枚举跳过: {}", e.getMessage());
        }

        return new ArrayList<>(names);
    }

    /**
     * OCR 模型列表：deeplearning 模型registry 中文字识别类模型。
     *
     * @return 模型名列表
     */
    private List<String> listOcrModels() {
        TreeSet<String> names = new TreeSet<>();
        try {
            ModelRegistry.discoverAll();
            Collection<ModelRegistry.Entry> entries = ModelRegistry.getAll();
            for (ModelRegistry.Entry entry : entries) {
                String id = entry.modelId() != null ? entry.modelId().toLowerCase() : "";
                if (id.contains("ocr") || id.contains("pp-word") || id.contains("svtr")
                        || id.contains("word-extract") || id.contains("paddle-ocr")
                        || id.contains("table-struct") || id.contains("pp-structure")
                        || id.contains("docling")) {
                    names.add(entry.modelId());
                }
            }
        } catch (Throwable e) {
            log.warn("[RagSetting] deeplearning OCR 模型枚举失败: {}", e.getMessage());
        }
        return new ArrayList<>(names);
    }

    /**
     * LLM 模型列表：AI_模型 表，能力含 对话。
     *
     * @return 模型名列表
     */
    private List<String> listLlmModels() {
        try {
            List<AiModelDTO> models = aiModelService.list();
            return models.stream()
                    .filter(m -> m.getCapabilities() != null
                            && m.getCapabilities().stream().anyMatch(c -> "chat".equalsIgnoreCase(c)))
                    .map(AiModelDTO::getCode)
                    .filter(code -> code != null && !code.isBlank())
                    .distinct()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[RagSetting] 读取 LLM 模型列表失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 向量数据库列表：向量storage提供者 SPI。
     *
     * @return 提供者名称列表
     */
    private List<String> listVectorStorages() {
        try {
            return VectorStorageProvider.providers().stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[RagSetting] 枚举向量数据库失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 文本切片策略列表：文本splitter提供者 SPI。
     *
     * @return 提供者名称列表
     */
    private List<String> listSplitters() {
        try {
            return TextSplitterProvider.providers().stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[RagSetting] 枚举文本切片策略失败: {}", e.getMessage());
            return List.of();
        }
    }
}

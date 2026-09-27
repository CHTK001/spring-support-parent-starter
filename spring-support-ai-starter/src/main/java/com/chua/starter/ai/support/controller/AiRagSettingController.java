package com.chua.starter.ai.support.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.embedding.EmbeddingClient;
import com.chua.common.support.ai.splitter.TextSplitterProvider;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.vector.VectorStorageDescriptor;
import com.chua.common.support.vector.VectorStorageProvider;
import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderModelMapper;
import com.chua.starter.ai.support.manage.store.SysAiProviderModel;
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
import java.util.Objects;
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
     * 能力项中表示任务类型的字段名
     */
    private static final String CAPABILITY_TASK_KEY = "task";

    /**
     * 对话任务标识：{@code sys_ai_provider_model_capabilities} 中区分对话/思考/图像的判据
     */
    private static final String CAPABILITY_CHAT_TASK = "chat";

    /**
     * 供应商模型映射器：LLM 下拉数据源，与前端模型选择器同表同标识
     */
    private final SysAiProviderModelMapper providerModelMapper;

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
        result.put("vectorStorageOptions", listVectorStorageOptions());
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
     * LLM 模型列表：{@code sys_ai_provider_model} 中已启用且能力含 {@code chat} 的模型。
     *
     * <p>与前端模型选择器同源（同一张表、同一个 {@code model} 标识），因此下拉里选中的值
     * 可以直接用于对话请求，不需要再做一次名称映射。</p>
     *
     * <p>此前读的是 {@code sys_ai_model}，但该表只承载 OCR/版面类模型，且能力列是
     * {@code ["ocr","layout"]} 字符串数组，与本表 {@code [{"task":"chat"}]} 对象数组并不同构；
     * 线上该表的对话模型行数为 0，导致本下拉恒为空、RAG 配置页无法选型。</p>
     *
     * @return 模型名列表，已停用或无 chat 能力的模型不出现
     */
    private List<String> listLlmModels() {
        try {
            return providerModelMapper.selectList(new LambdaQueryWrapper<SysAiProviderModel>()
                            .eq(SysAiProviderModel::getEnabled, true))
                    .stream()
                    .filter(AiRagSettingController::isChatCapable)
                    .map(SysAiProviderModel::getModel)
                    .filter(model -> model != null && !model.isBlank())
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[RagSetting] 读取 LLM 模型列表失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 判断供应商模型行是否具备对话能力。
     *
     * <p>能力以 JSON 对象数组存放，形如
     * {@code [{"task":"chat","protocol":"openai"},{"task":"thinking","protocol":"openai"}]}，
     * 所以必须按 {@code task} 字段匹配；用字符串包含去匹配会同时误命中 {@code thinking}。</p>
     *
     * @param row 供应商模型行
     * @return 任一能力项的 {@code task} 为 chat 时返回 true
     */
    private static boolean isChatCapable(SysAiProviderModel row) {
        List<Map<String, Object>> capabilities = row.getCapabilities();
        if (capabilities == null || capabilities.isEmpty()) {
            return false;
        }
        return capabilities.stream()
                .filter(Objects::nonNull)
                .map(capability -> capability.get(CAPABILITY_TASK_KEY))
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .anyMatch(CAPABILITY_CHAT_TASK::equalsIgnoreCase);
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
     * 向量库实现描述器：字段、默认值、是否敏感，供前端动态渲染与保存配置。
     *
     * <p>前端按描述器渲染表单，因此新增向量库无需再改前端。
     * {@code sensitive} 为 true 的字段提交后不参与回显。</p>
     *
     * @return 向量库描述器列表
     */
    private List<VectorStorageDescriptor> listVectorStorageOptions() {
        try {
            return VectorStorageProvider.descriptors();
        } catch (Exception e) {
            log.warn("[RagSetting] 枚举向量存储描述器失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 文本切片器列表：由 text splitters 提供的 SPI。
     *
     * @return 提供商名称列表
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

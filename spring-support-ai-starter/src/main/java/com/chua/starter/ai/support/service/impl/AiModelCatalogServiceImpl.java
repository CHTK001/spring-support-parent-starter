package com.chua.starter.ai.support.service.impl;

import com.chua.common.support.ai.audio.AudioClient;
import com.chua.common.support.ai.audio.TextToAudioClient;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.embedding.EmbeddingClient;
import com.chua.common.support.ai.feature.FeatureClient;
import com.chua.common.support.ai.image.ImageClient;
import com.chua.common.support.ai.video.VideoClient;
import com.chua.deeplearning.support.capability.ModelCapabilities;
import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.ai.support.model.AiModelDTO;
import com.chua.starter.ai.support.service.AiModelCatalogService;
import com.chua.starter.ai.support.service.settings.AiModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * AI 统一能力模型清单服务实现。
 *
 * <p>聚合本地模型（ModelRegistry 按能力接口归类）与云端模型
 * （各 AI 能力客户端门面 {@code listModels()}），统一按能力标签分组输出。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelCatalogServiceImpl implements AiModelCatalogService {

    /**
     * AI 模型定义服务（chat 能力从 ai_model 表拉取）
     */
    private final AiModelService aiModelService;

    /**
     * 能力 → 云端门面 listModels 提供者 映射。
     */
    private final Map<String, List<String>> cloudCapabilityModels = new LinkedHashMap<>();

    @Override
    /** ListModelsByCapability */
    public Map<String, List<String>> listModelsByCapability() {
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (cached != null) {
                return cached;
            }
            try {
                ModelRegistry.discoverAll();
            } catch (Exception e) {
                log.debug("[AiModelCatalog] 模型注册表发现失败: {}", e.getMessage());
            }
            Map<String, LinkedHashSet<String>> grouped = new LinkedHashMap<>();
        // 1. 本地模型：按能力接口归类
        for (ModelRegistry.Entry entry : ModelRegistry.getAll()) {
            String label = ModelCapabilities.labelOf(entry.capabilityInterface());
            if (label != null) {
                grouped.computeIfAbsent(label, k -> new LinkedHashSet<>()).add(entry.modelId());
                continue;
            }
            // 名称约定回退
            String name = entry.modelId() == null ? "" : entry.modelId().toLowerCase();
            String fallback = fallbackLabel(name);
            if (fallback != null) {
                grouped.computeIfAbsent(fallback, k -> new LinkedHashSet<>()).add(entry.modelId());
            }
        }
        // 2. 云端模型：各能力门面 listModels()
        mergeCloud(grouped, ModelCapabilities.CHAT, chatModels());
        mergeCloud(grouped, ModelCapabilities.TEXT_TO_IMAGE, imageModels());
        mergeCloud(grouped, ModelCapabilities.TEXT_TO_VIDEO, videoModels());
        mergeCloud(grouped, ModelCapabilities.TEXT_EMBEDDING, embeddingModels());
        mergeCloud(grouped, ModelCapabilities.ASR, asrModels());
        mergeCloud(grouped, ModelCapabilities.TTS, ttsModels());
        mergeCloud(grouped, ModelCapabilities.FEATURE, featureModels());

        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, LinkedHashSet<String>> e : grouped.entrySet()) {
            result.put(e.getKey(), List.copyOf(e.getValue()));
        }
        cached = result;
        return result;
        }
    }

    /**
     * 能力清单缓存（首次计算后复用，避免每次全量扫描模型注册表）。
     */
    private volatile Map<String, List<String>> cached;

    @Override
    /** ListModels */
    public List<String> listModels(String capability) {
        return listModelsByCapability().getOrDefault(capability, List.of());
    }

    @Override
    /** ListCapabilities */
    public List<String> listCapabilities() {
        return new ArrayList<>(listModelsByCapability().keySet());
    }

    /**
     * 合并云端模型到分组。
     */
    private void mergeCloud(Map<String, LinkedHashSet<String>> grouped, String capability, List<String> models) {
        if (models == null || models.isEmpty()) {
            return;
        }
        grouped.computeIfAbsent(capability, k -> new LinkedHashSet<>()).addAll(models);
    }

    /**
     * 能力接口无法识别时的名称约定回退。
     */
    private String fallbackLabel(String name) {
        if (containsAny(name, "ocr", "pp-word", "svtr", "paddle-ocr", "table-struct", "pp-structure", "docling")) {
            return ModelCapabilities.OCR;
        }
        if (containsAny(name, "layout", "d4la", "docstruct", "cdla", "doclaynet")) {
            return ModelCapabilities.LAYOUT;
        }
        if (containsAny(name, "pose", "vit-pose", "yolov8n-pose")) {
            return ModelCapabilities.POSE;
        }
        if (containsAny(name, "bge", "minilm", "embedding", "clip-text")) {
            return ModelCapabilities.EMBEDDING;
        }
        if (containsAny(name, "translate", "nllb", "opus-mt", "opus_alt")) {
            return ModelCapabilities.TRANSLATE;
        }
        return null;
    }

    /** ContainsAny */
    private boolean containsAny(String name, String... keywords) {
        for (String keyword : keywords) {
            if (name.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    // ==================== 云端门面模型清单 ====================

    /**
     * 聊天/大模型（含视觉理解）：优先 ai_model 表能力含 chat，回退 ChatClient SPI。
     */
    private List<String> chatModels() {
        List<String> fromDb = new ArrayList<>();
        try {
            List<com.chua.starter.ai.support.model.AiModelDTO> models = aiModelService.list();
            for (com.chua.starter.ai.support.model.AiModelDTO m : models) {
                if (m.getCapabilities() != null && m.getCapabilities().stream()
                        .anyMatch(c -> "chat".equalsIgnoreCase(c))
                        && m.getCode() != null && !m.getCode().isBlank()) {
                    fromDb.add(m.getCode());
                }
            }
        } catch (Exception e) {
            log.debug("[AiModelCatalog] ai_model 表 chat 模型读取失败: {}", e.getMessage());
        }
        if (!fromDb.isEmpty()) {
            return new ArrayList<>(new LinkedHashSet<>(fromDb));
        }
        return cloudModels(ChatClient.class, (name, client) -> client.listModels());
    }

    /**
     * 文生图：遍历全部 ImageClient provider 枚举模型。
     */
    private List<String> imageModels() {
        return cloudModels(ImageClient.class, (name, client) -> client.listModels());
    }

    /**
     * 文生视频：遍历全部 VideoClient provider 枚举模型。
     */
    private List<String> videoModels() {
        return cloudModels(VideoClient.class, (name, client) -> client.listModels());
    }

    /**
     * 文本嵌入：遍历全部 EmbeddingClient provider 枚举模型。
     */
    private List<String> embeddingModels() {
        return cloudModels(EmbeddingClient.class, (name, client) -> client.listModels());
    }

    /**
     * 语音识别 ASR：遍历全部 AudioClient provider 枚举模型。
     */
    private List<String> asrModels() {
        return cloudModels(AudioClient.class, (name, client) -> client.listModels());
    }

    /**
     * 语音合成 TTS：遍历全部 TextToAudioClient provider 枚举模型。
     */
    private List<String> ttsModels() {
        return cloudModels(TextToAudioClient.class, (name, client) -> client.listModels());
    }

    /**
     * 特征提取：遍历全部 FeatureClient provider 枚举模型。
     */
    private List<String> featureModels() {
        return cloudModels(FeatureClient.class, (name, client) -> client.listModels());
    }

    /**
     * 遍历客户端接口的全部 SPI provider，逐个拉取模型清单并合并去重。
     *
     * @param clientClass 客户端接口类型
     * @param lister      模型清单提取函数
     * @param <T>         客户端类型
     * @return 合并后的模型 ID 列表
     */
    private <T> List<String> cloudModels(Class<T> clientClass, java.util.function.BiFunction<String, T, List<String>> lister) {
        LinkedHashSet<String> all = new LinkedHashSet<>();
        try {
            ServiceProvider.of(clientClass).getExtensions().forEach(name -> {
                try {
                    T client = createClient(clientClass, name);
                    if (client == null) {
                        return;
                    }
                    List<String> models = lister.apply(name, client);
                    if (models != null) {
                        all.addAll(models);
                    }
                } catch (Exception e) {
                    log.debug("[AiModelCatalog] provider={} 模型清单跳过: {}", name, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.debug("[AiModelCatalog] 枚举 {} 失败: {}", clientClass.getSimpleName(), e.getMessage());
        }
        return new ArrayList<>(all);
    }

    /**
     * 按客户端接口类型调用对应工厂创建实例（各门面 create(String,String) 签名一致）。
     *
     * @param clientClass 客户端接口类型
     * @param provider    provider 名称
     * @param <T>         客户端类型
     * @return 客户端实例
     */
    @SuppressWarnings("unchecked")
    private <T> T createClient(Class<T> clientClass, String provider) {
        if (clientClass == ChatClient.class) {
            return (T) ChatClient.create(provider, "");
        }
        if (clientClass == ImageClient.class) {
            return (T) ImageClient.create(provider, "");
        }
        if (clientClass == VideoClient.class) {
            return (T) VideoClient.create(provider, "");
        }
        if (clientClass == EmbeddingClient.class) {
            return (T) EmbeddingClient.create(provider, "");
        }
        if (clientClass == AudioClient.class) {
            return (T) AudioClient.create(provider, "");
        }
        if (clientClass == TextToAudioClient.class) {
            return (T) TextToAudioClient.create(provider, "");
        }
        if (clientClass == FeatureClient.class) {
            return (T) FeatureClient.create(provider, "");
        }
        return null;
    }

    /**
     * 安全执行门面模型清单查询。
     */
    private List<String> safeList(java.util.function.Supplier<List<String>> supplier) {
        try {
            List<String> models = supplier.get();
            return models != null ? models : List.of();
        } catch (Exception e) {
            log.debug("[AiModelCatalog] 能力模型清单查询跳过: {}", e.getMessage());
            return List.of();
        }
    }
}

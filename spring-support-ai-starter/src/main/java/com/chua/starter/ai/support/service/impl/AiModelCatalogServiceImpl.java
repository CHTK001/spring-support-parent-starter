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
     * AI 模型定义服务（对话 能力从 AI_模型 表拉取）
     */
    private final AiModelService aiModelService;

    /**
     * 能力 → 云端门面 列表模型 提供者 映射。
     */
    private final Map<String, List<String>> cloudCapabilityModels = new LinkedHashMap<>();

    @Override
    /** 列表模型bycapability */
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
 // 2. 云端模型：各能力门面 列表模型()
        /**
         * 合并cloud。
         * @param grouped 群体
         * @param ModelCapabilities.CHAT 模型capabilities.对话
         * @param chatModels( 对话模型(
         */
        mergeCloud(grouped, ModelCapabilities.CHAT, chatModels());
        /**
         * 合并cloud。
         * @param grouped 群体
         * @param ModelCapabilities.TEXT_TO_IMAGE 模型capabilities.文本转为镜像
         * @param imageModels( 镜像模型(
         */
        mergeCloud(grouped, ModelCapabilities.TEXT_TO_IMAGE, imageModels());
        /**
         * 合并cloud。
         * @param grouped 群体
         * @param ModelCapabilities.TEXT_TO_VIDEO 模型capabilities.文本转为视频
         * @param videoModels( 视频模型(
         */
        mergeCloud(grouped, ModelCapabilities.TEXT_TO_VIDEO, videoModels());
        /**
         * 合并cloud。
         * @param grouped 群体
         * @param ModelCapabilities.TEXT_EMBEDDING 模型capabilities.文本嵌入
         * @param embeddingModels( 嵌入模型(
         */
        mergeCloud(grouped, ModelCapabilities.TEXT_EMBEDDING, embeddingModels());
        /**
         * 合并cloud。
         * @param grouped 群体
         * @param ModelCapabilities.ASR 模型capabilities.asr
         * @param asrModels( asr模型(
         */
        mergeCloud(grouped, ModelCapabilities.ASR, asrModels());
        /**
         * 合并cloud。
         * @param grouped 群体
         * @param ModelCapabilities.TTS 模型capabilities.tts
         * @param ttsModels( tts模型(
         */
        mergeCloud(grouped, ModelCapabilities.TTS, ttsModels());
        /**
         * 合并cloud。
         * @param grouped 群体
         * @param ModelCapabilities.FEATURE 模型capabilities.特征
         * @param featureModels( 特征模型(
         */
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
    /** 列表模型 */
    public List<String> listModels(String capability) {
        return listModelsByCapability().getOrDefault(capability, List.of());
    }

    @Override
    /** 列表capabilities */
    public List<String> listCapabilities() {
        return new ArrayList<>(listModelsByCapability().keySet());
    }

    /**
     * 合并云端模型到分组。
     * @param grouped 群体
     * @param capability capability
     * @param models 模型
     */
    private void mergeCloud(Map<String, LinkedHashSet<String>> grouped, String capability, List<String> models) {
        if (models == null || models.isEmpty()) {
            return;
        }
        grouped.computeIfAbsent(capability, k -> new LinkedHashSet<>()).addAll(models);
    }

    /**
     * 能力接口无法识别时的名称约定回退。
     * @param name 名称
     * @return 降级标签的结果
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
        if (containsAny(name, "bsr", "text-bsr", "text-super", "text_super")) {
            return ModelCapabilities.ENHANCE;
        }
        if (containsAny(name, "caption", "vit-gpt2", "blip", "git-base")) {
            return ModelCapabilities.CLASSIFY;
        }
        return null;
    }

    /**
     * contains任意
     *
     * @param name 名称
     * @param keywords keywords
     * @return contains任意的结果
     */
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
     * 聊天/大模型（含视觉理解）：优先 AI_模型 表能力含 对话，回退 对话客户端 SPI。
     * @return 对话模型的结果
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
     * 文生图：遍历全部 镜像客户端 提供者 枚举模型。
     * @return 镜像模型的结果
     */
    private List<String> imageModels() {
        return cloudModels(ImageClient.class, (name, client) -> client.listModels());
    }

    /**
     * 文生视频：遍历全部 视频客户端 提供者 枚举模型。
     * @return 视频模型的结果
     */
    private List<String> videoModels() {
        return cloudModels(VideoClient.class, (name, client) -> client.listModels());
    }

    /**
     * 文本嵌入：遍历全部 嵌入客户端 提供者 枚举模型。
     * @return 嵌入模型的结果
     */
    private List<String> embeddingModels() {
        return cloudModels(EmbeddingClient.class, (name, client) -> client.listModels());
    }

    /**
     * 语音识别 ASR：遍历全部 音频客户端 提供者 枚举模型。
     * @return asr模型的结果
     */
    private List<String> asrModels() {
        return cloudModels(AudioClient.class, (name, client) -> client.listModels());
    }

    /**
     * 语音合成 TTS：遍历全部 文本转为音频客户端 提供者 枚举模型。
     * @return tts模型的结果
     */
    private List<String> ttsModels() {
        return cloudModels(TextToAudioClient.class, (name, client) -> client.listModels());
    }

    /**
     * 特征提取：遍历全部 特征客户端 提供者 枚举模型。
     * @return 特征模型的结果
     */
    private List<String> featureModels() {
        return cloudModels(FeatureClient.class, (name, client) -> client.listModels());
    }

    /**
     * 遍历客户端接口的全部 SPI 提供者，逐个拉取模型清单并合并去重。
     *
     * @param clientClass 客户端接口类型
     * @param lister      模型清单提取函数
     * @param <T>         客户端类型
     * @return 合并后的模型 标识 列表
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
     * 按客户端接口类型调用对应工厂创建实例（各门面 创建(字符串,字符串) 签名一致）。
     *
     * @param clientClass 客户端接口类型
     * @param provider    提供者 名称
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
            return (T) com.chua.common.support.ai.audio.VirtualClient.create(provider, "");
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
     * @param supplier 供应商
     * @return safe列表的结果
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

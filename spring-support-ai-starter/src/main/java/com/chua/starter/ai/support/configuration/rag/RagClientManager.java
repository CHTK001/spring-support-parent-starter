package com.chua.starter.ai.support.configuration.rag;

import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.configuration.ocr.OcrSettingsChangedEvent;
import com.chua.starter.ai.support.configuration.ocr.OcrTextExtractor;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.embedding.EmbeddingClient;
import com.chua.common.support.ai.embedding.MemoryEmbeddingClient;
import com.chua.common.support.ai.rag.RagClient;
import com.chua.common.support.ai.rag.RagClientSetting;
import com.chua.common.support.ai.splitter.SentenceTextSplitter;
import com.chua.common.support.ai.splitter.TextSplitter;
import com.chua.common.support.ai.splitter.TextSplitterProvider;
import com.chua.common.support.file.txtractor.TextExtractor;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.vector.VectorCompareAlgorithm;
import com.chua.common.support.vector.VectorStorage;
import com.chua.common.support.vector.VectorStorageProvider;
import com.chua.starter.ai.support.registry.ChatClientRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
* 全局 RAG 客户端管理器。
*
* <p>引入 AI 模块即提供全局 RAG 客户端，配置读取自 {@code sys_ai_global_setting}
* 键值表（分组 {@code global}：向量模型、向量数据库、上传目录、RAG 系统提示词、RAG LLM 模型、
* 切片大小、切片重叠、OCR 模型）。
* 配置通过全局配置面板修改后调用 {@link #rebuild()} 热更新，无需重启。</p>
*
* @since 4.0.0.42
* @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagClientManager {

    /**
    * 与默认 内存嵌入客户端 输出维度一致。
     */
    private static final int DEFAULT_EMBEDDING_DIMENSIONS = 1536;

    /**
    * 全局设置键值分组
     */
    private static final String GROUP = "rag";

    /**
    * 全局 RAG 客户端在 Spring 容器中的 Bean 名称
     */
    private static final String RAG_CLIENT_BEAN_NAME = "globalRagClient";

    /**
    * 全局设置读取工具
     */
    private final GlobalSettingReader settingReader;

    /**
    * 全局 对话客户端 注册表（按模型解析 RAG LLM）
     */
    private final ChatClientRegistry chatClientRegistry;

    /**
    * RAG 配置属性（默认值兜底）
     */
    private final RagProperties properties;

    /**
    * Spring Bean工厂（动态注册/替换 全局rag客户端 Bean）
     */
    private final DefaultListableBeanFactory beanFactory;

    /**
    * 当前生效的全局 RAG 客户端（懒构建，热更新时替换）。
     */
    private volatile RagClient ragClient;

    /**
    * 获取当前 RAG 客户端，未构建时按全局配置懒构建。
    *
    * @return RAG 客户端（配置缺失时可能为 空）
     */
    public RagClient current() {
        RagClient client = ragClient;
        if (client == null) {
            synchronized (this) {
                if (ragClient == null) {
                    ragClient = build();
                }
                client = ragClient;
            }
        }
        return client;
    }

    /**
    * 监听 OCR 设置变更事件，重建 RAG 客户端以使用最新的 OCR 模型配置。
    * @param event 事件
     */
    @EventListener
    public void onOcrSettingsChanged(OcrSettingsChangedEvent event) {
        if (event == null || !"ocr".equals(event.getGroup())) {
            return;
        }
        log.info("[RagClientManager] 收到 OCR 设置变更事件，重建 RAG 客户端");
        rebuild();
    }

    /**
    * 按最新全局配置重建 RAG 客户端（热更新）。
     */
    public synchronized void rebuild() {
        RagClient next = build();
        RagClient old = ragClient;
        this.ragClient = next;
        registerBean(next);
        if (old != null) {
            try {
                old.close();
            } catch (Exception ignored) {
            }
        }
        if (next != null) {
            log.info("[RagClientManager] 全局 RAG 客户端已热更新");
        }
    }

    /**
    * 通过 Bean工厂 动态注册 {@code globalRagClient} 单例 Bean，供容器内按名称获取最新实例。
    *
    * @param client RAG 客户端
     */
    private void registerBean(RagClient client) {
        try {
            if (beanFactory.containsSingleton(RAG_CLIENT_BEAN_NAME)) {
                beanFactory.destroySingleton(RAG_CLIENT_BEAN_NAME);
            }
            if (client != null) {
                beanFactory.registerSingleton(RAG_CLIENT_BEAN_NAME, client);
            }
        } catch (Exception e) {
            log.warn("[RagClientManager] BeanFactory 注册 RAG 客户端失败: {}", e.getMessage());
        }
    }

    /**
    * 基于全局配置构建 RAG 客户端。
    * @return 构建的结果
     */
    private RagClient build() {
        String ragEmbeddingModel = settingReader.getStr(GROUP, "rag_embedding_model");
        String ragLlmModel = settingReader.getStr(GROUP, "rag_llm_model");
        String ragOcrModel = settingReader.getStr(GROUP, "rag_ocr_model");
        String ragUploadDir = settingReader.getStr(GROUP, "rag_upload_dir");
        String ragVectorStorage = settingReader.getStr(GROUP, "rag_vector_storage");
        String ragSystemPrompt = settingReader.getStr(GROUP, "rag_system_prompt");
        String ragSplitter = settingReader.getStr(GROUP, "rag_splitter");
        int chunkSize = settingReader.getInt(GROUP, "rag_chunk_size", properties.getChunkSize());
        int chunkOverlap = settingReader.getInt(GROUP, "rag_chunk_overlap", properties.getChunkOverlap());

        ChatClient chatClient = resolveChatClient(ragLlmModel);
        if (chatClient == null) {
            log.warn("[RagClientManager] 全局 ChatClient 未初始化，RAG 客户端暂不可用");
            return null;
        }

        String uploadDir = StringUtils.hasText(ragUploadDir) ? ragUploadDir : properties.getUploadDir();

        EmbeddingClient embeddingClient = createEmbeddingClient(ragEmbeddingModel);
        VectorStorage vectorStorage = createVectorStorage(ragVectorStorage, uploadDir);
        TextSplitter splitter = createTextSplitter(ragSplitter, chunkSize, chunkOverlap);
        RagClientSetting setting = RagClientSetting.builder()
                .topK(properties.getDefaultTopK())
                .similarityThreshold(properties.getDefaultSimilarityThreshold())
                .uploadDir(uploadDir)
                .chatClient(chatClient)
                .embeddingClient(embeddingClient)
                .textSplitter(splitter)
                .vectorStorage(vectorStorage)
                .systemPrompt(ragSystemPrompt)
                .textExtractor(resolveTextExtractor(ragOcrModel))
                .build();

        RagClient client = RagClient.create("memory", setting);
        if (client != null) {
            log.info("[RagClientManager] RAG 客户端构建完成: embedding={}, storage={}, uploadDir={}, splitter={}, chunkSize={}, overlap={}",
                    StringUtils.defaultString(ragEmbeddingModel, "memory"),
                    StringUtils.defaultString(ragVectorStorage, properties.getVectorStorage()),
                    uploadDir, StringUtils.defaultString(ragSplitter, "sentence"), chunkSize, chunkOverlap);
        } else {
            log.error("[RagClientManager] 无可用 RagClient SPI 实现，RAG 功能不可用");
        }
        return client;
    }

    /**
    * 解析 RAG 使用的 LLM：优先 rag_llm_模型 指定模型，否则全局默认客户端。
    * @param ragLlmModel ragllm模型
    * @return resolve对话客户端的结果
     */
    private ChatClient resolveChatClient(String ragLlmModel) {
        if (chatClientRegistry == null) {
            return null;
        }
        if (StringUtils.hasText(ragLlmModel)) {
            ChatClient byModel = chatClientRegistry.getByModel(ragLlmModel);
            if (byModel != null) {
                return byModel;
            }
            log.warn("[RagClientManager] RAG LLM 模型 {} 未注册，回退全局默认", ragLlmModel);
        }
        return chatClientRegistry.getDefault();
    }

    /**
    * 解析文本提取器：配置了 RAG OCR 模型时使用 OCR 提取器识别图片文字，否则返回 空。
    *
    * <p>优先使用 ocr 分组的完整管线配置（detector + recognizer + 阈值 + 阅读序 + direction + restorer），
    * 未配置 detector/recognizer 时回退到旧版单模型 recognizer 路径。</p>
    *
    * @param ocrModel 兜底 OCR 模型名（rag_ocr_模型）
    * @return 文本提取器，未配置 OCR 时返回 空
     */
    private TextExtractor resolveTextExtractor(String ocrModel) {
        String detectorModel = settingReader.getStr("ocr", "detector_model");
        String recognizerModel = settingReader.getStr("ocr", "recognizer_model");
        String sortStr = settingReader.getStr("ocr", "sort_reading_order");
        String thresholdStr = settingReader.getStr("ocr", "detector_threshold");
        String directionModel = settingReader.getStr("ocr", "direction_model");
        String restorerModel = settingReader.getStr("ocr", "restorer_model");

        if (StringUtils.hasText(detectorModel) && StringUtils.hasText(recognizerModel)) {
            // 完整 OCR 管线（detect→recognize，含阈值过滤与阅读序）
            float threshold = parseFloatSafe(thresholdStr, 0.5f);
            boolean sort = !"false".equalsIgnoreCase(sortStr);
            try {
                return new OcrTextExtractor(detectorModel, recognizerModel,
                        threshold, sort, directionModel, restorerModel);
            } catch (Exception e) {
                log.warn("[RagClientManager] 完整 OCR 管线创建失败: {}", e.getMessage());
            }
        }
        if (StringUtils.hasText(ocrModel)) {
            // 回退到单模型 recognizer 路径（仅识别，无检测/阈值）
            try {
                return new OcrTextExtractor(ocrModel);
            } catch (Exception e) {
                log.warn("[RagClientManager] OCR 提取器创建失败，图片文字提取不可用: {}", e.getMessage());
                return null;
            }
        }
        return null;
    }

    /**
    * 安全解析 float，失败返回默认值。
    * @param s s
    * @param def def
    * @return 解析floatsafe的结果
     */
    private static float parseFloatSafe(String s, float def) {
        if (!StringUtils.hasText(s)) {
            return def;
        }
        try {
            return Float.parseFloat(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
    * 创建向量化客户端：优先使用已注册的 嵌入客户端 SPI，否则使用离线 内存嵌入客户端。
    * @param model 模型
    * @return 创建嵌入客户端的结果
     */
    private EmbeddingClient createEmbeddingClient(String model) {
        String engine = StringUtils.hasText(model) ? model.trim() : properties.getEmbeddingEngine();
        if (StringUtils.hasText(engine)) {
            try {
                Set<String> providers = ServiceProvider.of(EmbeddingClient.class).getExtensions();
                if (providers.contains(engine.toLowerCase())) {
                    return EmbeddingClient.create(engine.toLowerCase(), (String) null);
                }
            } catch (Exception e) {
                log.debug("[RagClientManager] EmbeddingClient SPI 创建失败，回退离线实现: {}", e.getMessage());
            }
        }
        log.info("[RagClientManager] 使用离线向量化 MemoryEmbeddingClient（engine={}）", engine);
        return new MemoryEmbeddingClient();
    }

    /**
    * 创建向量存储：jvector 使用 ON_DISK 落盘，其余使用内存实现。
    * @param storageName storage名称
    * @param uploadDir uploaddir
    * @return 创建向量storage的结果
     */
    private VectorStorage createVectorStorage(String storageName, String uploadDir) {
        String name = StringUtils.hasText(storageName)
                ? storageName.trim().toLowerCase()
                : properties.getVectorStorage();
        VectorCompareAlgorithm algorithm = VectorCompareAlgorithm.cosine();
        if ("jvector".equals(name)) {
            log.info("[RagClientManager] 向量存储使用 jvector(ON_DISK)");
            return VectorStorageProvider.create("jvector", DEFAULT_EMBEDDING_DIMENSIONS, algorithm);
        }
        log.info("[RagClientManager] 向量存储使用 memory（无落盘）");
        return VectorStorageProvider.create("memory", DEFAULT_EMBEDDING_DIMENSIONS, algorithm);
    }

    /**
    * 创建文本分割器：按全局配置的切片 SPI 名称解析，未配置或不可用时回退 sentence。
    * @param splitterName splitter名称
    * @param chunkSize chunk大小
    * @param chunkOverlap chunkoverlap
    * @return 创建文本splitter的结果
     */
    private TextSplitter createTextSplitter(String splitterName, int chunkSize, int chunkOverlap) {
        String name = StringUtils.hasText(splitterName) ? splitterName.trim().toLowerCase() : "sentence";
        try {
            return TextSplitterProvider.create(name, chunkSize, chunkOverlap);
        } catch (Exception e) {
            log.warn("[RagClientManager] 文本分割器 {} 不可用，回退 sentence: {}", name, e.getMessage());
            return new SentenceTextSplitter(chunkSize, chunkOverlap);
        }
    }
}



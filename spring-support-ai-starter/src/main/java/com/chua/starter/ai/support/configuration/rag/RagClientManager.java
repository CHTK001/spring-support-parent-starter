package com.chua.starter.ai.support.configuration.rag;

import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.configuration.ocr.OcrSettingsChangedEvent;
import com.chua.starter.ai.support.configuration.ocr.OcrTextExtractor;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.embedding.EmbeddingClient;
import com.chua.common.support.ai.embedding.EmbeddingClientSetting;
import com.chua.common.support.ai.embedding.MemoryEmbeddingClient;
import com.chua.common.support.ai.rag.RagClient;
import com.chua.common.support.ai.rag.RagClientSetting;
import com.chua.common.support.ai.rag.RagPipeline;
import com.chua.starter.ai.support.configuration.rag.RagSettingsChangedEvent;
import com.chua.common.support.ai.splitter.SentenceTextSplitter;
import com.chua.common.support.ai.splitter.TextSplitter;
import com.chua.common.support.ai.splitter.TextSplitterProvider;
import com.chua.common.support.file.txtractor.TextExtractor;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.vector.VectorCompareAlgorithm;
import com.chua.common.support.vector.VectorStorage;
import com.chua.common.support.vector.VectorStorageProvider;
import com.chua.jvector.support.configuration.JVectorStorageProperties;
import com.chua.starter.ai.support.registry.ChatClientRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
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
            } catch (Exception e) {
                log.warn("[RagClientManager] 关闭旧 RAG 客户端失败: {}", e.getMessage());
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

        // 检索 Top-K 与相似度阈值：优先读取全局设置，未配置时回退配置属性
        int topK = settingReader.getInt(GROUP, "rag_default_top_k", properties.getDefaultTopK());
        double similarityThreshold = settingReader.getDouble(GROUP, "rag_similarity_threshold",
                properties.getDefaultSimilarityThreshold());
        int embeddingDimensions = settingReader.getInt(GROUP, "rag_embedding_dimensions",
                DEFAULT_EMBEDDING_DIMENSIONS);

        ChatClient chatClient = resolveChatClient(ragLlmModel);
        if (chatClient == null) {
            log.warn("[RagClientManager] 全局 ChatClient 未初始化，RAG 客户端暂不可用");
            return null;
        }

        String uploadDir = StringUtils.hasText(ragUploadDir) ? ragUploadDir : properties.getUploadDir();

        EmbeddingClient embeddingClient = createEmbeddingClient(ragEmbeddingModel, embeddingDimensions);
        VectorStorage vectorStorage = createVectorStorage(ragVectorStorage, uploadDir, embeddingDimensions);
        TextSplitter splitter = createTextSplitter(ragSplitter, chunkSize, chunkOverlap);
        RagClientSetting setting = RagClientSetting.builder()
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .uploadDir(uploadDir)
                .chatClient(chatClient)
                .embeddingClient(embeddingClient)
                .textSplitter(splitter)
                .vectorStorage(vectorStorage)
                .systemPrompt(ragSystemPrompt)
                .textExtractor(resolveTextExtractor(ragOcrModel))
                .build();

        RagClient client = createClient(setting);
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
     * 解析 RAG 使用的 LLM：
     * 1. 按 rag_llm_model 从 ChatClientRegistry 查找已注册客户端
     * 2. 查不到则用 registry 默认客户端
     * 3. 都没有时，从 runtime 全局设置（provider/apiKey/baseUrl/model）直接构造真实客户端
     */
    private ChatClient resolveChatClient(String ragLlmModel) {
        if (chatClientRegistry != null) {
            if (StringUtils.hasText(ragLlmModel)) {
                ChatClient byModel = chatClientRegistry.getByModel(ragLlmModel);
                if (byModel != null) {
                    return byModel;
                }
                log.warn("[RagClientManager] RAG LLM 模型 {} 未注册，回退全局默认", ragLlmModel);
            }
            ChatClient defaultClient = chatClientRegistry.getDefault();
            if (defaultClient != null) {
                return defaultClient;
            }
        }
        // registry 无客户端 → 从 runtime 全局设置直接构建
        return buildRuntimeChatClient();
    }

    /**
     * 从 runtime 全局设置构建真实 ChatClient。
     * 读取 provider/provider_api_key/provider_base_url/default_model，构造并返回。
     */
    private ChatClient buildRuntimeChatClient() {
        String provider = settingReader.getStr("runtime", "provider");
        String apiKey = settingReader.getStr("runtime", "provider_api_key");
        String baseUrl = settingReader.getStr("runtime", "provider_base_url");
        String model = settingReader.getStr("runtime", "default_model");
        if (StringUtils.isBlank(provider) || StringUtils.isBlank(apiKey)) {
            log.warn("[RagClientManager] runtime 分组未配置 provider/api_key，无法构建真实 ChatClient");
            return null;
        }
        try {
            ChatClientSetting setting = ChatClientSetting.builder()
                    .provider(provider.trim())
                    .appKey(apiKey.trim())
                    .baseUrl(StringUtils.hasText(baseUrl) ? baseUrl.trim() : null)
                    .model(StringUtils.hasText(model) ? model.trim() : null)
                    .build();
            ChatClient client = ChatClient.create(setting);
            if (client != null) {
                log.info("[RagClientManager] 从 runtime 配置创建真实 ChatClient: provider={}, model={}", provider, model);
            }
            return client;
        } catch (Exception e) {
            log.warn("[RagClientManager] runtime ChatClient 创建失败: {}", e.getMessage());
            return null;
        }
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
     * @param s 待解析字符串，null 或空白时直接返回默认值
     * @param def 解析失败时的默认值
     * @return 解析出的 float；解析失败或输入为空时返回 def
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
     * 创建向量化客户端。
     * 1. 按 rag_embedding_model（如 "openai"）从 SPI 查找 provider
     * 2. 若为外部 provider（非 memory），从 rag/runtime 分组读取 apiKey/baseUrl/model 构造真实客户端
     * 3. 找不到或构造失败时降级为离线 MemoryEmbeddingClient
     *
     * @param model               嵌入引擎名称（如 "openai" / "siliconflow" / "memory"，留空取属性默认）
     * @param embeddingDimensions 向量维度，透传给真实嵌入客户端保证输出维度一致
     * @return EmbeddingClient 实例（失败时降级为离线内存实现）
     */
    private EmbeddingClient createEmbeddingClient(String model, int embeddingDimensions) {
        String engine = StringUtils.hasText(model) ? model.trim() : properties.getEmbeddingEngine();
        if (StringUtils.isBlank(engine)) {
            engine = properties.getEmbeddingEngine();
        }
        if ("memory".equalsIgnoreCase(engine)) {
            return new MemoryEmbeddingClient().dimensions(embeddingDimensions);
        }
        try {
            Set<String> providers = ServiceProvider.of(EmbeddingClient.class).getExtensions();
            if (!providers.contains(engine.toLowerCase())) {
                log.debug("[RagClientManager] EmbeddingClient SPI 中没有 provider '{}', 回退离线实现", engine);
                return new MemoryEmbeddingClient().dimensions(embeddingDimensions);
            }
            // 优先 rag 分组独立 embedding 配置，其次 runtime 配置
            String embKey = settingReader.getStr(GROUP, "rag_embedding_api_key");
            String embBase = settingReader.getStr(GROUP, "rag_embedding_base_url");
            String embModel = settingReader.getStr(GROUP, "rag_embedding_model_name");
            if (!StringUtils.hasText(embKey)) {
                embKey = settingReader.getStr("runtime", "provider_api_key");
            }
            if (!StringUtils.hasText(embBase)) {
                embBase = settingReader.getStr("runtime", "provider_base_url");
            }
            if (!StringUtils.hasText(embModel)) {
                embModel = settingReader.getStr("runtime", "default_model");
            }
            if (StringUtils.hasText(embKey) && StringUtils.isNotBlank(embBase)) {
                EmbeddingClientSetting setting = EmbeddingClientSetting.builder()
                        .provider(engine.toLowerCase())
                        .appKey(embKey.trim())
                        .baseUrl(embBase.trim())
                        .model(StringUtils.hasText(embModel) ? embModel.trim() : null)
                        .dimensions(embeddingDimensions)
                        .build();
                EmbeddingClient client = EmbeddingClient.create(setting);
                if (client != null) {
                    log.info("[RagClientManager] 创建真实 EmbeddingClient: provider={}, model={}, dim={}",
                            engine, embModel, embeddingDimensions);
                    return client;
                }
            }
            // 无 runtime key 时回退离线
            log.debug("[RagClientManager] 无 runtime apiKey，EmbeddingClient 回退离线实现（engine={}）", engine);
        } catch (Exception e) {
            log.warn("[RagClientManager] EmbeddingClient SPI 创建失败，回退离线实现: {}", e.getMessage());
        }
        log.info("[RagClientManager] 使用离线向量化 MemoryEmbeddingClient（engine={}, dim={}）", engine, embeddingDimensions);
        return new MemoryEmbeddingClient().dimensions(embeddingDimensions);
    }

    /**
     * 创建向量存储：jvector 使用 ON_DISK 落盘，其余使用内存实现。
     * jvector 初始化失败（如 native VectorizationProvider 不可用）时降级到内存实现，
     * 保证 RAG 服务始终可用。
     *
     * <p>生产默认 ON_DISK 模式 + 持久化索引路径（uploadDir 下的 jvector-index），
     * 重启后向量数据不丢失；开发测试可配 memory 模式快速验证。</p>
     *
     * @param storageName 存储名称（jvector / memory / milvus 等，留空取属性默认）
     * @param uploadDir   上传根目录，jvector 索引文件存放于其下
     * @return VectorStorage 实例（jvector 失败时降级 memory）
     */
    private VectorStorage createVectorStorage(String storageName, String uploadDir, int embeddingDimensions) {
        String name = StringUtils.hasText(storageName)
                ? storageName.trim().toLowerCase()
                : properties.getVectorStorage();
        VectorCompareAlgorithm algorithm = VectorCompareAlgorithm.cosine();
        int dim = embeddingDimensions > 0 ? embeddingDimensions : properties.getEmbeddingDimensions();
        if ("jvector".equals(name)) {
            try {
                JVectorStorageProperties props = new JVectorStorageProperties();
                // 生产持久化：ON_DISK 模式，索引文件落在 uploadDir 下，重启不丢失
                props.setMode(JVectorStorageProperties.Mode.ON_DISK);
                props.setIndexPath(uploadDir + "/jvector-index");
                log.info("[RagClientManager] 向量存储使用 jvector(ON_DISK), indexPath={}", props.getIndexPath());
                return VectorStorageProvider.create("jvector", dim, algorithm, props);
            } catch (Throwable e) {
                log.warn("[RagClientManager] jvector 初始化失败，降级到内存存储: {}", e.getMessage());
            }
        }
        log.info("[RagClientManager] 向量存储使用 memory（无落盘）");
        return VectorStorageProvider.create("memory", dim, algorithm);
    }

    /**
    * 输出当前 RAG 关键配置的描述信息。
    * @return 键值对形式的 RAG 配置描述
     */
    public Map<String, String> describe() {
        Map<String, String> desc = new LinkedHashMap<>();
        desc.put("embedding_model", settingReader.getStr(GROUP, "rag_embedding_model"));
        desc.put("llm_model", settingReader.getStr(GROUP, "rag_llm_model"));
        desc.put("vector_storage", settingReader.getStr(GROUP, "rag_vector_storage"));
        desc.put("upload_dir", settingReader.getStr(GROUP, "rag_upload_dir"));
        desc.put("splitter", settingReader.getStr(GROUP, "rag_splitter"));
        desc.put("top_k", String.valueOf(settingReader.getInt(GROUP, "rag_default_top_k", properties.getDefaultTopK())));
        desc.put("similarity_threshold", String.valueOf(settingReader.getDouble(GROUP, "rag_similarity_threshold",
                properties.getDefaultSimilarityThreshold())));
        desc.put("client_provider", StringUtils.hasText(settingReader.getStr(GROUP, "rag_client_provider"))
                ? settingReader.getStr(GROUP, "rag_client_provider") : "pipeline");
        return desc;
    }

    /**
     * 创建文本分割器：按全局配置的切片 SPI 名称解析，未配置或不可用时回退 sentence。
     * @param splitterName 分割器 SPI 扩展名，null 或空时默认 sentence
     * @param chunkSize 分块大小（字符）
     * @param chunkOverlap 相邻分块重叠字符数
     * @return 文本分块器实例，不为 null
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

    /**
    * 创建 RAG 客户端：优先通过 rag_client_provider 配置的名称查找 SPI，未配置或失败时回退管线实现。
    * @param setting RAG 客户端配置
    * @return RAG 客户端
     */
    private RagClient createClient(RagClientSetting setting) {
        String provider = settingReader.getStr(GROUP, "rag_client_provider");
        if (StringUtils.isBlank(provider) || "pipeline".equalsIgnoreCase(provider.trim())) {
            return new RagPipeline(setting);
        }
        String name = provider.trim().toLowerCase();
        try {
            RagClient client = RagClient.create(name, setting);
            if (client != null) {
                return client;
            }
        } catch (Exception e) {
            log.warn("[RagClientManager] SPI 客户端 {} 创建失败，回退管线实现: {}", name, e.getMessage());
        }
        return new RagPipeline(setting);
    }

    /**
    * 监听 RAG 设置变更事件，重建 RAG 客户端以使用最新的配置。
    * @param event 事件
     */
    @EventListener
    public void onRagSettingsChanged(RagSettingsChangedEvent event) {
        if (event == null || !GROUP.equals(event.getGroup())) {
            return;
        }
        log.info("[RagClientManager] 收到 rag 设置变更事件，重建 RAG 客户端");
        rebuild();
    }

    /**
    * 获取当前 RAG 管线实例（仅在 createClient 返回 RagPipeline 时有效）。
    * @return RagPipeline 实例，非管线实现时返回 null
     */
    public RagPipeline currentPipeline() {
        RagClient client = current();
        return client instanceof RagPipeline pipeline ? pipeline : null;
    }
}



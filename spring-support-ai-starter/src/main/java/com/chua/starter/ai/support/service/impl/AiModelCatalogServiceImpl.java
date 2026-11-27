package com.chua.starter.ai.support.service.impl;

import com.chua.common.support.ai.audio.AudioClient;
import com.chua.common.support.ai.audio.TextToAudioClient;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.embedding.EmbeddingClient;
import com.chua.common.support.ai.feature.FeatureClient;
import com.chua.common.support.ai.image.ImageClient;
import com.chua.common.support.ai.video.VideoClient;
import com.chua.deeplearning.support.capability.ModelCapabilities;
import com.chua.deeplearning.support.engine.ModelHardwareFilter;
import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.ai.support.model.AiModelDTO;
import com.chua.starter.ai.support.manage.service.OnnxExternalDirectoryService;
import com.chua.starter.ai.support.service.AiModelCatalogService;
import com.chua.starter.ai.support.service.settings.AiModelService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import com.chua.common.support.utils.CollectionUtils;

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
     * ONNX 外部目录服务：提供持久化的模型根，用于在 discoverAll 之后把根推回注册表
     */
    private final OnnxExternalDirectoryService onnxExternalDirectoryService;

    /**
     * 能力 → 云端门面 列表模型 提供者 映射。
     */
    private final Map<String, List<String>> cloudCapabilityModels = new LinkedHashMap<>();

    /**
     * 本地模型文件后缀，与 {@code ModelRegistry#MODEL_SUFFIXES} 保持一致（后者为私有）。
     */
    private static final String[] LOCAL_MODEL_SUFFIXES = {".onnx", ".pt", ".pth", ".pdmodel", ".pb", ".safetensors"};

    @Override
    /**
     * 列表模型bycapability
     * @return 返回值
    */
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
        // discoverAll 会把模型根重置为库默认值，必须重新推回持久化的根，
        // 否则本地模型按相对路径探测时全部落空（/v2/models 恒为空）
        onnxExternalDirectoryService.applyModelRoot();
        // 1. 本地模型：按能力接口归类
        for (ModelRegistry.Entry entry : ModelRegistry.getAll()) {
            // 硬件过滤：当前机器跑不动的模型（GPU/显存/内存不足）不进能力目录，防止选中后 OOM/崩溃
            if (!ModelHardwareFilter.isSupported(entry)) {
                continue;
            }
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
    /**
     * 列表模型
     * @param capability 字符串
     * @return 返回值
    */
    public List<String> listModels(String capability) {
        return listModelsByCapability().getOrDefault(capability, List.of());
    }

    @Override
    /**
     * 列表capabilities
     * @return 返回值
    */
    public List<String> listCapabilities() {
        return new ArrayList<>(listModelsByCapability().keySet());
    }

    /**
     * 本地模型详情缓存（只读探测，不触发下载）；TTL 内复用，避免每次打开选择器都做大量路径探测。
     *
     * <p>过期后按 stale-while-revalidate 口径处理：请求线程立即返回旧快照，
     * 由后台单飞线程重算。全量类路径扫描（ModelRegistry.discoverAll）在 fat jar 与
     * 内存分页压力下单次可达数十秒，若在请求线程内持锁重算，并发的设置读取会全部
     * 排队挂死（实测坏窗口 8~25 分钟）。本地模型文件极少变化，短暂旧值可接受；
     * 外部目录变更仍由 {@link #onModelDirsChanged} 即时失效。</p>
     */
    private volatile List<Map<String, Object>> localDetailsCache;
    private volatile long localDetailsCacheAt;
    private static final long LOCAL_DETAILS_TTL_MS = 60_000L;

    /**
     * 本地模型目录专用锁：与能力清单缓存的 this 锁分离，
     * 两类扫描互不排队（discoverAll 只做 SPI 类加载，并发安全）。
     */
    private final Object localDetailsLock = new Object();

    /**
     * 后台刷新单飞标记（同一时刻至多一个线程在重算）
     */
    private final AtomicBoolean localDetailsRefreshing = new AtomicBoolean(false);

    /**
     * 后台刷新线程池：单线程、守护线程，进程退出不阻塞
     */
    private final ExecutorService localDetailsRefresher = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ai-model-catalog-refresh");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public List<Map<String, Object>> listLocalModelDetails() {
        List<Map<String, Object>> cache = localDetailsCache;
        if (cache != null && System.currentTimeMillis() - localDetailsCacheAt < LOCAL_DETAILS_TTL_MS) {
            return cache;
        }
        if (cache != null) {
            // 过期但可用：立即返回旧值，重算交给后台单飞线程
            triggerLocalDetailsRefresh();
            return cache;
        }
        // 首次访问（无任何快照）：同步计算，保持原有语义
        synchronized (localDetailsLock) {
            cache = localDetailsCache;
            if (cache != null && System.currentTimeMillis() - localDetailsCacheAt < LOCAL_DETAILS_TTL_MS) {
                return cache;
            }
            return computeAndStoreLocalDetails();
        }
    }

    /**
     * 触发一次后台重算（已在跑则跳过）。
     */
    private void triggerLocalDetailsRefresh() {
        if (!localDetailsRefreshing.compareAndSet(false, true)) {
            return;
        }
        try {
            localDetailsRefresher.execute(() -> {
                try {
                    synchronized (localDetailsLock) {
                        // 双检：等锁期间别的路径刚算完就不再重复扫描
                        if (System.currentTimeMillis() - localDetailsCacheAt < LOCAL_DETAILS_TTL_MS) {
                            return;
                        }
                        computeAndStoreLocalDetails();
                    }
                } catch (Throwable e) {
                    // 重算失败沿用旧快照，不打断业务
                    log.warn("[AiModelCatalog] 后台刷新本地模型目录失败，沿用旧快照: {}", e.getMessage());
                } finally {
                    localDetailsRefreshing.set(false);
                }
            });
        } catch (RejectedExecutionException e) {
            // 线程池已关闭（停机中）：放弃本次后台刷新，不影响调用方
            localDetailsRefreshing.set(false);
        }
    }

    /**
     * 计算并落缓存（调用方需持有 {@link #localDetailsLock}）。
     *
     * @return 新计算的本地模型详情
     */
    private List<Map<String, Object>> computeAndStoreLocalDetails() {
        List<Map<String, Object>> computed = computeLocalModelDetails();
        localDetailsCache = computed;
        localDetailsCacheAt = System.currentTimeMillis();
        return computed;
    }

    /**
     * 停机时释放后台刷新线程。
     */
    @PreDestroy
    void shutdownLocalDetailsRefresher() {
        localDetailsRefresher.shutdownNow();
    }

    /**
     * 外部模型目录变更后清空本地模型目录清单缓存。
     *
     * <p>该清单带 TTL 缓存，而外部目录是在 {@code ApplicationReadyEvent} 阶段才注册进
     * {@code ModelRegistry} 的。若缓存在注册之前被填充，ONNX 提供商的「刷新模型」在 TTL 内
     * 看不到这些目录，列表会是空的。收到变更事件后立即失效，避免等 TTL 自然过期。</p>
     *
     * @param event 目录变更事件
     */
    @org.springframework.context.event.EventListener
    public void onModelDirsChanged(
            com.chua.starter.ai.support.manage.service.OnnxModelDirsChangedEvent event) {
        localDetailsCache = null;
        localDetailsCacheAt = 0L;
        log.debug("[AiModelCatalog] 外部目录变更，已清空本地模型目录缓存 root={}", event.modelRoot());
    }

    /**
     * 计算本地模型详情：只读探测本地可用性，不调用 resolveModelPath（避免触发联网下载）。
     *
     * @return 本地模型详情列表，每项含 modelId、capability、available、localPath 等
     */
    private List<Map<String, Object>> computeLocalModelDetails() {
        // 顺序敏感：必须先把持久化的模型根推回注册表，再触发发现。
        // 反过来 discoverAll 会在库默认根（通常不存在）下扫描，外部目录一个都发现不了，
        // 本地模型清单就会缺失这些目录，ONNX 提供商「刷新模型」返回空
        onnxExternalDirectoryService.applyModelRoot();
        try {
            ModelRegistry.discoverAll();
        } catch (Exception e) {
            log.debug("[AiModelCatalog] 模型注册表发现失败: {}", e.getMessage());
        }
        List<Map<String, Object>> result = new ArrayList<>();
        onnxExternalDirectoryService.applyModelRoot();
        for (ModelRegistry.Entry entry : ModelRegistry.getAll()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("modelId", entry.modelId());
            item.put("capability", ModelCapabilities.labelOf(entry.capabilityInterface()));
            item.put("recommended", entry.isRecommended());
            // 硬件过滤判定：false 表示当前机器跑不动（显存/内存不足），前端据此置灰/拦截选择
            item.put("supported", ModelHardwareFilter.isSupported(entry));
            item.put("relativePath", entry.relativePath());
            item.put("downloadUrl", entry.downloadUrl());

            Path resolved = probeAvailablePath(entry.modelId(), entry.relativePath());
            boolean available = resolved != null && Files.exists(resolved);
            String localPath = resolved == null ? null : resolved.toAbsolutePath().normalize().toString();
            // 磁盘上找不到时再查一次 classpath 资源：权重构件（utils-support-models-onnx-*）
            // 是以 jar 内资源发布的，推理时由 ModelRegistry 按 classpath 解析，
            // 只查磁盘会把「运行时明明能跑」的模型报成未就绪 —— 模型选择器据此过滤，
            // 用户就选不到（实测 OCR 的 paddleocrv6-det/rec 正是这种：能识别，但列表显示未下载）。
            if (!available) {
                String cp = classpathResource(entry.relativePath());
                if (cp != null) {
                    available = true;
                    localPath = cp;
                }
            }
            item.put("available", available);
            item.put("localPath", localPath);
            result.add(item);
        }
        return result;
    }

    /**
     * 探测 classpath 上是否存在该模型资源（jar 内权重）。
     *
     * <p>权重构件的发布形态就是 jar 内资源（{@code ocr/PP-OCRv6/tiny/det_infer/inference.onnx}），
     * 推理阶段由 {@code ModelRegistry} 按 classpath 解析，因此可用性判定必须同口径。
     * 命中时返回 {@code classpath:} 前缀的标识，供管理面展示「权重来自 classpath」。</p>
     *
     * @param relativePath 注册条目声明的相对路径
     * @return 命中返回 {@code classpath:<path>}；未命中返回 {@code null}
     */
    private static String classpathResource(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        String cfg = relativePath.trim();
        if (cfg.startsWith("classpath:")) {
            cfg = cfg.substring("classpath:".length());
        }
        while (cfg.startsWith("/")) {
            cfg = cfg.substring(1);
        }
        if (cfg.isBlank()) {
            return null;
        }
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) {
                cl = AiModelCatalogServiceImpl.class.getClassLoader();
            }
            // 模型条目可能声明到目录（.../det_infer）或具体文件（.../inference.onnx），
            // 两者都按资源名探一次：目录在 jar 里没有资源条目本身，故补一次代表性文件。
            if (cl.getResource(cfg) != null) {
                return "classpath:" + cfg;
            }
            String probe = cfg.endsWith(".onnx") ? cfg : cfg + "/inference.onnx";
            if (cl.getResource(probe) != null) {
                return "classpath:" + probe;
            }
        } catch (Exception ignored) {
            // 探测失败按「不可用」处理，不影响主流程
        }
        return null;
    }

    /**
     * 轻量本地可用性探测：仅检查绝对路径 / 模型根目录 / 工作目录，以及显式 classpath: 引用；
     * 不对下载型条目做昂贵的 classpath 全量扫描（下载由 resolveModelPath 在推理时处理）。
     *
     * <p>relativePath 未命中时，继续按 {@link ModelRegistry#resolveModelPath} 的本地判定
     * 口径探测模型根精确形态与外部目录注册，保持「探针判定可用」与「请求时真的能解析到
     * 本地文件」同口径：命名条目的 relativePath 指向仓库内布局，部署现场往往由
     * registerExternalDirectory 注册的外部目录供给，只探 relativePath 会把运行时可用的
     * 模型报成缺失（实测人脸检测 scrfd-face-detector / 特征 faceplugin-face-feature）。
     * 全程只读文件系统，不触发联网下载。</p>
     *
     * @param modelId      注册条目的模型标识
     * @param relativePath 注册条目声明的相对路径
     * @return 本地已存在的路径；不存在返回 空
     */
    private Path probeAvailablePath(String modelId, String relativePath) {
        if (relativePath != null && !relativePath.isBlank()) {
            String cfg = relativePath.trim();
            if (cfg.startsWith("classpath:")) {
                try {
                    return ModelRegistry.resolveConfiguredPath(cfg);
                } catch (Exception e) {
                    return null;
                }
            }
            try {
                Path p = Paths.get(cfg);
                if (p.isAbsolute() && Files.exists(p)) {
                    return p;
                }
                Path underRoot = Paths.get(ModelRegistry.getModelRootDir()).resolve(cfg);
                if (Files.exists(underRoot)) {
                    return underRoot;
                }
                if (!p.isAbsolute() && Files.exists(p)) {
                    return p.toAbsolutePath().normalize();
                }
            } catch (Exception e) {
                return null;
            }
        }
        return probeAvailableByModelIdShapes(modelId);
    }

    /**
     * 按 {@link ModelRegistry#resolveModelPath} 的本地形态探测模型是否在盘上：
     * 模型根下的精确文件、每模型一目录布局、已注册的外部目录。
     *
     * @param modelId 模型标识
     * @return 本地已存在的路径；不存在返回 空
     */
    private Path probeAvailableByModelIdShapes(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return null;
        }
        try {
            Path root = Paths.get(ModelRegistry.getModelRootDir());
            for (String suffix : LOCAL_MODEL_SUFFIXES) {
                Path exact = root.resolve(modelId + suffix);
                if (Files.exists(exact)) {
                    return exact;
                }
                Path inModelDir = root.resolve(modelId).resolve("model" + suffix);
                if (Files.exists(inModelDir)) {
                    return inModelDir;
                }
            }
            Path external = ModelRegistry.findExternalDirectory(modelId);
            if (external != null && Files.exists(external)) {
                return external;
            }
        } catch (Exception ignore) {
            // 探测失败按「不可用」处理，不影响主流程
        }
        return null;
    }

    /**
     * 合并云端模型到分组。
     * @param grouped 群体
     * @param capability capability
     * @param models 模型
     */
    private void mergeCloud(Map<String, LinkedHashSet<String>> grouped, String capability, List<String> models) {
        if (CollectionUtils.isEmpty(models)) {
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

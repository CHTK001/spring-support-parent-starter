package com.chua.starter.ai.support.configuration.paper;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.utils.StringUtils;
import com.chua.deeplearning.support.layout.LayoutDetector;
import com.chua.deeplearning.support.model.PredictRectangle;
import com.chua.deeplearning.support.ocr.OcrPipeline;
import com.chua.deeplearning.support.ocr.OcrResult;
import com.chua.deeplearning.support.paper.PaperClient;
import com.chua.deeplearning.support.paper.PaperPipeline;
import com.chua.starter.ai.support.chat.ChatClientSettingsResolver;
import com.chua.starter.ai.support.chat.ModelClientResolver;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.configuration.properties.DefaultModelResolver;
import com.chua.starter.ai.support.configuration.client.ModelIds;
import com.chua.starter.ai.support.configuration.layout.LayoutClientManager;
import com.chua.starter.ai.support.configuration.ocr.OcrClientManager;
import com.chua.starter.ai.support.registry.ChatClientRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 试卷识别客户端管理器（ai-starter 直接提供的试卷 Bean）。
 *
 * <p>按 {@code paper} 分组全局设置（sys_ai_global_setting）提供两条试卷识别管线：</p>
 * <ul>
 *   <li><b>OCR</b>（engine=ocr）：版面分析 → 按文本区裁剪 → OCR，输出带坐标的文字块。</li>
 *   <li><b>OCR + LLM</b>（engine=ocr+llm）：OCR 文本再交给大模型结构化，
 *       输出「题号 / 题干 / 作答 / 分值」数组，供外来卷等无题库场景判分。</li>
 * </ul>
 *
 * <p>模型字段优先读 {@code paper} 分组；留空时回落 {@code ocr} / {@code layout} 分组，
 * 与现有分组配置共用一套模型实例。配置变更通过 {@link PaperSettingsChangedEvent}
 * 热重建，实时生效。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Component
public class PaperClientManager implements PaperClient {

    /**
     * 全局设置键值分组
     */
    public static final String GROUP = "paper";

    /**
     * 引擎：纯 OCR
     */
    public static final String ENGINE_OCR = "ocr";

    /**
     * 引擎：OCR + LLM 结构化
     */
    public static final String ENGINE_OCR_LLM = "ocr+llm";

    /**
     * JSON 提取正则：从 LLM 输出里抽出最外层 JSON 数组/对象。
     */
    private static final Pattern JSON_BLOCK = Pattern.compile(
            "(\\{[\\s\\S]*\\}|\\[[\\s\\S]*\\])", Pattern.DOTALL);

    /**
     * 全局设置读取工具
     */
    private final GlobalSettingReader settingReader;

    /**
     * OCR 客户端管理器（复用 ocr 分组的共享实例）
     */
    private final OcrClientManager ocrClientManager;

    /**
     * 版面检测客户端管理器（复用 layout 分组的共享实例）
     */
    private final LayoutClientManager layoutClientManager;

    /**
     * 对话客户端注册中心（OCR+LLM 引擎的结构化调用）
     */
    private final ChatClientRegistry chatClientRegistry;

    /**
     * 默认模型解析器：取「系统设置 → 默认模型」里生效的对话模型。
     *
     * <p>为什么需要：Spring 容器里的 {@code AggregateChatClient} Bean 是默认客户端，
     * 但它只有在装配了主客户端（{@code .chatClient(...)}）时才能调用；本机未配置
     * {@code plugin.ai.chat.*} 时它是个空壳，调用直接抛
     * 「未配置主 ChatClient，请先调用 .chatClient(...)」。
     * 因此这里按「默认对话模型」现建客户端兜底。</p>
     */
    private final DefaultModelResolver defaultModelResolver;

    /**
     * 按「providerId:model」从供应商表构建对话客户端（与聊天链路同源）。
     */
    private final ModelClientResolver modelClientResolver;

    /**
     * 当前引擎指纹（engine + 模型 + 阈值），用于轻量热重建判断
     */
    private volatile String fingerprint = "";

    /**
     * 试卷识别管线（utils-xx 能力门面，按 paper 分组配置装配后开花）。
     *
     * <p>识别逻辑统一下沉到 {@link PaperPipeline}（OcrPipeline + LayoutDetector +
     * ChatClient 组合）；本类退化为「装配层 + 配置热更新」：从 {@code paper} 分组
     * 读取模型与引擎配置，装配并缓存一条 {@link PaperPipeline}，配置变更事件触发重建。
     * 业务侧（eduai 等）只需要看这层暴露的方法，不再与 OCR/版面具体管理器握手。</p>
     */
    private volatile PaperPipeline pipeline;

    /**
     * JSON 解析器
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构造试卷识别管理器。
     *
     * @param settingReader      全局设置读取工具
     * @param ocrClientManager   OCR 客户端管理器
     * @param layoutClientManager 版面检测客户端管理器
     * @param chatClientRegistry 对话客户端注册中心
     * @param defaultModelResolver 默认模型解析器
     * @param modelClientResolver 供应商级对话客户端解析器
     */
    public PaperClientManager(GlobalSettingReader settingReader,
                              OcrClientManager ocrClientManager,
                              LayoutClientManager layoutClientManager,
                              ChatClientRegistry chatClientRegistry,
                              DefaultModelResolver defaultModelResolver,
                              ModelClientResolver modelClientResolver) {
        this.settingReader = settingReader;
        this.ocrClientManager = ocrClientManager;
        this.layoutClientManager = layoutClientManager;
        this.chatClientRegistry = chatClientRegistry;
        this.defaultModelResolver = defaultModelResolver;
        this.modelClientResolver = modelClientResolver;
    }

    /**
     * 试卷识别管线（utils-xx 能力门面，懒装配 + 配置变更重建）。
     *
     * <p>从 {@code paper} 分组配置装配部件：OCR 用 {@link OcrClientManager#current()}
     * （复用 ocr 分组的共享 OcrPipeline）、版面用 {@link LayoutClientManager#current()}
     * （复用 layout 分组的共享 LayoutDetector）、LLM 用 {@link #resolveLlmClient()}
     * （paper 分组 provider/model，回落到默认对话模型）。配置指纹变化时重建。</p>
     *
     * @return 试卷识别管线；OCR 部件缺失时返回 null（识别走 {@code available()} 判不可用）
     */
    public PaperPipeline pipeline() {
        PaperPipeline current = pipeline;
        // pipelineFingerprint 非空表示「这一版配置已经尝试装配过」，无论成功与否。
        // 用它区分「尚未装配」与「装配失败」：后者不再每次重试，避免模型加载反复失败刷日志。
        if (current == null && pipelineFingerprint == null) {
            synchronized (this) {
                current = pipeline;
                if (current == null && pipelineFingerprint == null) {
                    current = buildPipeline();
                    pipeline = current;
                    pipelineFingerprint = fingerprint;
                }
            }
            return current;
        }
        // 配置变更后轻量重算指纹：指纹不同则重装一次（事件监听已触发 refreshIfChanged）
        refreshIfChanged();
        if (pipelineFingerprint != null && !pipelineFingerprint.equals(fingerprint)) {
            synchronized (this) {
                if (pipelineFingerprint != null && !pipelineFingerprint.equals(fingerprint)) {
                    current = buildPipeline();
                    pipeline = current;
                    pipelineFingerprint = fingerprint;
                }
            }
        }
        return pipeline;
    }

    /**
     * 已装配管线的指纹（用于轻量重建判断；null 表示尚未装配过）。
     */
    private volatile String pipelineFingerprint;

    /**
     * 按 {@code paper} 分组配置装配 {@link PaperPipeline}。
     *
     * @return 装配好的管线；OCR 部件缺失时返回 null
     */
    private PaperPipeline buildPipeline() {
        OcrPipeline ocr = null;
        try {
            ocr = ocrClientManager.current();
        } catch (Exception e) {
            log.warn("[PaperClientManager] OCR 部件不可用，试卷识别不可用: {}", e.getMessage());
            return null;
        }
        LayoutDetector layout = null;
        try {
            layout = layoutClientManager.current();
        } catch (Exception e) {
            // 版面为可选项：缺失时 PaperPipeline 退化为整图 OCR
            log.debug("[PaperClientManager] 版面部件不可用，试卷管线按整图 OCR 装配: {}", e.getMessage());
        }
        ChatClient llm = resolveLlmClient();
        PaperPipeline built = PaperPipeline.builder()
                .layout(layout)
                .ocr(ocr)
                .llm(llm)
                .engine(engine())
                .mergeAnswer(settingReader.getBool(GROUP, "merge_answer", true))
                .build();
        log.info("[PaperClientManager] 试卷管线已装配: engine={}, layout={}, ocr={}, llm={}",
                built.engine(), layout != null ? "on" : "off",
                ocr != null ? "on" : "off", llm != null ? "on" : "off");
        return built;
    }

    /**
     * 当前生效的引擎。
     *
     * @return ocr / ocr+llm
     */
    @Override
    public String engine() {
        String engine = settingReader.getStr(GROUP, "engine");
        if (!ENGINE_OCR.equals(engine) && !ENGINE_OCR_LLM.equals(engine)) {
            return ENGINE_OCR;
        }
        return engine;
    }

    /**
     * 试卷识别管线是否真的可用。
     *
     * <p><b>不能用 {@link OcrClientManager#available()} 代替</b>：后者只判断配置里是否填了
     * 模型名，而管线装配还要能真正加载到模型文件。模型资产缺失时
     * {@code available()} 会返回 true、{@code pipeline()} 却返回 null，
     * 调用方据此放行后会在识别阶段撞上异常。这里以「管线能否装配」为准。</p>
     *
     * @return true 表示可以执行识别
     */
    @Override
    public boolean available() {
        PaperPipeline pp = pipeline();
        return pp != null && pp.available();
    }

    /**
     * 纯 OCR 识别：版面分析后按文本区逐区识别，返回带坐标的文字块。
     *
     * <p>委托给 {@link PaperPipeline#recognizeDetail(byte[])}（utils-xx 能力门面，
     * 内含版面召回保护与阅读顺序排序）；管线未装配时走原 {@link OcrClientManager}
     * 直调回退，保证可用性不因装配失败而降低。</p>
     *
     * @param imageData 图片字节
     * @return 识别结果列表（按阅读顺序排序）
     */
    @Override
    public List<OcrResult> recognizeDetail(byte[] imageData) {
        PaperPipeline pp = pipeline();
        if (pp != null) {
            return pp.recognizeDetail(imageData);
        }
        return recognizeDetailFallback(imageData);
    }

    /**
     * 原直调回退路径：管线未装配时用 OcrClientManager 直识（含版面召回保护）。
     *
     * @param imageData 图片字节
     * @return 识别结果列表
     */
    private List<OcrResult> recognizeDetailFallback(byte[] imageData) {
        refreshIfChanged();
        List<PredictRectangle> textRegions = textRegions(imageData);
        if (textRegions.isEmpty()) {
            return ocrClientManager.recognizeDetail(imageData);
        }
        List<OcrResult> byRegion = ocrClientManager.recognizeRegions(imageData, textRegions);
        // 质量保护：版面模型的文本区召回直接决定最终能识别多少字。
        // 实测 rtdetr-layout 在这张扫描卷上只框出「姓名/学号/班级」3 个区域，
        // 按区域识别后标题、题干、页码全丢（10 块 → 3 块）。
        // 因此区域结果字符数明显少于整图时，回退整图识别，宁可慢一点也不能漏内容。
        List<OcrResult> whole = ocrClientManager.recognizeDetail(imageData);
        long regionChars = chars(byRegion);
        long wholeChars = chars(whole);
        List<OcrResult> results;
        if (wholeChars > regionChars) {
            log.info("[PaperClientManager] 版面文本区召回不足（region={} 字 / whole={} 字），改用整图 OCR",
                    regionChars, wholeChars);
            results = whole;
        } else {
            results = byRegion;
        }
        return results.stream()
                .sorted(Comparator.comparingDouble((OcrResult o) -> o.boundingBox() == null ? 0d : o.boundingBox().y())
                        .thenComparingDouble(o -> o.boundingBox() == null ? 0d : o.boundingBox().x()))
                .collect(Collectors.toList());
    }

    /**
     * 统计识别结果的有效字符数（忽略空白块），用于比较两条识别路径的完整度。
     *
     * @param results 识别结果
     * @return 有效字符数
     */
    private static long chars(List<OcrResult> results) {
        if (results == null || results.isEmpty()) {
            return 0L;
        }
        return results.stream()
                .map(OcrResult::text)
                .filter(StringUtils::isNotBlank)
                .mapToLong(t -> t.replaceAll("\\s", "").length())
                .sum();
    }

    /**
     * 试卷识别：按当前引擎管线执行。
     *
     * <p>委托给 {@link PaperPipeline#recognize(byte[])}（utils-xx 能力门面：
     * OCR 返回 blocks，OCR+LLM 返回 questions）；管线未装配时走原
     * {@link #recognizeFallback(byte[])} 回退。</p>
     *
     * @param imageData 图片字节
     * @return 识别结果（{@code engine} + {@code questions} 数组或 {@code blocks} 数组）
     */
    @Override
    public Map<String, Object> recognize(byte[] imageData) {
        PaperPipeline pp = pipeline();
        if (pp != null) {
            return pp.recognize(imageData);
        }
        return recognizeFallback(imageData);
    }

    /**
     * 原直调回退路径：管线未装配时按 engine 走本类原实现。
     *
     * @param imageData 图片字节
     * @return 识别结果
     */
    private Map<String, Object> recognizeFallback(byte[] imageData) {
        String engine = engine();
        if (ENGINE_OCR_LLM.equals(engine)) {
            return structureWithLlm(imageData);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("engine", ENGINE_OCR);
        out.put("blocks", recognizeDetail(imageData));
        return out;
    }

    /**
     * 版面检测：返回按标签分组的检测框。
     *
     * <p>委托给 {@link PaperPipeline#layoutRegions(byte[])}；管线未装配时用
     * {@link LayoutClientManager} 直取，保证版面能力不因装配失败而丢失。</p>
     *
     * @param imageData 图片字节
     * @return 标签 → 检测框列表；不可用时为空 Map
     */
    @Override
    public Map<String, List<PredictRectangle>> layoutRegions(byte[] imageData) {
        PaperPipeline pp = pipeline();
        if (pp != null) {
            return pp.layoutRegions(imageData);
        }
        try {
            Map<String, List<PredictRectangle>> grouped = layoutClientManager.detect(imageData);
            return grouped == null ? new LinkedHashMap<>() : grouped;
        } catch (Exception e) {
            log.warn("[PaperClientManager] 版面检测失败: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /**
     * 获取试卷结构化的默认提示词。
     *
     * @return 提示词
     */
    public String defaultPrompt() {
        return """
                你是一名试卷结构解析器。下面是扫描识别的文字块，请按题目出现顺序，
                把每一题的结构化信息提取出来，只输出 JSON 数组，禁止解释。

                每个元素字段：
                {"no":"题号，如 1.","stem":"题干（含选项 A./B./C./D.）","answer":"学生作答","maxScore":"分值(数字,没有给5)"}

                注意：
                1. 学生作答只取学生手写/填写的部分；题干、选项、分值标注、页眉水印（如"第N页"）不要混入 answer。
                2. 填空题空位用______表示；答案在题干后面时，答案与题干要分开。
                3. 判断题/选择题把括号里的符号或字母作为 answer（如 √ / C）。
                4. 简答/翻译题 answer 取学生实际书写内容，识别不出就留空字符串。

                文字块：
                %s
                """;
    }

    /**
     * 提取需要 OCR 的文本类版面区域（复用 layout 分组的文本区判定）。
     *
     * @param imageData 图片字节
     * @return 文本区域列表
     */
    private List<PredictRectangle> textRegions(byte[] imageData) {
        Map<String, List<PredictRectangle>> grouped;
        try {
            grouped = layoutClientManager.detect(imageData);
        } catch (Exception ex) {
            // 版面模型未配置（layout_model 留空且 layout 分组也没配）时，
            // LayoutClientManager.current() 抛 IllegalStateException。版面分析属于
            // 「可选增强」：调用方 recognizeDetail 本就有「regions 为空 → 整图 OCR」
            // 的回退分支，这里必须让它走到那条分支，而不是把试卷识别整体打挂
            // （实测：只配 OCR 的部署下 POST /v2/paper/recognize 直接 500）。
            log.info("[PaperClientManager] 版面分析不可用，回退整图 OCR: {}", ex.getMessage());
            return new ArrayList<>();
        }
        List<PredictRectangle> regions = new ArrayList<>();
        for (Map.Entry<String, List<PredictRectangle>> entry : grouped.entrySet()) {
            regions.addAll(entry.getValue());
        }
        if (regions.isEmpty()) {
            return regions;
        }
        boolean mergeAnswer = settingReader.getBool(GROUP, "merge_answer", true);
        if (mergeAnswer) {
            regions.sort(Comparator.comparingDouble(PredictRectangle::y)
                    .thenComparingDouble(PredictRectangle::x));
        }
        return regions;
    }

    /**
     * OCR + LLM 结构化识别。
     *
     * <p>大模型不可用 / 返回空 / 返回非 JSON 时<b>不返回空结果</b>：
     * OCR 已经跑过一遍，把文字块一并带回（{@code blocks} + {@code fallback} 标记），
     * 调用方至少能拿到可判分的文本，而不是"识别成功但什么都没有"。
     * 实测踩过的坑：推理型模型（如 agnes-2.5-flash）把内容全放
     * {@code reasoning_content}，{@code content} 恒为空 → questions 恒空。</p>
     *
     * @param imageData 图片字节
     * @return 识别结果（engine + questions，必要时附 blocks 与 fallback）
     */
    private Map<String, Object> structureWithLlm(byte[] imageData) {
        List<OcrResult> blocks = recognizeDetail(imageData);
        String text = blocks.stream()
                .map(OcrResult::text)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining("\n"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("engine", ENGINE_OCR_LLM);
        if (StringUtils.isBlank(text)) {
            out.put("questions", List.of());
            out.put("fallback", "ocr-empty");
            return out;
        }
        String prompt = defaultPrompt().formatted(text);
        List<Map<String, Object>> questions = callLlm(prompt);
        out.put("questions", questions);
        if (questions.isEmpty()) {
            // 结构化失败：退回文字块，避免前端拿到 0 题还以为整页没字
            out.put("blocks", blocks);
            out.put("fallback", "llm-empty");
            log.warn("[PaperClientManager] 结构化无结果，已回退 {} 个文字块供上层使用", blocks.size());
        }
        return out;
    }

    /**
     * 调用大模型做结构化解析。
     *
     * @param prompt 用户提示词
     * @return 结构化题目数组；解析失败返回空列表
     */
    private List<Map<String, Object>> callLlm(String prompt) {
        ChatClient client = resolveLlmClient();
        if (client == null) {
            log.warn("[PaperClientManager] 未配置可用的结构化大模型，OCR+LLM 引擎回退为空");
            return List.of();
        }
        try {
            String raw = client.chatSync(prompt);
            Matcher matcher = JSON_BLOCK.matcher(raw == null ? "" : raw);
            if (!matcher.find()) {
                log.warn("[PaperClientManager] 大模型未返回 JSON: {}", raw == null ? "" : raw.substring(0, Math.min(120, raw.length())));
                return List.of();
            }
            JsonNode node = objectMapper.readTree(matcher.group(1));
            if (!node.isArray()) {
                node = node.path("questions");
            }
            List<Map<String, Object>> questions = new ArrayList<>();
            if (node.isArray()) {
                for (JsonNode item : node) {
                    Map<String, Object> q = new LinkedHashMap<>();
                    q.put("no", item.path("no").asText(""));
                    q.put("stem", item.path("stem").asText(""));
                    q.put("answer", item.path("answer").asText(""));
                    q.put("maxScore", item.path("maxScore").asInt(5));
                    questions.add(q);
                }
            }
            return questions;
        } catch (Exception e) {
            log.warn("[PaperClientManager] OCR+LLM 结构化失败: {}", e.toString());
            return List.of();
        }
    }

    /**
     * 解析 LLM 客户端：优先按 paper 分组 provider/model，否则用默认聚合客户端。
     *
     * @return ChatClient，找不到返回 空
     */
    private ChatClient resolveLlmClient() {
        String provider = settingReader.getStr(GROUP, "llm_provider");
        String model = settingReader.getStr(GROUP, "llm_model");
        if (StringUtils.isNotBlank(provider)) {
            ChatClient byProvider = chatClientRegistry.getByProvider(provider);
            if (byProvider != null) {
                return byProvider;
            }
        }
        if (StringUtils.isNotBlank(model)) {
            ChatClient byModel = chatClientRegistry.getByModel(model);
            if (byModel != null) {
                return byModel;
            }
            // 注册中心只登记了「容器里装配过的客户端」；显式指定了模型名但没登记时，
            // 按 supplier 表现建，避免「填了模型却不生效」
            ChatClient builtByModel = modelClientResolver.resolve(model);
            if (builtByModel != null) {
                log.info("[PaperClientManager] 结构化改用指定模型现建客户端: {}", model);
                return builtByModel;
            }
        }
        // 回落到「系统设置 → 默认模型」的对话模型：先取注册中心里已建好的实例，
        // 再退一步按 providerId:model 现建。直接返回 getDefault() 不可靠 ——
        // 容器里的 AggregateChatClient 在未装配主客户端时是空壳，调用必抛
        // 「未配置主 ChatClient」，实测 OCR+LLM 因此永远出不了结果。
        String defaultChat = defaultModelResolver.chat();
        if (StringUtils.isNotBlank(defaultChat)) {
            ChatClient registered = chatClientRegistry.getByModel(defaultChat);
            if (registered != null) {
                return registered;
            }
            ChatClient built = modelClientResolver.resolve(defaultChat);
            if (built != null) {
                log.info("[PaperClientManager] 结构化改用默认对话模型现建客户端: {}", defaultChat);
                return built;
            }
            log.warn("[PaperClientManager] 默认对话模型无法构建客户端: {}", defaultChat);
        }
        return chatClientRegistry.getDefault();
    }

    /**
     * 配置指纹（engine + 各模型 + 阈值），用于轻量判断是否需要重建。
     *
     * @return 指纹字符串
     */
    private String readFingerprint() {
        return String.join("|",
                engine(),
                StringUtils.defaultString(settingReader.getStr(GROUP, "layout_model"), ""),
                StringUtils.defaultString(settingReader.getStr(GROUP, "ocr_detector_model"), ""),
                StringUtils.defaultString(settingReader.getStr(GROUP, "ocr_recognizer_model"), ""),
                String.valueOf(settingReader.getDecimalOrNull(GROUP, "ocr_threshold")),
                StringUtils.defaultString(settingReader.getStr(GROUP, "llm_provider"), ""),
                StringUtils.defaultString(settingReader.getStr(GROUP, "llm_model"), ""),
                StringUtils.defaultString(settingReader.getStr(GROUP, "device"), ""));
    }

    /**
     * 配置变更后重建（轻量：仅重算指纹并触发 OCR/版面管理器自身重建）。
     */
    private void refreshIfChanged() {
        String currentFp = readFingerprint();
        if (!currentFp.equals(fingerprint)) {
            synchronized (this) {
                String latest = readFingerprint();
                if (!latest.equals(fingerprint)) {
                    this.fingerprint = latest;
                    log.info("[PaperClientManager] 试卷配置变更，管线指纹更新: {}", latest);
                }
            }
        }
    }

    /**
     * 全局配置变更监听：paper 分组更新后重建。
     *
     * @param event 配置变更事件
     */
    @EventListener
    public void onPaperSettingsChanged(PaperSettingsChangedEvent event) {
        if (event == null || !GROUP.equals(event.group())) {
            return;
        }
        if (event.userScoped()) {
            log.debug("[PaperClientManager] 收到用户级 paper 配置写入，共享试卷管线不重建");
            return;
        }
        log.info("[PaperClientManager] 收到 paper 分组配置变更，准备重建");
        refreshIfChanged();
    }

    /**
     * 读取 paper 分组单独配置的布局模型（回落 layout 分组）。
     *
     * @return 布局模型名（裸名）
     */
    public String layoutModel() {
        String model = settingReader.getStr(GROUP, "layout_model");
        if (StringUtils.isNotBlank(model)) {
            return ModelIds.bare(model);
        }
        return ModelIds.bare(settingReader.getStr("layout", "layout_model"));
    }

    /**
     * 读取 paper 分组单独配置的检测模型（回落 ocr 分组）。
     *
     * @return 检测模型名（裸名）
     */
    public String ocrDetectorModel() {
        String model = settingReader.getStr(GROUP, "ocr_detector_model");
        if (StringUtils.isNotBlank(model)) {
            return ModelIds.bare(model);
        }
        return ModelIds.bare(settingReader.getStr("ocr", "detector_model"));
    }

    /**
     * 读取 paper 分组单独配置的识别模型（回落 ocr 分组）。
     *
     * @return 识别模型名（裸名）
     */
    public String ocrRecognizerModel() {
        String model = settingReader.getStr(GROUP, "ocr_recognizer_model");
        if (StringUtils.isNotBlank(model)) {
            return ModelIds.bare(model);
        }
        return ModelIds.bare(settingReader.getStr("ocr", "recognizer_model"));
    }
}
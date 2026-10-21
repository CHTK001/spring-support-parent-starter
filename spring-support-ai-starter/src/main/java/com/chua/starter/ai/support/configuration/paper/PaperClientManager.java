package com.chua.starter.ai.support.configuration.paper;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.utils.StringUtils;
import com.chua.deeplearning.support.layout.LayoutDetector;
import com.chua.deeplearning.support.model.PredictRectangle;
import com.chua.deeplearning.support.ocr.OcrResult;
import com.chua.starter.ai.support.chat.ChatClientSettingsResolver;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
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
public class PaperClientManager {

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
     * 当前引擎指纹（engine + 模型 + 阈值），用于轻量热重建判断
     */
    private volatile String fingerprint = "";

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
     */
    public PaperClientManager(GlobalSettingReader settingReader,
                              OcrClientManager ocrClientManager,
                              LayoutClientManager layoutClientManager,
                              ChatClientRegistry chatClientRegistry) {
        this.settingReader = settingReader;
        this.ocrClientManager = ocrClientManager;
        this.layoutClientManager = layoutClientManager;
        this.chatClientRegistry = chatClientRegistry;
    }

    /**
     * 当前生效的引擎。
     *
     * @return ocr / ocr+llm
     */
    public String engine() {
        String engine = settingReader.getStr(GROUP, "engine");
        if (!ENGINE_OCR.equals(engine) && !ENGINE_OCR_LLM.equals(engine)) {
            return ENGINE_OCR;
        }
        return engine;
    }

    /**
     * 试卷识别是否已配置可用（OCR 可用即可）。
     *
     * @return true 表示可用
     */
    public boolean available() {
        return ocrClientManager.available();
    }

    /**
     * 纯 OCR 识别：版面分析后按文本区逐区识别，返回带坐标的文字块。
     *
     * @param imageData 图片字节
     * @return 识别结果列表（按阅读顺序排序）
     */
    public List<OcrResult> recognizeDetail(byte[] imageData) {
        refreshIfChanged();
        List<PredictRectangle> textRegions = textRegions(imageData);
        if (textRegions.isEmpty()) {
            return ocrClientManager.recognizeDetail(imageData);
        }
        List<OcrResult> results = ocrClientManager.recognizeRegions(imageData, textRegions);
        return results.stream()
                .sorted(Comparator.comparingDouble((OcrResult o) -> o.boundingBox() == null ? 0d : o.boundingBox().y())
                        .thenComparingDouble(o -> o.boundingBox() == null ? 0d : o.boundingBox().x()))
                .collect(Collectors.toList());
    }

    /**
     * 试卷识别：按当前引擎管线执行。
     *
     * <p>OCR：返回逐题文字块（题目与作答按版面坐标合并，取决于 merge_answer）。
     * OCR+LLM：返回大模型结构化的题目数组（题号 / 题干 / 作答 / 分值）。</p>
     *
     * @param imageData 图片字节
     * @return 识别结果（{@code engine} + {@code questions} 数组或 {@code blocks} 数组）
     */
    public Map<String, Object> recognize(byte[] imageData) {
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
        Map<String, List<PredictRectangle>> grouped = layoutClientManager.detect(imageData);
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
     * @param imageData 图片字节
     * @return 识别结果（engine + questions 数组）
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
            return out;
        }
        String prompt = defaultPrompt().formatted(text);
        List<Map<String, Object>> questions = callLlm(prompt);
        out.put("questions", questions);
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
package com.chua.starter.eduai.ai;

import com.chua.common.support.utils.StringUtils;
import com.chua.deeplearning.support.ocr.OcrResult;
import com.chua.starter.ai.support.configuration.layout.LayoutClientManager;
import com.chua.starter.ai.support.configuration.ocr.OcrClientManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 试卷扫描器 —— 阅卷的「版面还原 + 文字提取 + 结构化」环节。
 *
 * <p>复用 ai-starter 的全局 Bean，不自行实现推理：</p>
 * <ul>
 * <li>{@link LayoutClientManager#detect(byte[])} —— 版面区域（标题/正文/编号等语义标签 + 坐标）</li>
 * <li>{@link OcrClientManager#recognizeDetail(byte[])} —— 文字块（文本 + 置信度 + 坐标）</li>
 * <li>{@link EduAiService} —— 调用大模型把文本结构化（自有卷提题号、任意卷提题干与作答）</li>
 * </ul>
 *
 * <p>两类试卷的处理差异（对应需求 3.1 / 3.2）：</p>
 * <ul>
 * <li><b>自有卷</b>：只需从版面文字里取出「题号」，再拿题号回数据库取准确的分值与标准答案，
 * 因此对大模型只要求「抽题号 + 抽作答」，不要求它理解题目本身，准确率依赖数据库而非模型。</li>
 * <li><b>任意卷</b>：题库里没有对应记录，只能让大模型同时抽出题干与作答并自行判定。</li>
 * </ul>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EduPaperScanner {

    /**
     * 版面分析管理器（ai-starter 全局 Bean；未引入模型资产时为 null）
     */
    private final ObjectProvider<LayoutClientManager> layoutProvider;

    /**
     * OCR 管理器（ai-starter 全局 Bean；未配置 ocr 分组模型时为 null）
     */
    private final ObjectProvider<OcrClientManager> ocrProvider;

    /**
     * AI 服务（用于文本结构化与判分）
     */
    private final EduAiService eduAiService;

    /**
     * JSON 解析器
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 试卷类型：自有卷（题目在本系统题库/试卷里）
     */
    public static final String MODE_SELF = "SELF";

    /**
     * 试卷类型：任意卷（外来试卷，只能靠模型）
     */
    public static final String MODE_ANY = "ANY";

    /**
     * 扫描结果：版面区域 + 文字块 + 拼装文本。
     *
     * @param layoutRegions 版面区域类型 → 区域数（仅用于观测与日志，不参与判分）
     * @param blocks        文字块（按阅读顺序）
     * @param text          按阅读顺序拼接的全文
     */
    public record ScanResult(java.util.Map<String, Integer> layoutRegions, List<ScanBlock> blocks, String text) {

        /**
         * 规范化构造器：集合组件做防御性拷贝。
         *
         * @param layoutRegions 版面区域统计
         * @param blocks        文字块
         * @param text          全文
         */
        public ScanResult {
            layoutRegions = layoutRegions == null ? java.util.Map.of() : java.util.Map.copyOf(layoutRegions);
            blocks = blocks == null ? List.of() : List.copyOf(blocks);
        }
    }

    /**
     * 一个文字块。
     *
     * @param text       识别文本
     * @param confidence 置信度
     * @param x          左上角 x
     * @param y          左上角 y
     * @param width      宽
     * @param height     高
     */
    public record ScanBlock(String text, float confidence, float x, float y, float width, float height) {
    }

    /**
     * 一道被识别出来的题目。
     *
     * @param no     题号（自有卷用于回查数据库；任意卷为模型给出的序号）
     * @param stem   题干（任意卷才有；自有卷以题库为准，此处可为空）
     * @param answer 学生作答
     */
    public record ScannedQuestion(String no, String stem, String answer) {
    }

    /**
     * 版面还原 + 文字提取。
     *
     * <p>版面与 OCR 任一不可用都不抛异常：版面缺失时仍可用 OCR 文本链路，
     * OCR 缺失时直接抛业务异常（没有文字就无法阅卷）。</p>
     *
     * @param imageData 试卷/答题卡图片字节
     * @return 扫描结果
     */
    public ScanResult scan(byte[] imageData) {
        var layoutRegions = new java.util.LinkedHashMap<String, Integer>();
        LayoutClientManager layout = layoutProvider.getIfAvailable();
        if (layout != null) {
            try {
                var regions = layout.detect(imageData);
                if (regions != null) {
                    regions.forEach((k, v) -> layoutRegions.put(k, v == null ? 0 : v.size()));
                }
                log.info("[EduScan] 版面区域: {}", layoutRegions);
            } catch (Exception e) {
                // 版面是增强能力：失败不阻断，OCR 文本链路仍可阅卷
                log.warn("[EduScan] 版面分析失败，降级为纯 OCR: {}", e.getMessage());
            }
        } else {
            log.warn("[EduScan] LayoutClientManager 不可用，跳过版面分析");
        }

        OcrClientManager ocr = ocrProvider.getIfAvailable();
        if (ocr == null) {
            throw new IllegalStateException("OCR 未配置：请检查 sys_ai_global_setting 的 ocr 分组与模型资产是否就位");
        }

        List<OcrResult> results = ocr.recognizeDetail(imageData);
        var blocks = new ArrayList<ScanBlock>();
        var text = new StringBuilder();
        if (results != null) {
            for (OcrResult item : results) {
                if (item == null || StringUtils.isBlank(item.text())) {
                    continue;
                }
                var box = item.boundingBox();
                blocks.add(new ScanBlock(item.text(),
                        item.confidence(),
                        box == null ? 0f : box.x(),
                        box == null ? 0f : box.y(),
                        box == null ? 0f : box.width(),
                        box == null ? 0f : box.height()));
                text.append(item.text()).append('\n');
            }
        }
        log.info("[EduScan] OCR 文字块={}，文本长度={}", blocks.size(), text.length());
        return new ScanResult(layoutRegions, blocks, text.toString());
    }

    /**
     * 自有卷：从版面文字里抽出「题号 + 学生作答」。
     *
     * <p>只要求模型做「抽取」，不要求它理解题目内容与判分 —— 分值与标准答案一律回数据库取，
     * 这是自有卷「准确」的来源。</p>
     *
     * @param text 版面全文
     * @return 抽出的题目列表；模型不可用或解析失败时返回空列表
     */
    public List<ScannedQuestion> extractSelfQuestions(String text) {
        if (StringUtils.isBlank(text)) {
            return List.of();
        }
        String systemPrompt = "你是试卷结构解析器。只输出 JSON，禁止解释。";
        String userPrompt = """
                下面是一份试卷/答题卡的图片识别文本。请抽取每一道题的「题号」与「学生手写作答」。
                题号可能是 1. / 一、 / 第1题 / 编号(如 65-1) 等形式，原样保留。
                学生作答是学生写在该题下方的答案内容；没写就留空字符串。

                JSON Schema:
                {"questions":[{"no":"1","answer":"学生作答"}]}

                识别文本：
                %s
                """.formatted(text);
        String raw = eduAiService.answerQuestion(systemPrompt, userPrompt);
        return parseQuestions(raw, false);
    }

    /**
     * 任意卷：让模型同时抽出「题干 + 学生作答」。
     *
     * <p>题库里没有这份卷子，题干与作答都只能来自识别文本，因此对模型的要求更高。</p>
     *
     * @param text 版面全文
     * @return 抽出的题目列表；模型不可用或解析失败时返回空列表
     */
    public List<ScannedQuestion> extractAnyQuestions(String text) {
        if (StringUtils.isBlank(text)) {
            return List.of();
        }
        String systemPrompt = "你是试卷结构解析器。只输出 JSON，禁止解释。";
        String userPrompt = """
                下面是一份外来试卷/答题卡的图片识别文本。请抽取每一道题，给出序号、题干与学生作答。
                识别可能有错字，按上下文合理还原；无法确定的字段留空字符串。

                JSON Schema:
                {"questions":[{"no":"1","stem":"题干","answer":"学生作答"}]}

                识别文本：
                %s
                """.formatted(text);
        String raw = eduAiService.answerQuestion(systemPrompt, userPrompt);
        return parseQuestions(raw, true);
    }

    /**
     * 任意卷判分：题库无对应记录，只能让模型依据题干与作答自行判定。
     *
     * @param question 题目（题干 + 作答）
     * @param maxScore 满分
     * @return 判分结果；模型不可用或解析失败时回退为 0 分并说明原因
     */
    public EduAiService.MarkingOutcome markAnyQuestion(ScannedQuestion question, int maxScore) {
        var fallback = new EduAiService.MarkingOutcome();
        fallback.setSource("FALLBACK");
        if (StringUtils.isBlank(question.answer())) {
            fallback.setScore(0);
            fallback.setComment("未作答");
            return fallback;
        }
        String systemPrompt = "你是资深阅卷老师。只输出 JSON，禁止解释。";
        String userPrompt = """
                外来试卷没有标准答案，请依据学科常识判断作答是否正确，并给出简短评语（不超过 50 字）。

                题干: %s
                学生作答: %s
                满分: %d

                输出 JSON Schema:
                {"score": 3, "comment": "评语"}
                注意：评分为 0~%d 之间的整数，只返回 JSON。
                """.formatted(question.stem() == null ? "" : question.stem(),
                question.answer() == null ? "" : question.answer(), maxScore, maxScore);
        try {
            String raw = eduAiService.answerQuestion(systemPrompt, userPrompt);
            JsonNode node = MAPPER.readTree(extractJson(raw));
            int score = node.path("score").asInt(0);
            score = Math.max(0, Math.min(score, maxScore));
            var outcome = new EduAiService.MarkingOutcome();
            outcome.setSource("AI");
            outcome.setScore(score);
            outcome.setComment(node.path("comment").asText(""));
            return outcome;
        } catch (Exception e) {
            log.warn("[EduScan] 任意卷判分失败，回退 0 分: {}", e.getMessage());
            fallback.setScore(0);
            fallback.setComment("AI 判分失败：" + e.getMessage());
            return fallback;
        }
    }

    /**
     * 解析模型返回的题目 JSON。
     *
     * @param raw       模型原始输出
     * @param needStem  是否需要题干（任意卷为 true）
     * @return 题目列表；解析失败返回空列表
     */
    private List<ScannedQuestion> parseQuestions(String raw, boolean needStem) {
        List<ScannedQuestion> list = new ArrayList<>();
        if (StringUtils.isBlank(raw)) {
            return list;
        }
        try {
            JsonNode root = MAPPER.readTree(extractJson(raw));
            JsonNode arr = root.path("questions");
            if (!arr.isArray()) {
                log.warn("[EduScan] 模型输出缺少 questions 数组");
                return list;
            }
            for (JsonNode node : arr) {
                String no = node.path("no").asText("");
                String answer = node.path("answer").asText("");
                String stem = needStem ? node.path("stem").asText("") : null;
                if (StringUtils.isBlank(no) && StringUtils.isBlank(stem)) {
                    continue;
                }
                list.add(new ScannedQuestion(no, stem, answer));
            }
            log.info("[EduScan] 抽取题目 {} 道", list.size());
        } catch (Exception e) {
            log.warn("[EduScan] 题目 JSON 解析失败: {}，原文前 200 字: {}",
                    e.getMessage(), StringUtils.substring(raw, 0, 200));
        }
        return list;
    }

    /**
     * 从模型输出里截出 JSON 主体（兼容 ```json 代码块与前后说明文字）。
     *
     * @param raw 模型原始输出
     * @return JSON 文本；截不到时原样返回
     */
    private static String extractJson(String raw) {
        if (raw == null) {
            return "{}";
        }
        String trimmed = raw.trim();
        int fence = trimmed.indexOf("```");
        if (fence >= 0) {
            int start = trimmed.indexOf('\n', fence);
            int end = trimmed.lastIndexOf("```");
            if (start > 0 && end > start) {
                trimmed = trimmed.substring(start + 1, end).trim();
            }
        }
        int brace = trimmed.indexOf('{');
        int last = trimmed.lastIndexOf('}');
        if (brace >= 0 && last > brace) {
            return trimmed.substring(brace, last + 1);
        }
        return trimmed;
    }
}

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
        // 1) 优先用确定性行规则：自有卷只需要「题号 + 学生作答」，而 OCR 文本里两者本就
        //    同一行（如 "L.主次不分"），属确定性工作。实测交给模型会引入随机性：
        //    连续 5 次同一张图，有 1 次模型不返回 questions 数组，整个任务被判
        //    「未能从图片中解析出题目」而失败 —— 而 SELF 的定位是「客观题准确」，
        //    不允许这种随机失败。
        List<ScannedQuestion> byRule = parseSelfByLines(text);
        if (!byRule.isEmpty()) {
            log.info("[EduScan] SELF 行规则解析出题目 {} 道", byRule.size());
            return byRule;
        }
        // 2) 兜底：版面换行不规则（一题跨多块、题号形式未覆盖）时再交给模型抽取。
        log.info("[EduScan] SELF 行规则未识别到题号，回退模型抽取");
        String systemPrompt = "你是试卷结构解析器。只输出 JSON，禁止解释。";
        String userPrompt = """
                下面是一份试卷/答题卡的图片识别文本。逐行处理：以「题号开头」的行为一道题的起点，
                其后直到下一道题号之前的行都属于这道题的内容。

                题号形式：1. / 一、 / 第1题 / 编号(如 65-1)，原样保留。
                L./l./I. 这类字形（数字 1 被 OCR 误识）也视为题号。

                学生作答 = 该题内容里属于学生书写答案的部分；没写就留空字符串。
                不要把题号与内容合并成一个字段。

                JSON Schema:
                {"questions":[{"no":"1","answer":"学生作答"}]}

                识别文本：
                %s
                """.formatted(text);
        String raw = eduAiService.answerQuestion(systemPrompt, userPrompt);
        List<ScannedQuestion> list = parseQuestions(raw, false);
        // 兜底：模型偶尔会把「题号 + 作答」合并成一个 no（如 no="L.主次不分"、answer=""）。
        // 拆分是确定性工作，不该交给模型 —— 这里按「题号后紧跟内容」把 no 再拆一次。
        return list.stream().map(EduPaperScanner::splitMergedNo).toList();
    }

    /**
     * 自有卷的确定性行解析：以「题号开头」的行为一道题的起点，题号后紧跟的内容即学生作答；
     * 其后直到下一道题号之前的行作为该题作答的续行拼接。
     *
     * <p>页眉（姓名 / 学号等）不以题号开头，直接忽略。这样自有卷的抽取结果只取决于
     * OCR 文本本身，不受模型随机性影响。</p>
     *
     * @param text 版面全文
     * @return 解析出的题目；未识别到任何题号时返回空列表（由调用方回退到模型抽取）
     */
    private static List<ScannedQuestion> parseSelfByLines(String text) {
        List<ScannedQuestion> list = new ArrayList<>();
        String no = null;
        var answer = new StringBuilder();
        for (String rawLine : text.split("\\R")) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            var matcher = SELF_NO_PREFIX.matcher(line);
            if (SUB_ITEM_PREFIX.matcher(line).find()) {
                // 子项（1）/1）：既不新起一题（会与主题号撞号，实测把选择题判成 0 分），
                // 也不并入上一题作答（会把作答拉长、稀释相似度：实测 13 字标准答案并进子项后
                // 只剩 0.6，正确作答被判 0 分）。故与「大节标题」同样丢弃。
                log.debug("[EduScan] 跳过子项: {}", line);
                continue;
            }
            var inlineSpans = findInlineItemSpans(line);
            if (inlineSpans.size() >= 2) {
                // 一行多题（真实答题卡的选择题常见）：逐个切成独立题目，
                // 否则 Q2/Q3 会被并进 Q1 的作答（实测 15 题只出 11 行）。
                log.info("[EduScan] 一行识别到 {} 个题号，按题号切分: {}", inlineSpans.size(), line);
                for (int k = 0; k < inlineSpans.size(); k++) {
                    int contentStart = inlineSpans.get(k)[1];
                    int contentEnd = k + 1 < inlineSpans.size() ? inlineSpans.get(k + 1)[0] : line.length();
                    appendQuestion(list, no, answer);
                    no = line.substring(inlineSpans.get(k)[0], inlineSpans.get(k)[1]).trim();
                    answer = new StringBuilder(line.substring(contentStart, contentEnd).trim());
                }
                continue;
            }
            if (matcher.find()) {
                appendQuestion(list, no, answer);
                no = matcher.group().trim();
                answer = new StringBuilder(line.substring(matcher.end()).trim());
            } else if (isSectionHeader(line)) {
                // 大节标题：丢弃。绝不能并入上一题作答（实测会把 Q10 的「错」污染成「错儿、简答影」→ 0 分）
                log.debug("[EduScan] 跳过大节标题: {}", line);
            } else if (no != null && LOOSE_ITEM_PREFIX.matcher(line).find()) {
                // OCR 把题号读错（8. → B.）时这一行其实是下一题的起点；
                // 若当续行并入上一题，会把上一题的正确答案污染成 0 分、下一题整题消失。
                var loose = LOOSE_ITEM_PREFIX.matcher(line);
                loose.find();
                log.info("[EduScan] 宽松识别到疑似新题号: {}", loose.group().trim());
                appendQuestion(list, no, answer);
                no = loose.group().trim();
                answer = new StringBuilder(line.substring(loose.end()).trim());
            } else if (no != null) {
                // 当前题的作答续行
                if (answer.length() > 0) {
                    answer.append(' ');
                }
                answer.append(line);
            }
            // no == null 且未匹配到题号：页眉/说明行，忽略
        }
        appendQuestion(list, no, answer);
        return list;
    }

    /**
     * 收尾一道题：题号为空则跳过（尚未遇到第一道题）。
     *
     * @param list   题目列表
     * @param no     题号
     * @param answer 作答
     */
    private static void appendQuestion(List<ScannedQuestion> list, String no, StringBuilder answer) {
        if (no == null) {
            return;
        }
        list.add(new ScannedQuestion(no, null, answer.toString().trim()));
    }

    /**
     * 自有卷题号前缀：兼容 {@code 1.} / {@code L.} / {@code 第1题} 等形式。
     *
     * <p>分隔符必须同时收半角与全角：OCR 实测会把「1.」识成「L，」（全角逗号）、「1、」等，
     * 只认半角点会漏题（漏掉的题就是「未作答」0 分）。</p>
     *
     * <p><b>刻意不收</b>两种形态：</p>
     * <ul>
     *   <li>{@code （1）} 括号子题号：真实卷里它是「大题的子项」，与主题号 {@code 1.} 撞号；
     *       收入后归一化会得到 {@code 1}，会把子项错配到第 1 题（实测把阅读题的 (1)(2)
     *       判成了选择题 1、2）。故按「上一题的续行」处理。</li>
     *   <li>中文数字 {@code 一、二、}：真实卷里它是<b>大节标题</b>（如「一、选择题」），不是题目。
     *       由 {@link #isSectionHeader} 拦截并丢弃。</li>
     * </ul>
     */
    private static final java.util.regex.Pattern SELF_NO_PREFIX =
            java.util.regex.Pattern.compile(
                    "^\\s*[\\[【「『{]?\\s*(?:第\\s*)?[0-9lLI]{1,3}\\s*(?:[-—][0-9]{1,3})?\\s*[.。、,，．：:；;]\\s*"
                            + "|^\\s*[\\[【「『{]?\\s*第\\s*[0-9lLI]{1,3}\\s*题\\s*[.。、,，：:；;]?\\s*");

    /**
     * 子项前缀：{@code 1）} / {@code 1)} / {@code （1）} 这类「括号编号」。
     *
     * <p>真实卷里它是大题下面的小问，与主题号 {@code 1.} 撞号。归入上一题作答，
     * 绝不作为独立题号参与匹配（实测会把它配到第 1、2 题，把选择题判成 0 分）。</p>
     */
    private static final java.util.regex.Pattern SUB_ITEM_PREFIX =
            java.util.regex.Pattern.compile("^\\s*[（(]?\\s*[0-9lLI]{1,3}\\s*[)）]\\s*");

    /**
     * 宽松的「新条目」前缀：开头 1~3 个字母/数字 + 分隔符（如 {@code 8.} 被 OCR 读成 {@code B.}）。
     *
     * <p>用于兜住「题号被认错 → 下一题被并进上一题作答」：实测干净硬卷里 {@code 8.} 被读成
     * {@code B.}，于是 Q8 的作答并进了 Q7，Q7 变成 {@code 主次不分 B.做了糊涂事} 判 0 分、
     * 且 Q8 整题消失。这里把它当新条目切出来，匹配交由 matchRelation + 前缀改派处理。</p>
     */
    private static final java.util.regex.Pattern LOOSE_ITEM_PREFIX =
            java.util.regex.Pattern.compile("^\\s*[\\[【「『{]?\\s*[0-9A-Za-zlLIgGqQ]{1,3}\\s*[.。、,，．：:；;]\\s*");

    /**
     * 中文数字序号开头的大节标题形态（一、二、三、…）。
     */
    private static final java.util.regex.Pattern CN_SECTION_NO =
            java.util.regex.Pattern.compile("^\\s*[一二三四五六七八九十]{1,3}\\s*[、.．]");

    /**
     * 题型关键词：用于兜住「序号被 OCR 认错」的大节标题（实测「四、简答题」被识成「儿、简答影」）。
     */
    private static final java.util.regex.Pattern SECTION_KEYWORD =
            java.util.regex.Pattern.compile("(选择|填空|判断|简答|阅读|应用|计算|作文|听力|连线|画图|问答|口语|默写)");

    /**
     * 行内题号（不带行首锚点）：用于「一行多题」的切分。
     *
     * <p>真实答题卡的选择题常把多题排在一行：{@code 1.(A) 2.(B) 3.(C)}（OCR 常把空格吃掉，
     * 变成 {@code 1.(A)2.(B)3.(C)}）。按行解析会把 Q2/Q3 吞进 Q1 的作答 —— 实测 15 题只出 11 行。</p>
     */
    private static final java.util.regex.Pattern INLINE_ITEM =
            java.util.regex.Pattern.compile("[\\[【「『{]?\\s*(?:第\\s*)?[0-9lLI]{1,3}\\s*(?:[-—][0-9]{1,3})?\\s*[.。、,，．：:；;]\\s*");

    /**
     * 找出该行内所有题号片段（起始/结束偏移）。
     *
     * @param line 单行文本
     * @return 题号片段列表；无则为空
     */
    private static List<int[]> findInlineItemSpans(String line) {
        var spans = new ArrayList<int[]>();
        var matcher = INLINE_ITEM.matcher(line);
        int from = 0;
        while (matcher.find(from)) {
            spans.add(new int[]{matcher.start(), matcher.end()});
            from = Math.max(matcher.end(), matcher.start() + 1);
        }
        return spans;
    }

    /**
     * 是否为大节标题（一、选择题 / 二、填空题 / 四、简答题 …）。
     *
     * <p>大节标题既不是题目，也<b>不是上一题的作答</b>：若当续行并入，会把上一题的正确答案
     * 污染成 0 分（实测 Q10「错」被并成「错儿、简答影」）。</p>
     *
     * @param line 单行文本
     * @return 是否大节标题
     */
    private static boolean isSectionHeader(String line) {
        if (line == null || line.length() > 16) {
            return false;
        }
        if (CN_SECTION_NO.matcher(line).find()) {
            return true;
        }
        return line.length() <= 10 && SECTION_KEYWORD.matcher(line).find();
    }

    /**
     * 拆分被模型合并的「题号 + 作答」。
     *
     * <p>实测：同一张图同一次识别，模型有时返回 {@code no="L." answer="主次不分"}（正确），
     * 有时返回 {@code no="L.主次不分" answer=""}（合并）—— 这是模型的随机性，
     * 一旦合并就会把正确答案判成「未作答」。拆分本可用确定性规则完成，故在此兜底。</p>
     *
     * @param q 模型抽出的题目
     * @return 拆分后的题目；无需拆分时原样返回
     */
    private static ScannedQuestion splitMergedNo(ScannedQuestion q) {
        if (q == null || StringUtils.isNotBlank(q.answer())) {
            return q;
        }
        String no = q.no() == null ? "" : q.no().trim();
        if (no.isEmpty()) {
            return q;
        }
        // 题号前缀：数字/字母 1（OCR 常见误识 l/L/I）+ 分隔符（. 、 ) 等）
        var m = NO_PREFIX.matcher(no);
        if (m.find() && m.end() < no.length()) {
            String head = no.substring(0, m.end()).trim();
            String tail = no.substring(m.end()).trim();
            if (!tail.isEmpty()) {
                log.info("[EduScan] 拆分合并的题号与作答: {} -> no={} answer={}", no, head, tail);
                return new ScannedQuestion(head, q.stem(), tail);
            }
        }
        return q;
    }

    /**
     * 题号前缀：可选「第」+ 数字或字形近似的 1(l/L/I) + 可选分隔符（半角与全角都要收）。
     */
    private static final java.util.regex.Pattern NO_PREFIX =
            java.util.regex.Pattern.compile("^\\s*(?:第\\s*)?[0-9]+[a-zA-Z]{0,3}\\s*[.。、,，．：:；;）)]\\s*|^\\s*[lLI1]\\s*[.。、,，．：:；;）)]\\s*");

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

                抽取规则（务必遵守）：
                1. answer 只取「学生实际写出的作答」；选择题若学生在括号内写了选项字母，answer 取该字母。
                2. 若文本里看不到明确的作答标记（选项字母、答题横线上的内容等），answer 必须留空字符串，
                   禁止用某个选项的文字充当作答 —— 把选项当成作答会让判分对象错位（实测把正确选项
                   「鲸鱼」当成学生作答，学生实际选的错项 A 反而被判满分）。
                3. 选项文字（A. …/B. …）属于题干内容，不得写进 answer。

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

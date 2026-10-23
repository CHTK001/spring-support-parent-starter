package com.chua.starter.eduai.ai;

import com.chua.common.support.utils.StringUtils;
import com.chua.deeplearning.support.ocr.OcrResult;
import com.chua.deeplearning.support.paper.PaperClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 试卷扫描器 —— 阅卷的「版面还原 + 文字提取 + 结构化」环节。
 *
 * <p>复用 ai-starter 的全局 Bean，不自行实现推理：</p>
 * <ul>
 * <li>{@link PaperClient} —— 试卷识别契约（utils-xx 接口，实现由 ai-starter 装配）：
 *     文字块识别、版面分组框、结构化结果</li>
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
     * 试卷识别客户端（utils-xx 接口）：实现类由 ai-starter 装配为 Bean，
     * 本模块在编译期只认接口，不依赖任何具体实现类。
     */
    private final ObjectProvider<PaperClient> paperProvider;

    /**
     * AI 服务（用于文本结构化与判分）
     */
    private final EduAiService eduAiService;

    /**
     * 任意卷识别策略（SPI）：按 {@code plugin.eduai.marking.extract-strategy} 选择，默认 whole。
     *
     * <p>识别逻辑不再写死在本类里，而是由策略实现；本类只负责提供文字块/版面/模型回调。</p>
     */
    private final List<com.chua.starter.eduai.ai.strategy.AnyExtractStrategy> anyExtractStrategies;

    /**
     * 教育 AI 配置（读取识别策略 key）
     */
    private final com.chua.starter.eduai.configuration.EduAiProperties eduAiProperties;

    /**
     * 阅卷运行时设置（识别策略）。
     *
     * <p>可选项：读到系统配置弹窗保存的运行时覆盖值；bean 缺失时完全退回
     * {@code plugin.eduai.marking.extract-strategy} 的 YAML 默认值。</p>
     */
    @Autowired(required = false)
    private com.chua.starter.eduai.service.MarkingSettingService markingSettingService;

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
    public record ScanResult(java.util.Map<String, Integer> layoutRegions, List<ScanBlock> blocks,
                             List<LayoutBox> layoutBoxes, int pageWidth, int pageHeight, String text) {

        /**
         * 规范化构造器：集合组件做防御性拷贝。
         *
         * @param layoutRegions 版面区域统计
         * @param blocks        文字块
         * @param layoutBoxes   版面区域坐标（语义标签 + 像素框）
         * @param pageWidth      页面像素宽（读不到时为 0）
         * @param pageHeight     页面像素高（读不到时为 0）
         * @param text           全文
         */
        public ScanResult {
            layoutRegions = layoutRegions == null ? java.util.Map.of() : java.util.Map.copyOf(layoutRegions);
            blocks = blocks == null ? List.of() : List.copyOf(blocks);
            layoutBoxes = layoutBoxes == null ? List.of() : List.copyOf(layoutBoxes);
        }
    }

    /**
     * 一个版面区域（语义标签 + 像素坐标）。
     *
     * <p>用于「与图片同版式」的结构化展示：把 layout 模型检出的标题/正文/编号等区域
     * 按原图位置叠放，而不是只保留数量。</p>
     *
     * @param label  语义标签（如 title / text / figure）
     * @param x      左上角 x
     * @param y      左上角 y
     * @param width  宽
     * @param height 高
     */
    public record LayoutBox(String label, float x, float y, float width, float height) {
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
     * @param score  卷面标注的本题分值（可空；自有卷以数据库为准，任意卷无标注时用表单兜底）
     */
    public record ScannedQuestion(String no, String stem, String answer, Integer score) {

        /**
         * 兼容构造：无分值时 score 置空。
         *
         * @param no     题号
         * @param stem   题干
         * @param answer 学生作答
         */
        public ScannedQuestion(String no, String stem, String answer) {
            this(no, stem, answer, null);
        }
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
        PaperClient paper = paperProvider.getIfAvailable();
        if (paper == null || !paper.available()) {
            throw new IllegalStateException("试卷识别管线不可用：请检查 sys_ai_global_setting 的 ocr/paper 分组与模型资产是否就位");
        }

        var layoutRegions = new java.util.LinkedHashMap<String, Integer>();
        var layoutBoxes = new ArrayList<LayoutBox>();
        try {
            var regions = paper.layoutRegions(imageData);
            if (regions != null && !regions.isEmpty()) {
                regions.forEach((label, list) -> {
                    layoutRegions.put(label, list == null ? 0 : list.size());
                    if (list != null) {
                        for (var rect : list) {
                            if (rect != null) {
                                layoutBoxes.add(new LayoutBox(label, rect.x(), rect.y(),
                                        rect.width(), rect.height()));
                            }
                        }
                    }
                });
                log.info("[EduScan] 版面区域: {}（保留坐标 {} 个）", layoutRegions, layoutBoxes.size());
            } else {
                log.info("[EduScan] 版面无可用区域，按整图 OCR 识别");
            }
        } catch (Exception e) {
            // 版面是增强能力：失败不阻断，OCR 文本链路仍可阅卷
            log.warn("[EduScan] 版面分析失败，降级为纯 OCR: {}", e.getMessage());
        }

        int[] size = imageSize(imageData);

        List<OcrResult> results = paper.recognizeDetail(imageData);
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
        return new ScanResult(layoutRegions, blocks, layoutBoxes, size[0], size[1], text.toString());
    }

    /**
     * 读取图片像素尺寸（宽、高）。
     *
     * <p>版面区域与文字块都是像素坐标，前端要按原图等比叠放就必须知道页尺寸；
     * 读不到时返回 0，前端退化为按容器自适应。只读文件头，不做整图解码。</p>
     *
     * @param imageData 图片字节
     * @return [宽, 高]；读取失败返回 [0, 0]
     */
    private static int[] imageSize(byte[] imageData) {
        try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(imageData))) {
            var readers = ImageIO.getImageReaders(stream);
            if (readers.hasNext()) {
                var reader = readers.next();
                try {
                    reader.setInput(stream);
                    return new int[]{reader.getWidth(0), reader.getHeight(0)};
                } finally {
                    reader.dispose();
                }
            }
        } catch (Exception ignored) {
            // 尺寸读不到不阻断识别
        }
        return new int[]{0, 0};
    }

    /**
     * 版面优先抽取：按 OCR 文字块坐标归属「题号块 → 后续块」，不调 LLM。
     *
     * <p>规则：文字块按 (y, x) 排序；以题号开头的块为一道题的起点，其后直到下一题号块
     * 之前的所有块拼作该题内容（首块题号后的行内文字为作答开头，续块整体追加）。
     * 大节标题（{@link #isSectionHeader}）丢弃；子项（1)/(1)）同样丢弃（与行规则一致，
     * 避免与主题号撞号）。</p>
     *
     * <p>版面块顺序错乱或一道题都切不出时返回空列表，由调用方回退行规则/模型抽取。</p>
     *
     * @param blocks OCR 文字块（含坐标）
     * @return 抽出的题目；切不出时为空
     */
    public List<ScannedQuestion> extractByLayout(List<ScanBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return List.of();
        }
        var ordered = new ArrayList<>(blocks);
        ordered.sort((a, b) -> {
            int dy = Float.compare(a.y(), b.y());
            // 同一行（y 差小于块高一半）按 x 排，否则按 y 排。
            // 修复：结构回放给 splitLegacyAggregated 的 blocks 是归一化坐标，
            // 不能再用像素坐标的 +4px 容差（会把整页块误判成同一行，仅切出 2/3/1）。
            boolean normalized = a.width() <= 1f && b.width() <= 1f && a.height() <= 1f && b.height() <= 1f;
            float tolerance = normalized
                    ? Math.max(a.height(), b.height()) * 0.5f + 0.005f
                    : Math.max(a.height(), b.height()) * 0.5f + 4f;
            if (Math.abs(a.y() - b.y()) <= tolerance) {
                return Float.compare(a.x(), b.x());
            }
            return dy;
        });
        List<ScannedQuestion> list = new ArrayList<>();
        String no = null;
        StringBuilder answer = new StringBuilder();
        int lastNum = -1;
        for (ScanBlock block : ordered) {
            String line = block.text() == null ? "" : block.text().trim();
            if (line.isEmpty()) {
                continue;
            }
            if (SUB_ITEM_PREFIX.matcher(line).find() || isSectionHeader(line)) {
                continue;
            }
            var inlineSpans = findInlineItemSpans(line);
            if (inlineSpans.size() >= 2 && fitsProgression(
                    parseNoNum(line.substring(inlineSpans.get(0)[0], inlineSpans.get(0)[1])), lastNum)) {
                // 行内多题只认「递进成立」的题号边界：作答里的算式数字（"80," "9," "1/8。"）
                // 同样带「数字+分隔符」，逐个校验可避免整行作答被拆成幽灵题判 0；
                // 被拒的边界不算新题，其内容自然并回上一题的作答区间
                var bounds = new ArrayList<int[]>();
                bounds.add(inlineSpans.get(0));
                int prevNum = parseNoNum(line.substring(inlineSpans.get(0)[0], inlineSpans.get(0)[1]));
                for (int k = 1; k < inlineSpans.size(); k++) {
                    int n = parseNoNum(line.substring(inlineSpans.get(k)[0], inlineSpans.get(k)[1]));
                    if (fitsProgression(n, prevNum)) {
                        bounds.add(inlineSpans.get(k));
                        prevNum = n;
                    }
                }
                for (int k = 0; k < bounds.size(); k++) {
                    int contentStart = bounds.get(k)[1];
                    int contentEnd = k + 1 < bounds.size() ? bounds.get(k + 1)[0] : line.length();
                    appendQuestion(list, no, answer);
                    no = line.substring(bounds.get(k)[0], bounds.get(k)[1]).trim();
                    lastNum = parseNoNum(no);
                    answer = new StringBuilder(line.substring(contentStart, contentEnd).trim());
                }
                continue;
            }
            var matcher = SELF_NO_PREFIX.matcher(line);
            if (matcher.find()
                    && fitsProgression(parseNoNum(matcher.group()), lastNum)) {
                // 题号必须递进（n==1 新大节重来 / n==上题+1），否则当续行：
                // 实测 "1/8。" 被 OCR 切块后 "8。" 匹配题号格式、"80," "9," 同理，
                // 误认会把上一题的作答拆成幽灵题（判断(×) 与计算结果整段被判 0）
                appendQuestion(list, no, answer);
                no = matcher.group().trim();
                lastNum = parseNoNum(no);
                answer = new StringBuilder(line.substring(matcher.end()).trim());
            } else if (isBareItemBlock(block, lastNum)) {
                // OCR 把题号标点漏掉时（实测 "2" 单独成块），按左侧页边的裸数字补切。
                appendQuestion(list, no, answer);
                no = line.trim();
                lastNum = parseNoNum(no);
                answer = new StringBuilder();
            } else if (no != null) {
                if (answer.length() > 0) {
                    answer.append(' ');
                }
                answer.append(line);
            }
        }
        appendQuestion(list, no, answer);
        if (!list.isEmpty()) {
            log.info("[EduScan] 版面块归属解析出题目 {} 道", list.size());
        }
        return list;
    }

    /**
     * 解析题号里的数字（{@code 第1题} / {@code 12.} / {@code L.} 都要收）。
     *
     * @param no 题号原文
     * @return 数字值；解析不出返回 -1
     */
    private static int parseNoNum(String no) {
        if (no == null || no.isEmpty()) {
            return -1;
        }
        var m = java.util.regex.Pattern.compile("[0-9lLI]{1,4}").matcher(no);
        if (!m.find()) {
            return -1;
        }
        String digits = m.group().replace('l', '1').replace('L', '1').replace('I', '1');
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * 题号递进校验：新题号必须是「1（新大节）」或「上一题 + 1」。
     *
     * <p>解析不出数字（字母题号等）一律放行 —— 宁可保守，不吞真题。</p>
     *
     * @param n       新题号数字（-1 表示解析不出）
     * @param lastNum 上一题号数字（-1 表示尚无）
     * @return 是否作为新题起点
     */
    private static boolean fitsProgression(int n, int lastNum) {
        if (n < 0 || lastNum < 0) {
            return true;
        }
        return n == 1 || n == lastNum + 1;
    }

    /**
     * 左侧页边、单独成块的裸题号判断。
     *
     * @param block   OCR 文字块（含归一化坐标）
     * @param lastNum 上一题号数字
     * @return 是否应作为新题起点
     */
    private static boolean isBareItemBlock(EduPaperScanner.ScanBlock block, int lastNum) {
        if (block == null) {
            return false;
        }
        String line = block.text() == null ? "" : block.text().trim();
        if (!BARE_ITEM_NO.matcher(line).matches()) {
            return false;
        }
        // 归一化坐标（结构回放/页面展示）直接按页边比例判断；像素坐标（实时扫描）
        // 按「左侧页边 + 小宽高」兜底，避免把解答里居中/靠右的纯数字判定为题号。
        boolean normalized = block.width() <= 1f && block.height() <= 1f;
        boolean leftMargin = normalized ? block.x() < 0.18f : block.x() < block.width() * 6f;
        boolean smallBlock = normalized
                ? block.width() < 0.12f && block.height() < 0.08f
                : block.width() < 200f && block.height() < 120f;
        return leftMargin && smallBlock && fitsProgression(parseNoNum(line), lastNum);
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
        return extractSelfQuestions(null, text);
    }

    /**
     * 自有卷抽取（版面优先）。
     *
     * <p>顺序：版面块归属 → 确定性行规则 → 模型兜底。前两步都不调 LLM，
     * 模型只在版面与行规则都抽不出时才用；模型也失败时回退空列表（由调用方决定）。</p>
     *
     * @param blocks 版面文字块（含坐标）；为空时跳过版面归属
     * @param text   版面全文
     * @return 抽出的题目列表
     */
    public List<ScannedQuestion> extractSelfQuestions(List<ScanBlock> blocks, String text) {
        if (blocks != null && !blocks.isEmpty()) {
            List<ScannedQuestion> byLayout = extractByLayout(blocks);
            if (!byLayout.isEmpty()) {
                return byLayout;
            }
            log.info("[EduScan] 版面块归属未识别到题号，回退行规则");
        }
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
        // 模型不可用/限流时 answerQuestion 返回的是错误「字符串」而非 JSON，
        // 直接喂给 JSON 解析只会得到「解析失败」，把真因（如 429 限流）埋掉。
        requireModelOutput(raw);
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
        list.add(new ScannedQuestion(no, null, cleanScannedAnswer(answer.toString())));
    }

    /**
     * 行内大节标题污染：空白 + 中文序号（一、二、…）+ 分隔符。
     */
    private static final java.util.regex.Pattern CN_SECTION_AT =
            java.util.regex.Pattern.compile("\\s+[一二三四五六七八九十]{1,3}\\s*[、.．]");

    /**
     * 作答文本降噪：OCR 可能把「大节标题」与作答并进同一文字块，行首过滤拦不住时在此剪除。
     *
     * <p>只剪「空白 + 中文序号 + 含『题』『分』分值要素 + 直到行尾」的尾段 ——
     * 覆盖实测的「…（×） 二、判断。对的打√，错的打×。（每题5分）」类污染；
     * 正常作答里的中文序号不在行尾、也不带分值要素，不受影响。</p>
     *
     * @param answer 原始作答
     * @return 降噪后的作答
     */
    private static String stripAnswerNoise(String answer) {
        if (answer == null || answer.isEmpty()) {
            return answer;
        }
        int cut = -1;
        var m = CN_SECTION_AT.matcher(answer);
        while (m.find()) {
            String rest = answer.substring(m.end());
            if (rest.contains("题") && rest.contains("分")) {
                cut = m.start();
            }
        }
        if (cut > 0) {
            log.info("[EduScan] 作答尾部大节标题污染已剪除: {}", StringUtils.substring(answer, cut, cut + 40));
            return answer.substring(0, cut).trim();
        }
        return answer;
    }

    /**
     * 页眉/页脚水印：{@code 第1页-阅卷验收专用} / {@code 第N页/共N页} / {@code 阅卷验收专用}。
     *
     * <p>打印版答卷会印「阅卷验收专用」与页码水印，OCR 把它们并进题号后的作答区，
     * 混入 studentAnswer 后既污染判分（相似度被水印稀释），也让前端作答区显示错误内容
     * （实测第 1 题「作答 C 判错」的根因就是整段题干+水印被写进 studentAnswer）。</p>
     */
    private static final java.util.regex.Pattern WATERMARK =
            java.util.regex.Pattern.compile(
                    "(?i)(?:阅卷验收专用|扫描归档专用|第\\s*(?:\\d+|\\{page\\}|\\{t\\}|N)\\s*页\\s*[-—]\\s*阅卷验收专用"
                            + "|第\\s*(?:\\d+|\\{page\\}|\\{t\\}|N)\\s*页\\s*[-—/]\\s*(?:第\\s*)?(?:共\\s*)?(?:\\d+|\\{page\\}|\\{t\\}|N)\\s*页"
                            + "|第\\s*(?:\\d+|\\{page\\}|\\{t\\}|N)\\s*页|(?:\\d+|\\{page\\}|\\{t\\}|N)\\s*/\\s*(?:第\\s*)?(?:共\\s*)?(?:\\d+|\\{page\\}|\\{t\\}|N)\\s*页|共\\s*(?:\\d+|\\{page\\}|\\{t\\}|N)\\s*页)");

    /**
     * 分值标注：{@code （每题5分）} / {@code (5分)} / {@code 每题X分} 等。
     *
     * <p>行规则把「题号 + 题干 + 分值标注 + 作答」整段并入 answer 时，先剥掉分值标注，
     * 避免「每题5分」这类字样稀释相似度（实测混入后正确作答被判 0 分）。</p>
     */
    private static final java.util.regex.Pattern SCORE_ANNOTATION =
            java.util.regex.Pattern.compile(
                    "[（(]\\s*[共每]?\\s*题?\\s*\\d{1,3}\\s*分\\s*[)）]"
                            + "|\\s*[（(]\\s*每题\\s*\\d{1,3}\\s*分\\s*[)）]"
                            + "|(?:共|每|本|满|总)题?\\s*\\d{1,3}\\s*分\\s*");

    /**
     * 清理「学生作答」区域：把扫描混入的题号前缀、分值标注、页眉水印、行尾大节标题
     * 从作答里剥离，让判分与前端展示都只面对学生真正书写的内容。
     *
     * <p><b>只剪确定性污染，绝不裁剪学生实质作答</b>：42/43 简答、翻译题的学生书写
     * 必须原样保留（该题的答案是学生文字，不是字母/符号），这里只去掉「不是学生写的
     * 那部分」。顺序：行尾大节标题 → 分值标注 → 页眉水印 → 行首题号前缀。</p>
     *
     * @param answer 扫描识别出的原始作答文本
     * @return 剥离污染后的作答；为空或无法剥离时原样返回
     */
    public static String cleanScannedAnswer(String answer) {
        if (answer == null || answer.isEmpty()) {
            return answer;
        }
        String text = stripAnswerNoise(answer);
        text = SCORE_ANNOTATION.matcher(text).replaceAll(" ");
        text = WATERMARK.matcher(text).replaceAll(" ");
        // 行首题号前缀：1. / L. / 第1题（保留答案本身；只去掉前缀）
        text = text.replaceFirst("^\\s*[\\[【「『{]?\\s*(?:第\\s*)?[0-9lLI]{1,3}\\s*(?:[-—][0-9]{1,3})?\\s*[.。、,，．：:；;]\\s*", "");
        // 折叠多个空白/标点之间的空格，保持可读性
        text = text.replaceAll("\\s{2,}", " ").trim();
        if (text.isEmpty()) {
            return answer.trim();
        }
        return text;
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
     * 独立成块的裸题号：{@code 2} 这类 OCR 把标点漏掉、只剩一个数字的题号块。
     *
     * <p>只在版面路径使用且要求左侧页边（x 归一化小于 0.18），避免把学生答卷里
     * 居中/右边作为答案的数字（如计算题结果）误切成新题。</p>
     */
    private static final java.util.regex.Pattern BARE_ITEM_NO =
            java.util.regex.Pattern.compile("^\\s*[0-9lLI]{1,3}\\s*$");

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
        if (line == null) {
            return false;
        }
        if (CN_SECTION_NO.matcher(line).find()) {
            // 「一、…」开头：真实大节标题常带「（每题X分）」分值后缀，整体可超 16 字 ——
            // 实测「二、判断。对的打√，错的打×。（每题5分）」21 字漏过 16 字闸，
            // 被当续行并进上一题作答、把全对作答污染成 0 分。短行按标题收；
            // 长行必须同时含「题」「分」两个分值要素才收，避免误伤以中文序号开头的长作答。
            return line.length() <= 16 || (line.contains("题") && line.contains("分"));
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

    public List<ScannedQuestion> extractAnyQuestions(String text) {
        return extractAnyQuestions(null, text);
    }

    /** 任意卷抽取结果：题目列表 + 卷面识别到的学生（姓名/学号，可为空）。 */
    public record AnyScan(List<ScannedQuestion> questions, String studentName, String studentNo) {
        /**
         * 规范化构造：空串统一为 null，集合防御性拷贝。
         *
         * @param questions   抽出的题目
         * @param studentName 卷面姓名（识别不到为 null）
         * @param studentNo   卷面学号（识别不到为 null）
         */
        public AnyScan {
            questions = questions == null ? List.of() : List.copyOf(questions);
            studentName = StringUtils.isBlank(studentName) ? null : studentName.trim();
            studentNo = StringUtils.isBlank(studentNo) ? null : studentNo.trim();
        }
    }

    /**
     * 任意卷抽取（带卷面学生识别）。
     *
     * <p>与 {@link #extractAnyQuestions(List, String)} 同一套切题链路，
     * 额外识别卷头的「姓名 / 学号」栏：模型抽取路径由大模型顺带给出，
     * 版面/行规则路径用正则从卷头文字抽。识别结果供匿名任务回填学生标识。</p>
     *
     * @param blocks 版面文字块（含坐标）；为空时跳过版面归属
     * @param text   版面全文
     * @return 题目 + 卷面学生
     */
    public AnyScan extractAnyScan(List<ScanBlock> blocks, String text) {
        return extractAnyScan(blocks, null, text);
    }

    /**
     * 任意卷抽取（带版面区域，供 region 策略按区域分块识别）。
     *
     * <p>策略由系统配置 {@code plugin.eduai.marking.extract-strategy} 选择（默认 {@code whole}），
     * 通过 {@link com.chua.starter.eduai.ai.strategy.AnyExtractStrategy} SPI 扩展；
     * 配置值找不到对应实现时回落 {@code whole}，保证行为可预期。</p>
     *
     * <p>取值优先「系统配置弹窗」的运行时覆盖（{@code edu_app_config} 的
     * {@code marking-extract-strategy} 键），其次 YAML 默认；改完无需重启。</p>
     *
     * @param blocks      版面文字块（含坐标）；为空时跳过版面归属
     * @param layoutBoxes 版面区域（含坐标）；region 策略用
     * @param text        版面全文
     * @return 题目 + 卷面学生
     */
    public AnyScan extractAnyScan(List<ScanBlock> blocks, List<LayoutBox> layoutBoxes, String text) {
        String key = eduAiProperties != null && eduAiProperties.getMarking() != null
                ? eduAiProperties.getMarking().getExtractStrategy() : null;
        if (markingSettingService != null) {
            key = markingSettingService.resolved().getExtractStrategy();
        }
        com.chua.starter.eduai.ai.strategy.AnyExtractStrategy strategy = resolveStrategy(key);
        log.info("[EduScan] 任意卷识别策略: {}", strategy.key());
        return strategy.extract(new ExtractContext(blocks, layoutBoxes, text));
    }

    /**
     * 按 key 取策略；缺失/未知名回落 whole（默认），再不行取任意一个。
     *
     * @param key 配置的策略 key
     * @return 策略实现
     */
    private com.chua.starter.eduai.ai.strategy.AnyExtractStrategy resolveStrategy(String key) {
        String want = org.springframework.util.StringUtils.hasText(key) ? key.trim() : "whole";
        if (anyExtractStrategies != null) {
            for (com.chua.starter.eduai.ai.strategy.AnyExtractStrategy s : anyExtractStrategies) {
                if (want.equalsIgnoreCase(s.key())) {
                    return s;
                }
            }
            for (com.chua.starter.eduai.ai.strategy.AnyExtractStrategy s : anyExtractStrategies) {
                if ("whole".equalsIgnoreCase(s.key())) {
                    log.warn("[EduScan] 未找到识别策略 '{}'，回落 whole", want);
                    return s;
                }
            }
            if (!anyExtractStrategies.isEmpty()) {
                return anyExtractStrategies.get(0);
            }
        }
        throw new IllegalStateException("没有任何 AnyExtractStrategy 实现可用");
    }

    /**
     * 识别上下文实现：把本类已有的能力以回调形式交给策略，策略不依赖本类，避免循环依赖。
     */
    private final class ExtractContext implements com.chua.starter.eduai.ai.strategy.AnyExtractStrategy.Context {

        private final List<ScanBlock> blocks;
        private final List<LayoutBox> layoutBoxes;
        private final String text;

        private ExtractContext(List<ScanBlock> blocks, List<LayoutBox> layoutBoxes, String text) {
            this.blocks = blocks;
            this.layoutBoxes = layoutBoxes;
            this.text = text;
        }

        @Override
        public List<ScanBlock> blocks() {
            return blocks;
        }

        @Override
        public String text() {
            return text;
        }

        @Override
        public List<LayoutBox> layoutBoxes() {
            return layoutBoxes;
        }

        @Override
        public List<ScannedQuestion> layoutQuestions() {
            return extractByLayout(blocks);
        }

        @Override
        public List<ScannedQuestion> fillStems(List<ScannedQuestion> base) {
            return fillStemsByModel(base, text);
        }

        @Override
        public AnyScan modelExtract() {
            return extractAnyScanByModel(text);
        }

        @Override
        public List<ScannedQuestion> ruleQuestions() {
            return parseSelfByLines(text).stream()
                    .map(q -> new ScannedQuestion(q.no(), "", q.answer(), q.score())).toList();
        }

        @Override
        public String[] studentByLines() {
            return extractStudentByLines(text);
        }

        @Override
        public AnyScan modelExtract(String regionText) {
            return extractAnyScanByModel(regionText);
        }
    }

    /**
     * 卷头学生信息正则抽取：「姓名＿＿张三」「学号:20260001」这类栏位。
     *
     * <p>模型路径失败的兜底，也是自有卷（SELF）链路的学生识别通道——
     * 自有卷抽题不调模型（题号回数据库取准确分值），学生信息只能正则抽。
     * OCR 可能把下划线识别成一串下划线字符或冒号全角，分隔符从宽匹配。
     * 返回 [姓名, 学号]，识别不到为 null。</p>
     *
     * @param text 版面全文
     * @return [studentName, studentNo]
     */
    public String[] extractStudentByLines(String text) {
        String name = null;
        String no = null;
        var head = text.length() > 600 ? text.substring(0, 600) : text;
        // 卷头栏位标签词：姓名栏为空白时，正则容易把下一栏的「班级」「学号」等栏位名捕获成姓名。
        // 捕获结果若命中这些栏位词（或属于「栏位名 + 紧跟内容」形态）即视为未识别。
        var nameM = java.util.regex.Pattern
                .compile("姓\\s*名\\s*[:：_\\-—]*\\s*([\\u4e00-\\u9fa5]{2,4})").matcher(head);
        if (nameM.find() && !isHeaderLabelWord(nameM.group(1))) {
            name = nameM.group(1);
        }
        var noM = java.util.regex.Pattern
                .compile("学\\s*号\\s*[:：_\\-—]*\\s*([0-9A-Za-z]{3,20})").matcher(head);
        if (noM.find()) {
            no = noM.group(1);
        }
        return new String[]{name, no};
    }

    /**
     * 判断捕获的姓名是否实为卷头栏位标签词（班级 / 学号 / 姓名 / 性别 / 学校 / 年级等）。
     *
     * <p>姓名栏空白、下划线未被 OCR 识别时，正则捕获组会抓到下一栏的栏位名
     * （实测卷头「姓名：＿＿ 班级：＿＿ 学号：＿＿」识别出 name="班级"）。
     * 命中标签词则视为未识别，避免把栏位名当学生姓名展示。</p>
     *
     * @param raw 正则捕获的候选姓名
     * @return true 表示是栏位标签词（应判未识别）
     */
    private static boolean isHeaderLabelWord(String raw) {
        if (raw == null) {
            return true;
        }
        String s = raw.trim();
        return s.isEmpty()
                || s.equals("姓名") || s.equals("班级") || s.equals("学号")
                || s.equals("性别") || s.equals("学校") || s.equals("年级")
                || s.equals("考试") || s.equals("科目") || s.equals("考场")
                || s.equals("座位") || s.equals("准考证") || s.equals("学校名");
    }

    public List<ScannedQuestion> extractAnyQuestions(List<ScanBlock> blocks, String text) {
        return extractAnyScan(blocks, text).questions();
    }

    /**
     * 任意卷：让模型同时抽出「题干 + 学生作答 + 分值」与卷面学生（姓名/学号）。
     *
     * <p>题库里没有这份卷子，题干与作答都只能来自识别文本，因此对模型的要求更高。
     * 模型失败时抛异常，由调用方回退行规则（不再整卷挂掉）。</p>
     *
     * @param text 版面全文
     * @return 抽出的题目列表 + 卷面学生
     */
    private AnyScan extractAnyScanByModel(String text) {
        String systemPrompt = "你是试卷结构解析器。只输出 JSON，禁止解释。";
        String userPrompt = """
                下面是一份外来试卷/答题卡的图片识别文本。请抽取每一道题，给出序号、题干与学生作答；
                同时从卷头（姓名/班级/学号栏）识别出考生姓名与学号。
                识别可能有错字，按上下文合理还原；无法确定的字段留空字符串。

                抽取规则（务必遵守）：
                1. answer 只取「学生实际写出的作答」；选择题若学生在括号内写了选项字母，answer 取该字母。
                2. 若文本里看不到明确的作答标记（选项字母、答题横线上的内容等），answer 必须留空字符串，
                   禁止用某个选项的文字充当作答 —— 把选项当成作答会让判分对象错位（实测把正确选项
                   「鲸鱼」当成学生作答，学生实际选的错项 A 反而被判满分）。
                3. 选项文字（A. …/B. …/C. …/D. …）属于题干：stem 必须完整保留题目正文与【全部选项】，
                   不得只给问句就丢开 A/B/C/D —— 只给问句会让判分环节看不到选项、无法核对作答，
                   实测因此把本应正确的作答判成 0 分。answer 只取学生所选的选项字母，不得把选项文字写进 answer。
                4. score 取卷面标注的本题分值（如「每题3分」「共25分」就近归属）；无明确标注时取 null，
                   禁止编造分值。
                5. studentName/studentNo 只取卷头「姓名＿＿＿」「学号＿＿＿」栏里手写或印刷的内容；
                   姓名为 2~4 个汉字，学号为字母数字串；栏位为空或识别不到时留空字符串，
                   禁止把标题/考场信息当成姓名学号。

                JSON Schema:
                {"studentName":"张三","studentNo":"20260001","questions":[{"no":"1","stem":"题干","answer":"学生作答","score":3}]}

                识别文本：
                %s
                """.formatted(text);
        String raw = eduAiService.answerQuestion(systemPrompt, userPrompt);
        // 模型不可用/限流时 answerQuestion 返回的是错误「字符串」而非 JSON，
        // 直接喂给 JSON 解析只会得到「解析失败」，把真因（如 429 限流）埋掉。
        requireModelOutput(raw);
        JsonNode root;
        try {
            root = MAPPER.readTree(extractJson(raw));
        } catch (Exception e) {
            // JSON 解析失败转非受检异常，由 extractAnyScan 统一回退行规则
            throw new IllegalStateException("模型输出 JSON 解析失败: " + e.getMessage(), e);
        }
        List<ScannedQuestion> list = parseQuestions(root, true);
        if (list.isEmpty()) {
            throw new IllegalStateException("模型未抽出题目");
        }
        return new AnyScan(list, root.path("studentName").asText(""), root.path("studentNo").asText(""));
    }

    /**
     * 从「作答文本」里取学生的实质作答（选择题字母 / 判断题符号）。
     *
     * <p>版面块归属路径把题号之后的整段文字（题干 + 选项 + 写在括号里的答案）都当成作答，
     * 直接交给判分模型时，模型看到的是「学生作答 = 整段题干」，只能回「作答照抄题干」并给 0 分
     * （实测 5 题里 3 题如此）。试卷的作答惯例是写进括号「（C）」或打「√/×」，
     * 这里按惯例确定性地取出来，避免把识别格式问题算成学生答错。</p>
     *
     * @param text 作答文本（可能夹带题干与选项）
     * @return 实质作答；取不到时原样返回
     */
    public static String extractInlineMark(String text) {
        if (StringUtils.isBlank(text)) {
            return text;
        }
        // 0) 明确的「作答/答案」前缀字母优先：作答C / 答案C / 答案：C / 答：C / 选C
        //    （OCR 常把整段题干+选项+学生作答合并在一个块里，只有这一句才真正属于学生）
        var explicit = java.util.regex.Pattern.compile(
                "(?:作答|答案|答|选|选择|填)\\s*[:：]?\\s*([A-Da-dＡ-Ｄａ-ｄ])").matcher(text);
        if (explicit.find()) {
            return toHalfWidthUpper(explicit.group(1));
        }
        // 1) 括号里的选项字母：（C）/(c)/（ Ｄ ）
        var paren = java.util.regex.Pattern.compile("[（(]\\s*([A-Da-dＡ-Ｄａ-ｄ])\\s*[）)]").matcher(text);
        if (paren.find()) {
            return toHalfWidthUpper(paren.group(1));
        }
        // 2) 「选C」「答案 C」「选 C」这类写法；这里仅兜底 0/1 没抓到的情况
        var pick = java.util.regex.Pattern.compile("(?:选|答|答案)\\s*[:：]?\\s*([A-Da-dＡ-Ｄａ-ｄ])").matcher(text);
        if (pick.find()) {
            return toHalfWidthUpper(pick.group(1));
        }
        // 3) 判断题符号（题干里通常不会自带 √/×）
        if (text.indexOf('√') >= 0 || text.indexOf('✓') >= 0) {
            return "对";
        }
        if (text.indexOf('×') >= 0 || text.indexOf('✗') >= 0) {
            return "错";
        }
        return text;
    }

    /**
     * 从扫描作答里剥离题库题干前缀。
     *
     * <p>SELF 卷的规则切题把「题号后到下一题前」整段当作作答，简答/填空的题干与
     * 学生作答常因此并在一起（实测 42/43 只截到「翻译句子：…」，题干的下一行
     * 才是学生翻译，但行规则把题干也收进了 answer）。判分阶段题库题干已确定，
     * 数据库里这道题的 {@code questionStem} 可当作「不是学生作答」的部分剥离，
     * 只留下确实属于学生的作答；剥离后为空则按未作答处理，不再把题干冒充作答。</p>
     *
     * @param answer 清洗/提取后的作答文本
     * @param stem   题库题干（可空）
     * @return 剥离题干后的作答；无法剥离时原样返回
     */
    public static String stripLeadingStem(String answer, String stem) {
        if (StringUtils.isBlank(answer) || StringUtils.isBlank(stem)) {
            return answer;
        }
        String answerNorm = answer.replaceAll("\\s+", "");
        String stemNorm = stem.replaceAll("\\s+", "");
        if (!answerNorm.startsWith(stemNorm)) {
            return answer;
        }
        // 原字符串带空格/换行，不能直接按长度切；在原文上跳过空白逐字符消费题干长度。
        int pos = 0;
        int stemPos = 0;
        while (stemPos < stemNorm.length() && pos < answer.length()) {
            if (Character.isWhitespace(answer.charAt(pos))) {
                pos++;
                continue;
            }
            pos++;
            stemPos++;
        }
        return answer.substring(Math.min(pos, answer.length())).trim();
    }

    /**
     * 填空题作答剥离题干碎片：只留学生在「______」空位填的内容。
     *
     * <p>规则切题把「题号后到下一题前」整段当作作答，填空的题干与作答常并在一起
     * （实测 17 题作答变成「，志在千里。（曹操《龟虽寿》） 老骥伏杨」）。若把整段
     * 交给相似度，题干碎片会稀释分母：标准「老骥伏枥」4 字对整段 13 字只有 0.35，
     * 本来只是 OCR 把「枥」认成「杨」（错 1 字）也应得分的题被判错了。</p>
     *
     * <p>做法：以题干中的 {@code ______} / {@code （）} 空位为界，删除空位左侧与
     * 右侧的题干片段；剩余的连续文字即学生填写内容。空位两侧片段在作答中可能被
     * OCR 拆开/加空格，做去空白匹配。</p>
     *
     * @param answer 扫描作答（可能含题干碎片）
     * @param stem   题库题干（含下划线空位，如 {@code ______，志在千里。（曹操《龟虽寿》）}）
     * @return 仅剩学生填空内容的作答；无法拆出空位或没有题干碎片时原样返回
     */
    public static String stripFillStem(String answer, String stem) {
        if (StringUtils.isBlank(answer) || StringUtils.isBlank(stem)) {
            return answer;
        }
        String marker = "______";
        int markerAt = stem.indexOf(marker);
        if (markerAt < 0) {
            markerAt = stem.indexOf("（）");
        }
        if (markerAt < 0) {
            markerAt = stem.indexOf("()");
        }
        if (markerAt < 0) {
            return stripLeadingStem(answer, stem);
        }
        String beforeMarker = stem.substring(0, markerAt);
        String afterMarker = stem.substring(markerAt + marker.length());
        String result = answer;
        // 学生作答通常位于空位右侧题干之后；先把右侧题干去掉，剩「学生作答 + 可能的尾注」
        if (StringUtils.isNotBlank(afterMarker) && containsIgnoringSpace(result, afterMarker)) {
            result = removeIgnoringSpace(result, afterMarker);
        }
        // 再删空位左侧题干（16 题这类题干前半在题号块，作答块只有后半时可能命不中）
        if (StringUtils.isNotBlank(beforeMarker) && containsIgnoringSpace(result, beforeMarker)) {
            result = removeIgnoringSpace(result, beforeMarker);
        }
        // 去掉 OCR 常见的尾注「（王绩《野望》）」「（2分）」等，保留学生书写内容
        result = result.replaceAll("\\s*[（(][^（）()]{0,30}[）)]\\s*", " ").trim();
        // 剥离后为空（或只剰标点/虚词）说明学生没有在空位填写内容，按「未作答」处理，
        // 不能把题干冒充作答返回（实测 16 题：学生空着，OCR 把整行题干当成作答）。
        String cleaned = result.replaceAll("[，。、；：？！,.;:?!\\s]+", "").trim();
        if (cleaned.isEmpty()) {
            return "";
        }
        return result.trim();
    }

    private static boolean containsIgnoringSpace(String text, String fragment) {
        if (StringUtils.isBlank(fragment)) {
            return false;
        }
        String t = text == null ? "" : text.replaceAll("\\s+", "");
        String f = fragment.replaceAll("\\s+", "");
        return !f.isEmpty() && t.contains(f);
    }

    private static String removeIgnoringSpace(String text, String fragment) {
        if (StringUtils.isBlank(text) || StringUtils.isBlank(fragment)) {
            return text;
        }
        String t = text.replaceAll("\\s+", "");
        String f = fragment.replaceAll("\\s+", "");
        if (!t.contains(f)) {
            return text;
        }
        // 原文可能带空格，逐字符在原字符串上跳过空白消费要删除的片段长度。
        int start = indexIgnoringSpace(text, f);
        int pos = start;
        int fragPos = 0;
        while (fragPos < f.length() && pos < text.length()) {
            if (Character.isWhitespace(text.charAt(pos))) {
                pos++;
                continue;
            }
            pos++;
            fragPos++;
        }
        return (text.substring(0, start) + text.substring(Math.min(pos, text.length()))).trim();
    }

    private static int indexIgnoringSpace(String text, String fragment) {
        String t = text.replaceAll("\\s+", "");
        String f = fragment.replaceAll("\\s+", "");
        int normalizedIndex = t.indexOf(f);
        if (normalizedIndex < 0) {
            return -1;
        }
        // 把归一化下标映射回原字符串（跳过原文中的空白）。
        int originalPos = 0;
        int count = 0;
        while (originalPos < text.length() && count < normalizedIndex) {
            if (!Character.isWhitespace(text.charAt(originalPos))) {
                count++;
            }
            originalPos++;
        }
        return originalPos;
    }

    /**
     * 全角字母转半角大写
     *
     * @param ch 单字符
     * @return 半角大写字母
     */
    private static String toHalfWidthUpper(String ch) {
        char c = ch.charAt(0);
        if (c >= 'Ａ' && c <= 'Ｄ') {
            c = (char) ('A' + (c - 'Ａ'));
        } else if (c >= 'ａ' && c <= 'ｄ') {
            c = (char) ('A' + (c - 'ａ'));
        }
        return String.valueOf(c).toUpperCase(java.util.Locale.ROOT);
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
        String mark = extractInlineMark(question.answer());
        String systemPrompt = "你是资深阅卷老师。只输出 JSON，禁止解释。";
        String userPrompt = """
                外来试卷没有标准答案，请依据学科常识判断作答是否正确，并给出简短评语（不超过 50 字）。

                判定要求：
                1. 作答可能写成「答：X」，或夹带题面文字/栏目字样（如「（每题5分）」），只取学生的实质作答判定；
                2. 比较类填空（在 ○ / ☐ 里填 >、< 或 =）：先分别算出两边数值，再核对所填符号；
                3. 判断题（对/错、√/×）：先核算题干陈述本身是否正确，再与学生所写比对；
                4. 选择题：题干里带 A./B./C./D. 选项时，先依据题干与选项自行判断出正确选项，
                   再与学生所填字母比对后给分。选项齐全却回「无法判断」而不给结论是不可接受的 ——
                   只有题干与选项都确实辨认不出时才在评语里说明，并给 0 分；
                5. 无法辨认或明显残缺的作答按错误处理，并在评语里注明原因。

                题干: %s
                学生作答: %s
                满分: %d

                输出 JSON Schema:
                {"score": 3, "comment": "评语"}
                注意：评分为 0~%d 之间的整数，无论对错都必须给出具体评语，只返回 JSON。
                """.formatted(question.stem() == null ? "" : question.stem(),
                mark == null ? "" : mark, maxScore, maxScore);
        try {
            String raw = eduAiService.answerQuestion(systemPrompt, userPrompt);
        // 模型不可用/限流时 answerQuestion 返回的是错误「字符串」而非 JSON，
        // 直接喂给 JSON 解析只会得到「解析失败」，把真因（如 429 限流）埋掉。
        requireModelOutput(raw);
            JsonNode node = MAPPER.readTree(extractJson(raw));
            int score = node.path("score").asInt(0);
            score = Math.max(0, Math.min(score, maxScore));
            var outcome = new EduAiService.MarkingOutcome();
            outcome.setSource("AI");
            outcome.setScore(score);
            String comment = node.path("comment").asText("");
            if (comment.isBlank()) {
                // 模型偶尔返回无 comment 的 JSON，落库空串会让结果表出现空白评语
                comment = score <= 0 ? "回答错误" : score >= maxScore ? "回答正确" : "部分得分";
            }
            outcome.setComment(comment);
            return outcome;
        } catch (Exception e) {
            log.warn("[EduScan] 任意卷判分失败，回退 0 分: {}", e.toString());
            fallback.setScore(0);
            // 用户可见评语不得直出内部异常原文（可能带限流/服务/堆栈细节），
            // 真实原因只进服务端日志；界面给出可行动的稳定文案。
            fallback.setComment("AI 判分暂不可用，请人工复核");
            return fallback;
        }
    }

    /**
     * 版面题号+作答已定，只让模型补题干（不重新切题）。
     *
     * <p>模型失败/返回空时返回空列表，由调用方保留版面结果。</p>
     *
     * @param byLayout 版面抽出的题号+作答
     * @param text     版面全文
     * @return 补上题干的题目；失败时为空
     */
    private List<ScannedQuestion> fillStemsByModel(List<ScannedQuestion> byLayout, String text) {
        try {
            // 题号在「一、二、三」各节间会重复（1./2./…每节重来），绝不能按题号回填 ——
            // 重复键在建映射时互相覆盖：实测 ds-v4.1 补齐题干后，B/C 卷各节题干被贴到
            // 最后一节同号题上，判分全部变成「作答与题干无关」为 0。这里改为
            // 「按顺序列出 → 按顺序数组返回 → 按下标一一对应」，数量不符即整体放弃（保留空题干）。
            StringBuilder ordered = new StringBuilder();
            for (int i = 0; i < byLayout.size(); i++) {
                ScannedQuestion q = byLayout.get(i);
                ordered.append(i + 1).append(". [题号 ").append(q.no() == null ? "" : q.no().trim())
                        .append("] ").append(q.answer() == null ? "" : q.answer().trim()).append('\n');
            }
            String systemPrompt = "你是试卷结构解析器。只输出 JSON，禁止解释。";
            String userPrompt = """
                    下面是一份外来试卷的图片识别文本。已按出现顺序切好 %d 道题：
                    %s
                    请依据识别文本，为每道题给出题干（识别有错字按上下文还原；找不到留空字符串），
                    不要合并拆分、不要编造分值。
                    题干必须完整：选择题要把该题的选项文字（A. …/B. …/C. …/D. …）一并写进题干 ——
                    只给问句（如「下列…的一项是」）会让判分环节看不到选项、无从核对作答，
                    实测因此把正确作答判成 0 分。

                    必须严格按上面题目的先后顺序，返回同样数量的题干数组（第 i 个元素对应第 i 道题）：
                    JSON Schema:
                    {"stems":["第1道题的题干","第2道题的题干"]}

                    识别文本：
                    %s
                    """.formatted(byLayout.size(), ordered, text);
            String raw = eduAiService.answerQuestion(systemPrompt, userPrompt);
            requireModelOutput(raw);
            JsonNode root = MAPPER.readTree(extractJson(raw));
            JsonNode arr = root.path("stems");
            if (!arr.isArray() || arr.size() != byLayout.size()) {
                log.warn("[EduScan] 补题干数量不符（期望 {} 实际 {}），放弃补题干保留空题干",
                        byLayout.size(), arr.isArray() ? arr.size() : -1);
                return List.of();
            }
            List<ScannedQuestion> out = new ArrayList<>();
            for (int i = 0; i < byLayout.size(); i++) {
                ScannedQuestion q = byLayout.get(i);
                String stem = arr.get(i).asText("").trim();
                out.add(new ScannedQuestion(q.no(), stem, q.answer(), q.score()));
            }
            return out;
        } catch (Exception e) {
            log.warn("[EduScan] 模型补题干失败: {}", e.getMessage());
            return List.of();
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
            return parseQuestions(MAPPER.readTree(extractJson(raw)), needStem);
        } catch (Exception e) {
            log.warn("[EduScan] 题目 JSON 解析失败: {}，原文前 200 字: {}",
                    e.getMessage(), StringUtils.substring(raw, 0, 200));
            return list;
        }
    }

    /**
     * 解析模型输出的题目 JSON（已解析的根节点）。
     *
     * <p>与 String 版分离：任意卷抽取需要在同一个根节点上同时取
     * questions 与顶层 studentName/studentNo，不重复解析。</p>
     *
     * @param root     模型输出 JSON 根节点
     * @param needStem 是否需要题干（任意卷为 true）
     * @return 题目列表；结构不符返回空列表
     */
    private List<ScannedQuestion> parseQuestions(JsonNode root, boolean needStem) {
        List<ScannedQuestion> list = new ArrayList<>();
        if (root == null) {
            return list;
        }
        JsonNode arr = root.path("questions");
        if (!arr.isArray()) {
            log.warn("[EduScan] 模型输出缺少 questions 数组");
            return list;
        }
        for (JsonNode node : arr) {
            String no = node.path("no").asText("");
            String answer = node.path("answer").asText("");
            String stem = needStem ? node.path("stem").asText("") : null;
            Integer score = null;
            JsonNode scoreNode = node.path("score");
            if (scoreNode.isInt() && scoreNode.asInt() > 0) {
                score = scoreNode.asInt();
            }
            if (StringUtils.isBlank(no) && StringUtils.isBlank(stem)) {
                continue;
            }
            list.add(new ScannedQuestion(no, stem, stripAnswerNoise(answer), score));
        }
        log.info("[EduScan] 抽取题目 {} 道", list.size());
        return list;
    }

    /**
     * 校验模型输出不是「失败提示串」。
     *
     * <p>{@link com.chua.starter.eduai.ai.EduAiService#answerQuestion} 在模型不可用或调用失败时
     * 返回 {@code "AI 模块未启用…"} / {@code "AI 响应失败: …"} 这类<b>字符串</b>而不是抛异常。
     * 调用方若直接拿去解析 JSON，只会得到「解析失败」，把真正原因（如 429 限流）埋掉 ——
     * 实测 ANY 抽取整卷失败，任务错误只写「未能从图片中解析出题目」，真因是限流。
     * 这里显式识别并抛出，让错误信息直接可读。</p>
     *
     * @param raw 模型输出
     * @throws IllegalStateException 输出为失败提示串或为空时
     */
    private static void requireModelOutput(String raw) {
        if (raw == null) {
            throw new IllegalStateException("模型无输出");
        }
        String text = raw.trim();
        if (text.startsWith("AI 响应失败") || text.startsWith("AI 模块未启用")) {
            throw new IllegalStateException("模型调用失败：" + StringUtils.substring(text, 0, 120));
        }
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

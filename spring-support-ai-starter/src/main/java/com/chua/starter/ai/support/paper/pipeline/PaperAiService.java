// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.paper.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.chat.AiChatGateway;
import com.chua.starter.ai.support.paper.pipeline.config.PaperPipelineProperties;
import com.chua.starter.ai.support.paper.pipeline.setting.MarkingSettingService;
import com.chua.starter.ai.support.paper.pipeline.setting.MarkingSettingVO;
import com.chua.starter.ai.support.paper.pipeline.util.AnswerSimilarity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.springframework.web.client.RestTemplate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.chua.common.support.utils.CollectionUtils;

/**
 * 教育 AI 业务服务 —— 只负责「教育业务要问模型什么」。
 *
 * <p>提示词、返回结构、降级策略属于教育业务：</p>
 * <ul>
 *   <li>{@link #generatePaperOutline} — AI 组卷大纲（知识点+难度+题量 → 题目ID列表）</li>
 *   <li>{@link #generateQuestionDrafts} — AI 出题（按条件原创题目草稿，教师审核后入库）</li>
 *   <li>{@link #markQuestion} — AI 阅卷（题目+学生答案 → 得分+评语）</li>
 *   <li>{@link #answerQuestion} — 通用问答（知识点/错题推荐等）</li>
 * </ul>
 *
 * <p>模型调用本身（客户端解析、供应商配置、限流判定、重试退避、连通性探测）由
 * ai-starter 的 {@code AiChatGateway} 提供，本模块不再持有任何 AI 传输实现。</p>
 *
 * <p>所有方法都做了「AI 不可用时回退」的兜底：客户端不可用或调用失败时，
 * 业务侧仍可继续运行（标记 降级 标志）。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
public class PaperAiService {

    /**
     * AI 对话网关（AI 模块提供的统一模型调用出口）。
     *
     * <p>客户端解析、供应商配置、限流判定、重试退避都在 ai-starter 内完成；
     * 本服务只负责「教育业务要问模型什么」。</p>
     */
    @Autowired
    private AiChatGateway aiChatGateway;

    /**
     * 属性
     */
    @Autowired(required = false)
    private PaperPipelineProperties properties = new PaperPipelineProperties();

    /**
     * 阅卷运行时设置（重试次数 / 退避曲线）。
     *
     * <p>可选项：读到系统配置弹窗保存的运行时覆盖值；bean 缺失（如精简上下文）时
     * 完全退回 {@link PaperPipelineProperties} 的 YAML 默认值，不影响原行为。</p>
     */
    @Autowired(required = false)
    private MarkingSettingService markingSettingService;

    /**
     * HTTP 客户端（用于发送告警 Webhook）。
     *
     * <p>Spring Boot 自动配置，bean 不存在时跳过告警（静默降级）。</p>
     */
    @Autowired(required = false)
    private RestTemplate restTemplate;
    /**
     * Objectmapper
    */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 是否启用 AI（总开关 + 对话客户端 是否可用）。
     * @return 是否可用的结果
     */
    public boolean isAvailable() {
        if (!properties.getAi().isEnabled()) {
            return false;
        }
        return aiChatGateway != null && aiChatGateway.available();
    }

    /**
     * AI 组卷：根据知识点/难度/题量生成题目 标识 列表。
     *
     * <p>Prompt 让模型返回形如 {@code {"questionIds":[1,2,3]}} 的 JSON；解析失败回退到传入的
     * {@code fallbackIds}。</p>
     *
     * @param subject     学科
     * @param gradeLevel  学段
     * @param grade       年级
     * @param types       题型列表，如 ["单个_CHOICE","JUDGE"]
     * @param knowledgeIds 关联知识点标识列表
     * @param count       期望生成多少题
     * @param fallbackIds AI 不可用或失败时使用的回退题目标识
     * @return 题目ID列表（来自 AI 或回退）
     */
    public List<Long> generatePaperOutline(
            String subject,
            String gradeLevel,
            String grade,
            List<String> types,
            List<Long> knowledgeIds,
            int count,
            List<Long> fallbackIds
    ) {
        if (!isAvailable()) {
            log.info("[EduAi] AI 不可用，使用回退题目: count={}", fallbackIds == null ? 0 : fallbackIds.size());
            return fallbackIds == null ? new ArrayList<>() : new ArrayList<>(fallbackIds);
        }

        String systemPrompt = """
                你是资深教研专家，擅长根据学科、年级、知识点、题型、难度智能组卷。
                你必须只返回 JSON，禁止任何解释或 Markdown。
                """;
        // 候选题目必须随 Prompt 一起给模型：早期 Prompt 未给候选、示例又写死 [101,102,103]，
        // 实测模型直接照抄示例数字（101~105 既非本学科也不存在于题库），落库后成为悬空引用。
        String candidateIdList = CollectionUtils.isEmpty(fallbackIds)
                ? ""
                : fallbackIds.stream().map(String::valueOf).collect(Collectors.joining(", "));
        String exampleIds = CollectionUtils.isEmpty(fallbackIds)
                ? ""
                : fallbackIds.stream().limit(Math.min(3, fallbackIds.size()))
                        .map(String::valueOf).collect(Collectors.joining(", "));
        String userPrompt = String.format("""
                请根据下列要求，输出 JSON 格式的组卷结果。

                学科: %s
                学段: %s
                年级: %s
                /**
                * 题型。
                * @param 逗号分隔 逗号分隔
                */
                题型(逗号分隔): %s
                /**
                * 关联知识点ID。
                * @param 逗号分隔 逗号分隔
                */
                关联知识点ID(逗号分隔): %s
                题目数量: %d
                候选题目ID（只能从这份列表中选择，不得编造）: %s

                输出 JSON Schema:
                {
                  "questionIds": [%s]
                }

                注意：
                1. 只返回 JSON，不要 ``` 包裹
                2. questionIds 中的数字必须来自上面的「候选题目ID」列表，必须是整数
                3. 如果无法满足要求，questionIds 返回空数组
                """,
                subject, gradeLevel, grade,
                String.join(",", types == null ? List.of() : types),
                knowledgeIds == null ? "" : knowledgeIds.toString(),
                count,
                candidateIdList,
                exampleIds
        );

        try {
            String response = call(systemPrompt, userPrompt);
            List<Long> ids = parseLongArray(response, "questionIds");
            // 只信任候选池里的题号：模型可能编造或照抄示例。候选池之外的 ID 一律丢弃，
            // 否则会落库成悬空引用/跨学科混题，破坏试卷结构（数据完整性）。
            if (CollectionUtils.isNotEmpty(fallbackIds)) {
                Set<Long> candidateSet = new HashSet<>(fallbackIds);
                List<Long> validIds = ids.stream()
                        .filter(id -> id != null && candidateSet.contains(id))
                        .distinct()
                        .collect(Collectors.toList());
                if (validIds.size() < ids.size()) {
                    log.warn("[EduAi] AI 返回 {} 个题号，其中 {} 个不在候选池，已丢弃", ids.size(),
                            ids.size() - validIds.size());
                }
                ids = validIds;
            }
            if (ids.isEmpty()) {
                log.warn("[EduAi] AI 组卷返回空，使用回退题目");
                return fallbackIds == null ? new ArrayList<>() : new ArrayList<>(fallbackIds);
            }
            log.info("[EduAi] AI 组卷成功: ai-count={}, fallback-count={}", ids.size(),
                    fallbackIds == null ? 0 : fallbackIds.size());
            return ids;
        } catch (Exception e) {
            log.warn("[EduAi] AI 组卷异常: {}", e.getMessage());
            return fallbackIds == null ? new ArrayList<>() : new ArrayList<>(fallbackIds);
        }
    }

    /**
     * AI 出题：按学科/学段/年级/题型/难度/知识点新写题目草稿。
     *
     * <p>与 {@link #generatePaperOutline} 的「从候选池选已有题」不同，本方法让模型
     * <b>原创</b>题干、选项、答案与解析，返回的是<b>尚未入库</b>的草稿；是否采纳、
     * 如何入库由 eduai 的题库业务决定（教师审核修订后走 save 链路，与手工新增同一审核口径）。</p>
     *
     * @param subject     学科
     * @param gradeLevel  学段（中文名）
     * @param grade       年级
     * @param types       期望题型列表（可空 = 不限，由模型合理搭配）
     * @param difficulty  目标难度 1-5（null = 由模型按内容自定）
     * @param count       期望题目数（1-50，越界自动收敛）
     * @param knowledgePointNames 知识点名称列表（可空 = 不限）
     * @return 题目草稿列表（题型已归一化、答案已做合法性校验）；AI 未配置可用模型时返回空列表
     * @throws IllegalStateException 模型调用失败，或返回内容解析不出任何可用题目
     */
    public List<QuestionDraft> generateQuestionDrafts(
            String subject,
            String gradeLevel,
            String grade,
            List<String> types,
            Integer difficulty,
            int count,
            List<String> knowledgePointNames
    ) {
        if (!isAvailable()) {
            // 调用方把空结果转译为「AI 服务不可用」的业务提示；出题没有题库兜底，
            // 不能像组卷那样静默回退，必须让老师明确知道这次没生成。
            log.info("[EduAi] AI 不可用，出题请求直接返回空");
            return List.of();
        }
        int safeCount = Math.max(1, Math.min(count, 50));
        String typeHint = (CollectionUtils.isEmpty(types)) ? "不限，按学科合理搭配" : String.join("、", types);
        String kpHint = (CollectionUtils.isEmpty(knowledgePointNames))
                ? "不限" : String.join("、", knowledgePointNames);
        String diffHint = (difficulty == null || difficulty < 1 || difficulty > 5)
                ? "不限（按学科常规难度分布）" : difficulty + "（1=很易，3=中等，5=很难）";
        String systemPrompt = """
                你是资深学科教研专家，擅长按知识点与难度命制中小学试题。
                你必须只返回 JSON，禁止任何解释或 Markdown。
                """;
        String userPrompt = String.format("""
                请命制 %d 道题，要求如下。

                学科: %s
                学段: %s
                年级: %s
                题型: %s
                难度: %s
                知识点: %s

                输出 JSON Schema:
                {
                  "questions": [
                    {
                      "questionType": "SINGLE_CHOICE|MULTIPLE_CHOICE|JUDGE|FILL|SHORT_ANSWER|SUBJECTIVE|ESSAY",
                      "stem": "题干（可直接作答，不要包含答案）",
                      "options": ["选项1内容", "选项2内容"],
                      "answer": "答案",
                      "analysis": "解析：考点 + 解题思路",
                      "difficulty": 3,
                      "score": 5
                    }
                  ]
                }

                注意：
                1. 只返回 JSON，不要 ``` 包裹
                2. 选择题 options 给 4 个左右选项，answer 用字母（单选如 A；多选如 A,C）；选项文字不要带「A.」等字母前缀
                3. 判断题 answer 用「对」或「错」，options 给空数组
                4. 填空/简答/主观/作文题 answer 给参考答案文字，options 给空数组；填空题多个空用 | 分隔
                5. 题目必须与学科、学段、年级匹配，表述规范、无歧义、答案唯一可判
                """,
                safeCount, subject, gradeLevel, grade, typeHint, diffHint, kpHint
        );

        // 出题一次生成多题，调用代价高；走带重试的通道，瞬时故障/限流不直接失败
        String response = callWithRateLimitRetry(systemPrompt, userPrompt);
        String json = extractJson(response);
        if (StringUtils.isBlank(json)) {
            throw new IllegalStateException("AI 返回内容为空");
        }
        List<QuestionDraft> drafts = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode arr = root.path("questions");
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    QuestionDraft d = parseDraft(n, difficulty);
                    // 单题不合法只丢弃该题，不整体失败；教师拿到的每一题都结构可用
                    if (d != null) {
                        drafts.add(d);
                    }
                    if (drafts.size() >= safeCount) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("AI 返回内容无法解析: " + e.getMessage(), e);
        }
        if (drafts.isEmpty()) {
            throw new IllegalStateException("AI 未返回有效题目（返回内容解析不出任何可用题）");
        }
        log.info("[EduAi] AI 出题成功: subject={}, gradeLevel={}, 期望={}, 实得={}",
                subject, gradeLevel, safeCount, drafts.size());
        return drafts;
    }

    /**
     * 把模型返回的单题 JSON 归一化为草稿；无法修复的条目返回 null 丢弃。
     *
     * <p>校验口径：题干/答案非空；选择题至少 2 个选项且答案字母不越界；
     * 判断题答案归一化为「对/错」。宽松解析（别名收敛）+ 严格校验（结构不合法即弃），
     * 与组卷「只信任候选池」是同一思路：模型输出永不直接采信。</p>
     */
    private QuestionDraft parseDraft(JsonNode n, Integer defaultDifficulty) {
        String type = normalizeDraftType(n.path("questionType").asText(""));
        String stem = n.path("stem").asText("").trim();
        String answer = n.path("answer").asText("").trim();
        if (type == null || stem.isEmpty() || answer.isEmpty()) {
            return null;
        }
        QuestionDraft d = new QuestionDraft();
        d.setQuestionType(type);
        d.setStem(stem);

        List<String> options = new ArrayList<>();
        JsonNode optArr = n.path("options");
        if (optArr.isArray()) {
            for (JsonNode o : optArr) {
                String text = stripOptionPrefix(o.asText("").trim());
                if (!text.isEmpty()) {
                    options.add(text);
                }
            }
        }

        if ("SINGLE_CHOICE".equals(type) || "MULTIPLE_CHOICE".equals(type)) {
            // 选项不足或答案字母越界的草稿直接丢弃：入库后无法作答
            if (options.size() < 2) {
                return null;
            }
            List<String> letters = extractAnswerLetters(answer, options.size());
            if (letters.isEmpty()) {
                return null;
            }
            d.setOptions(options);
            d.setAnswer("SINGLE_CHOICE".equals(type) ? letters.get(0) : String.join(",", letters));
        } else if ("JUDGE".equals(type)) {
            String low = answer.toLowerCase();
            if (low.contains("对") || low.contains("正确") || low.contains("是") || low.contains("true") || low.contains("√")) {
                d.setAnswer("对");
            } else if (low.contains("错") || low.contains("否") || low.contains("false") || low.contains("×")) {
                d.setAnswer("错");
            } else {
                return null;
            }
        } else {
            // 填空/简答/主观/作文：答案去掉 HTML 标签后必须仍有内容
            String plain = answer.replaceAll("<[^>]+>", "").trim();
            if (plain.isEmpty()) {
                return null;
            }
            d.setAnswer(answer);
        }

        d.setAnalysis(n.path("analysis").asText("").trim());
        int fb = (defaultDifficulty == null || defaultDifficulty < 1 || defaultDifficulty > 5) ? 3 : defaultDifficulty;
        int diff = n.path("difficulty").asInt(fb);
        d.setDifficulty(Math.max(1, Math.min(diff, 5)));
        int score = n.path("score").asInt(5);
        d.setScore(Math.max(1, Math.min(score, 150)));
        return d;
    }

    /**
     * 从答案文本提取合法选项字母（如 "A,C" → [A, C]），越界/重复字母忽略。
     */
    private static List<String> extractAnswerLetters(String answer, int optionCount) {
        List<String> letters = new ArrayList<>();
        if (answer == null) {
            return letters;
        }
        for (char c : answer.toCharArray()) {
            char up = Character.toUpperCase(c);
            if (up >= 'A' && up < 'A' + optionCount) {
                String letter = String.valueOf(up);
                if (!letters.contains(letter)) {
                    letters.add(letter);
                }
            }
        }
        return letters;
    }

    /**
     * 题型别名收敛：模型可能返回 CHOICE / MULTI_CHOICE / TRUE_FALSE / BLANK 等变体，
     * 统一映射为系统码值；不认识的返回 null（该题丢弃）。
     */
    private static String normalizeDraftType(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        switch (t) {
            case "SINGLE_CHOICE":
            case "SINGLE":
            case "CHOICE":
            case "单选":
            case "单选题":
                return "SINGLE_CHOICE";
            case "MULTIPLE_CHOICE":
            case "MULTI_CHOICE":
            case "MULTI":
            case "多选":
            case "多选题":
                return "MULTIPLE_CHOICE";
            case "JUDGE":
            case "TRUE_FALSE":
            case "JUDGMENT":
            case "判断":
            case "判断题":
                return "JUDGE";
            case "FILL":
            case "FILL_BLANK":
            case "BLANK":
            case "填空":
            case "填空题":
                return "FILL";
            case "SHORT_ANSWER":
            case "SHORT":
            case "简答":
            case "简答题":
                return "SHORT_ANSWER";
            case "SUBJECTIVE":
            case "主观":
            case "主观题":
                return "SUBJECTIVE";
            case "ESSAY":
            case "COMPOSITION":
            case "作文":
                return "ESSAY";
            default:
                return null;
        }
    }

    /**
     * 去掉模型偶尔加在选项前的「A.」「A、」「(A)」等前缀。
     */
    private static String stripOptionPrefix(String text) {
        return text.replaceFirst("^[（(]?[A-Ha-h][）)]?[.、:：,，)）]?\\s*", "");
    }

    /**
     * AI 阅卷：评分 + 评语。
     *
     * <p>支持单题调用，Prompt 让模型返回 {@code {"score":5,"comment":"..."}} JSON。</p>
     *
     * @param questionType   题目类型
     * @param questionStem   题目题干
     * @param standardAnswer 标准答案
     * @param studentAnswer  学生作答
     * @param maxScore       满分
     * @return 阅卷结果（包含 aiscore/AI评论/源 三个字段，源 = AI/降级）
     */
    public MarkingOutcome markQuestion(
            String questionType,
            String questionStem,
            String standardAnswer,
            String studentAnswer,
            int maxScore
    ) {
        MarkingOutcome fallback = new MarkingOutcome();
        fallback.setSource("FALLBACK");
        if (StringUtils.isBlank(studentAnswer)) {
            fallback.setScore(0);
            fallback.setComment("未作答");
            return fallback;
        }
        // 兜底也走相似度而非严格相等：OCR 错字与「答对但啰嗦」不该被判 0，
        // 并与客观题容忍比对（EduMarkingServiceImpl）保持同一阈值口径。
        // 客观题先做归一化提取（√/× → 对/错、选项字母提取）：学生作答里常混着
        // 题干文字（OCR 把「（√）」连题干一起识别），相似度对符号与字母不敏感，会误判。
        String normalizedStudent = extractObjectiveAnswer(questionType, studentAnswer);
        String normalizedStandard = normalizeJudgeAnswer(standardAnswer);
        boolean matched;
        if ("JUDGE".equals(questionType) && normalizedStudent != null) {
            matched = normalizedStudent.equals(normalizedStandard);
        } else if ("SINGLE_CHOICE".equals(questionType) && normalizedStudent != null) {
            matched = normalizedStudent.equalsIgnoreCase(
                    standardAnswer == null ? "" : standardAnswer.trim());
        } else {
            matched = standardAnswer != null
                    && AnswerSimilarity.similarity(standardAnswer, studentAnswer) >= AnswerSimilarity.SIMILARITY_THRESHOLD;
        }
        if (matched) {
            fallback.setScore(maxScore);
            fallback.setComment("与标准答案一致");
        } else {
            fallback.setScore(0);
            fallback.setComment("与标准答案不一致");
        }
        fallback.setFallbackReason("相似度匹配结果");
        return fallback;
    }

    /**
     * 客观题作答归一化提取。
     *
     * <p>JUDGE：从作答里找 √/✓/×/✗/对/错/正确/错误（常与题干混在 OCR 文本里），
     * 归一化为「对」/「错」；SINGLE_CHOICE：提取选项字母（(C)、C.、选C 等）。
     * 提取不到返回 null，由调用方回落相似度比对。</p>
     *
     * @param questionType  题目类型
     * @param studentAnswer 学生作答（可能混有题干）
     * @return 归一化作答；非客观题或提取不到为 null
     */
    public static String extractObjectiveAnswer(String questionType, String studentAnswer) {
        if (studentAnswer == null) {
            return null;
        }
        if ("JUDGE".equals(questionType)) {
            if (studentAnswer.contains("√") || studentAnswer.contains("✓")
                    || studentAnswer.contains("对") || studentAnswer.contains("正确")
                    || studentAnswer.matches("(?i).*[\\(（]?\\s*T(?:RUE)?\\s*[)）]?.*")) {
                return "对";
            }
            if (studentAnswer.contains("×") || studentAnswer.contains("✗") || studentAnswer.contains("X")
                    || studentAnswer.contains("错") || studentAnswer.matches("(?i).*[\\(（]?\\s*F(?:ALSE)?\\s*[)）]?.*")) {
                return "错";
            }
            return null;
        }
        if ("SINGLE_CHOICE".equals(questionType)) {
            var explicit = java.util.regex.Pattern
                    .compile("(?:作答|答案|答|选|选择|填)\\s*[:：]?\\s*([A-Da-d])").matcher(studentAnswer);
            if (explicit.find()) {
                return explicit.group(1).toUpperCase();
            }
            var m = java.util.regex.Pattern
                    .compile("[\\(（]\\s*([A-Da-d])\\s*[)）]|选\\s*([A-Da-d])|([A-Da-d])\\s*[.。、：:]")
                    .matcher(studentAnswer);
            if (m.find()) {
                return m.group(1) != null ? m.group(1).toUpperCase()
                        : m.group(2) != null ? m.group(2).toUpperCase()
                        : m.group(3) != null ? m.group(3).toUpperCase() : null;
            }
            return null;
        }
        return null;
    }

    /**
     * 判断题标准答案归一化：对/正确/T/true/√ → 「对」；错/错误/F/false/× → 「错」。
     *
     * @param standardAnswer 标准答案（库里可能是「对」「T」「正确」等多种写法）
     * @return 归一化结果；入参为空返回空串
     */
    public static String normalizeJudgeAnswer(String standardAnswer) {
        if (standardAnswer == null) {
            return "";
        }
        String s = standardAnswer.trim();
        if (s.matches("(?i)(对|正确|T|TRUE|√|Y|YES)")) {
            return "对";
        }
        if (s.matches("(?i)(错|错误|F|FALSE|×|X|N|NO)")) {
            return "错";
        }
        return s;
    }

    /**
     * AI 阅卷 — 实际调用大模型，结果不可用时回退到 {@link #markQuestion} 的相似度比对结果。
     *
     * @param questionType   题目类型
     * @param questionStem   题干
     * @param standardAnswer 标准答案
     * @param studentAnswer  学生作答
     * @param maxScore       最高得分
     * @return 阅卷结果（得分与评语）；AI 不可用时为相似度比对结果
     */
    public MarkingOutcome markQuestionWithAi(
            String questionType,
            String questionStem,
            String standardAnswer,
            String studentAnswer,
            int maxScore
    ) {
        MarkingOutcome fallback = markQuestion(questionType, questionStem, standardAnswer, studentAnswer, maxScore);
        if (!isAvailable() || isObjectiveQuestion(questionType)) {
            // 客观题直接使用相似度匹配，避免 AI 幻觉
            return fallback;
        }

        String systemPrompt = "你是资深阅卷老师，擅长根据标准答案和题目，对学生的作答进行客观、严格、公正的评分。\n你必须只返回 JSON，禁止任何解释。";
        // 阅卷 prompt 可配置：系统配置「AI 阅卷设置」维护，非空时覆盖内置默认（贴合学科/评分粒度）
        if (markingSettingService != null) {
            MarkingSettingVO setting = markingSettingService.resolved();
            if (StringUtils.hasText(setting.getMarkingPrompt())) {
                systemPrompt = setting.getMarkingPrompt();
            }
        }
        String userPrompt = String.format("""
                请根据下列信息对学生作答进行打分，并给出简短评语（不超过 50 字）。

                题型: %s
                题干: %s
                标准答案: %s
                学生作答: %s
                满分: %d

                输出 JSON Schema:
                {
                  "score": 3,
                  "comment": "回答基本正确，缺少关键步骤"
                }

                注意：
                1. 评分为 0~%d 之间的整数
                2. 严格基于标准答案给分
                3. 只返回 JSON
                """,
                questionType,
                /**
                 * safe。
                 * @param questionStem questionstem
                 */
                safe(questionStem),
                /**
                 * safe。
                 * @param standardAnswer 标准answer
                 */
                safe(standardAnswer),
                /**
                 * safe。
                 * @param studentAnswer studentanswer
                 */
                safe(studentAnswer),
                maxScore,
                maxScore
        );

        try {
            String response = callWithRateLimitRetry(systemPrompt, userPrompt);
            JsonNode node = objectMapper.readTree(extractJson(response));
            int score = node.path("score").asInt(0);
            String comment = node.path("comment").asText("");
            if (score < 0) {
                score = 0;
            }
            if (score > maxScore) {
                score = maxScore;
            }
            MarkingOutcome outcome = new MarkingOutcome();
            outcome.setSource("AI");
            outcome.setScore(score);
            outcome.setComment(comment);
            return outcome;
        } catch (Exception e) {
            log.warn("[EduAi] AI 阅卷失败，回退相似度匹配: {}", e.getMessage());
            return fallback;
        }
    }

    /**
     * 从模型输出中提取 JSON 对象文本。
     *
     * <p>模型常把结果包在 Markdown 代码围栏里（如 {@code ```json ... ```}），
     * 直接 {@code readTree} 会因首个字符是反引号而抛
     * {@code Unexpected character ('`')}，进而错误回退到字符串匹配、把正确作答判 0。
     * 这里先剥离围栏，再截取首个花括号块。</p>
     *
     * @param content 模型原始输出
     * @return 提取到的 JSON 文本；无花括号块时返回去空白后的原文
     */
    private static String extractJson(String content) {
        if (content == null) {
            return "";
        }
        var text = content.trim();
        if (text.startsWith("```")) {
            int firstLine = text.indexOf('\n');
            if (firstLine >= 0) {
                text = text.substring(firstLine + 1);
            }
            int fence = text.lastIndexOf("```");
            if (fence >= 0) {
                text = text.substring(0, fence);
            }
            text = text.trim();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    /**
     * 通用问答（错题推荐/知识点讲解等）。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @return 模型回答；不可用时返回固定文案
     */
    public String answerQuestion(String systemPrompt, String userPrompt) {
        if (!isAvailable()) {
            return "AI 服务不可用（未配置可用模型），无法回答";
        }
        try {
            String response = callWithRateLimitRetry(systemPrompt, userPrompt);
            return response;
        } catch (Exception e) {
            log.warn("[EduAi] AI 问答异常: {}", e.getMessage());
            return "AI 响应失败，请稍后重试";
        }
    }

    /**
     * 通过 AI 模块的对话网关发起一次同步调用。
     *
     * <p>客户端解析、供应商配置与提示词合并都由 ai-starter 的统一出口负责；
     * 这里只提供业务侧的超时预算。</p>
     *
     * @param systemPrompt 系统提示符
     * @param userPrompt   用户提示符
     * @return 模型输出；不可用时为空串
     */
    private String call(String systemPrompt, String userPrompt) {
        // 单次调用用「调用超时」而非「任务总预算」：后者可达 30 分钟，
        // 一次挂死的调用会把并发闸门（默认串行）堵到天荒地老，后续任务全部排队。
        long timeout = properties.getMarking().getCallTimeout() * 1000L;
        return aiChatGateway.complete(systemPrompt, userPrompt, timeout);
    }

    /**
     * 发送重试耗尽告警。
     *
     * <p>重试次数耗尽后，若配置了 {@code plugin.eduai.ai.alert.webhookUrl}，
     * 则发送 JSON 卡片到该地址。失败时只记 WARN 日志，不阻断业务流程。</p>
     *
     * @param e           导致耗尽的异常
     * @param attempts     总尝试次数（含首次）
     * @param systemHint  系统提示词摘要（可为 null）
     */
    private void fireAlertOnRetryExhausted(Exception e, int attempts, String systemHint) {
        PaperPipelineProperties.Alert alert = properties.getAlert();
        if (!alert.isEnabled() || StringUtils.isBlank(alert.getWebhookUrl())) {
            return;
        }
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("msgtype", "text");
        Map<String, Object> textContent = new LinkedHashMap<>();
        StringBuilder sb = new StringBuilder("【EduAi 告警】模型调用重试耗尽\n");
        sb.append("尝试次数: ").append(attempts).append("\n");
        sb.append("错误类型: ").append(e.getClass().getSimpleName()).append("\n");
        sb.append("错误信息: ").append(e.getMessage()).append("\n");
        if (StringUtils.isNotBlank(systemHint)) {
            sb.append("提示词摘要: ").append(systemHint, 0, Math.min(200, systemHint.length())).append("\n");
        }
        sb.append("时间: ").append(java.time.LocalDateTime.now()).append("\n");
        textContent.put("content", sb.toString());
        card.put("text", textContent);
        int timeout = alert.getTimeoutMillis() > 0 ? alert.getTimeoutMillis() : 5000;
        try {
            if (restTemplate != null) {
                restTemplate.postForEntity(alert.getWebhookUrl(), card, String.class);
                log.info("[EduAi] 告警发送成功: webhook={}, attempts={}", alert.getWebhookUrl(), attempts);
            }
        } catch (Exception ex) {
            log.warn("[EduAi] 告警发送失败: webhook={}, attempts={}, reason={}",
                    alert.getWebhookUrl(), attempts, ex.getMessage());
        }
    }

    /**
     * 模型调用失败时的重试（限流 429 + 瞬时故障）。
     *
     * <p>次数与退避曲线来自系统配置 {@code plugin.eduai.ai.retry.*}：
     * 一次失败就降级（判分掉 0 分）或整卷失败，代价都不小；
     * 但重试次数配得过大又会把单题耗时成倍放大，所以做成可配，由运维按配额决定。</p>
     *
     * <p>取值再经「系统配置弹窗」的运行时覆盖一次（{@code edu_app_config} 的
     * {@code marking-retry-*} 键，见 {@link MarkingSettingService}）：弹窗改了立刻生效，
     * 无需重启。</p>
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @return 模型响应
     */
    private String callWithRateLimitRetry(String systemPrompt, String userPrompt) {
        PaperPipelineProperties.Retry retry = properties.getAi().getRetry();
        int maxRetry = Math.max(0, retry.getTimes());
        double multiplier = retry.getBackoffMultiplier() > 1 ? retry.getBackoffMultiplier() : 2.0D;
        long cap = retry.getMaxBackoffMillis() > 0 ? retry.getMaxBackoffMillis() : 15000L;
        long backoff = Math.max(1L, retry.getBackoffMillis());
        boolean retryOnTransient = retry.isRetryOnTransient();
        if (markingSettingService != null) {
            MarkingSettingVO setting = markingSettingService.resolved();
            maxRetry = Math.max(0, setting.getRetryTimes());
            multiplier = setting.getRetryBackoffMultiplier() > 1 ? setting.getRetryBackoffMultiplier() : 2.0D;
            cap = setting.getRetryMaxBackoffMillis() > 0 ? setting.getRetryMaxBackoffMillis() : 15000L;
            backoff = Math.max(1L, setting.getRetryBackoffMillis());
            retryOnTransient = setting.isRetryOnTransient();
        }
        // 重试循环与限流/瞬时故障判定属于 AI 模块能力，业务只提供预算与告警口径。
        AiChatGateway.AiChatOptions options = new AiChatGateway.AiChatOptions(
                properties.getMarking().getCallTimeout() * 1000L,
                maxRetry, backoff, multiplier, cap, retryOnTransient);
        try {
            return aiChatGateway.completeWithRetry(systemPrompt, userPrompt, options);
        } catch (RuntimeException e) {
            // 耗尽后发送告警（不阻断抛出）
            fireAlertOnRetryExhausted(e, maxRetry + 1, systemPrompt);
            throw e;
        }
    }

    /**
     * 解析 JSON 中数字数组。
     * @param json json
     * @param field 字段
     * @return 解析longarray的结果
     */
    private List<Long> parseLongArray(String json, String field) {
        List<Long> result = new ArrayList<>();
        if (StringUtils.isBlank(json)) {
            return result;
        }
        try {
            String trimmed = json.trim();
            // 去除可能的 Markdown 代码块包装
            if (trimmed.startsWith("```")) {
                int firstNewline = trimmed.indexOf('\n');
                int lastFence = trimmed.lastIndexOf("```");
                if (firstNewline > 0 && lastFence > firstNewline) {
                    trimmed = trimmed.substring(firstNewline + 1, lastFence).trim();
                }
            }
            JsonNode node = objectMapper.readTree(trimmed);
            JsonNode arr = node.path(field);
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    if (n.canConvertToLong()) {
                        result.add(n.asLong());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[EduAi] 解析 JSON 失败: content={}, err={}", json, e.getMessage());
        }
        return result;
    }

    /**
     * 客观题判定：单选/多选/判断/填空走字符串匹配，不调 AI。
     * @param type 类型
     * @return 是否对象question的结果
     */
    private boolean isObjectiveQuestion(String type) {
        if (type == null) {
            return false;
        }
        String t = type.toUpperCase();
        return t.equals("SINGLE_CHOICE")
                || t.equals("MULTIPLE_CHOICE")
                || t.equals("JUDGE")
                || t.equals("FILL")
                || t.equals("BLANK");
    }

    /**
     * Safe
     *
     * @param s s
     * @return safe的结果
     */
    private String safe(String s) {
        return s == null ? "" : s;
    }

    /**
     * 阅卷结果。
     * @author CH
     * @since 4.0.0
     */
    @lombok.Data
    public static class MarkingOutcome {
        /**
         * 得分
         */
        private int score;
        /**
         * 评语
         */
        private String comment;
        /**
         * 来源：AI / 降级
         */
        private String source;
        /**
         * 回退原因（仅 源=降级 时存在）
         */
        private String fallbackReason;
        /**
         * 本次判分是否未获得可信结果。
         *
         * <p>{@code true} 表示模型不可用 / 限流 / 多次调用均未给出判定依据 ——
         * 此时 {@link #score} 不可采信，调用方（eduai 判分管线）应把该题写成
         * 「AI 判分失败」的显式失败态，等待重判或老师手动给分，
         * 而不是把兜底 0 分当成真实成绩落库（历史事故：失败题被写成 0 分 +
         * 「AI 已批改」，学生白丢分、老师还以为判过了）。</p>
         *
         * <p>「未作答」「客观题相似度比对」这类确定性兜底<b>不置位</b>：
         * 它们本身就是可靠的判定结果，不是失败。</p>
         */
        private boolean unavailable;
    }

    /**
     * AI 出题草稿（尚未入库）。
     *
     * <p>由 {@link #generateQuestionDrafts} 返回，字段与题库保存链路的
     * 入参对齐；教师可在前端修订后再走既有 save 链路入库（待审核态）。</p>
     *
     * @author CH
     * @since 4.0.0.51
     */
    @lombok.Data
    public static class QuestionDraft {
        /**
         * 题型（系统码值：SINGLE_CHOICE / MULTIPLE_CHOICE / JUDGE / FILL / SHORT_ANSWER / SUBJECTIVE / ESSAY）
         */
        private String questionType;
        /**
         * 题干
         */
        private String stem;
        /**
         * 选项内容列表（仅选择题非空；已去掉「A.」等字母前缀）
         */
        private List<String> options;
        /**
         * 答案（选择题为字母，单选单个、多选逗号连接；判断题为「对/错」；其余为参考答案文字）
         */
        private String answer;
        /**
         * 解析（考点 + 解题思路，可为空）
         */
        private String analysis;
        /**
         * 难度 1-5
         */
        private Integer difficulty;
        /**
         * 建议分值 1-150
         */
        private Integer score;
    }
}
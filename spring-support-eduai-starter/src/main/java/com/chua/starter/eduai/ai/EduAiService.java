package com.chua.starter.eduai.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.chat.ChatResponse;
import com.chua.starter.ai.support.registry.ChatClientRegistry;
import com.chua.starter.eduai.configuration.EduAiProperties;
import com.chua.starter.eduai.model.vo.EduAiCapabilityVO;
import com.chua.starter.ai.support.configuration.chat.AiChatProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 教育 AI 业务使用的统一 AI 调用服务。
 *
 * <p>封装 ChatClient 调用，规范化 Prompt 输入/JSON 输出，并提供：
 * <ul>
 *   <li>{@link #generatePaperOutline} — AI 组卷大纲（知识点+难度+题量 → 题目ID列表）</li>
 *   <li>{@link #markQuestion} — AI 阅卷（题目+学生答案 → 得分+评语）</li>
 *   <li>{@link #answerQuestion} — 通用问答（知识点/错题推荐等）</li>
 * </ul>
 *
 * <p>所有方法都做了"AI 不可用时回退"的兜底：未注册 ChatClient 或 ChatClient 调用失败时，
 * 业务侧仍可继续运行（标记 降级 标志）。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
public class EduAiService {

    /**
     * AI 对话客户端。
     *
     * <p>直接使用 spring-support-ai-starter 提供的 ChatClient bean（其 AiConfiguration
     * 会把类路径上全部 ChatClient 实现聚合为一个），业务侧不关心底层是哪个厂商、哪种形态。</p>
     */
    @Autowired(required = false)
    private ChatClient chatClient;

    /**
     * YAML 配置（{@code chua.ai.chat}）。
     *
     * <p>只为「能力探测」回显实际生效的厂商/模型、并识别「开了却没配密钥」用；
     * 没有该配置时保持为 null，不影响原有判定。</p>
     */
    @Autowired(required = false)
    private AiChatProperties aiChatProperties;
    /**
     * 对话客户端注册表；仅在显式指定 eduai.ai.provider 时用于取对应厂商的客户端
     */
    @Autowired(required = false)
    private ChatClientRegistry chatClientRegistry;
    /**
     * 属性
    */
    private final EduAiProperties properties;
    /**
     * Objectmapper
    */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构造方法，创建 EduAi服务 实例。
     *
     * @param properties 属性，不允许为 null
     */
    @Autowired
    public EduAiService(EduAiProperties properties) {
        this.properties = properties;
    }

    /**
     * 是否启用 AI（总开关 + 对话客户端 是否可用）。
     * @return 是否可用的结果
     */
    public boolean isAvailable() {
        if (!properties.getAi().isEnabled()) {
            return false;
        }
        return resolveClient() != null;
    }

    /**
     * 探测 AI 能力，给出「可用与否 + 不可用的具体原因」。
     *
     * <p>判定只看一件事：有没有可用的 {@link ChatClient} bean。该 bean 由
     * spring-support-ai-starter 提供，因此这一条同时覆盖了「没依赖该 starter」
     * 与「依赖了但一个实现都没有」两种情况。业务侧不区分底层是本地模型还是远端大模型、
     * 是哪个厂商 —— 那是 spring-support-ai-starter 的事。</p>
     *
     * <p>另外单独检查 YAML 配置（{@code chua.ai.chat}）是否「开了却没配密钥」：
     * 那种情形下 ai-starter 按条件不创建客户端，这里给出**可操作**的原因，
     * 而不是笼统的「未提供客户端」。</p>
     *
     * @return AI 能力探测结果
     */
    public EduAiCapabilityVO capability() {
        // 回显「实际生效」的厂商与模型：YAML 配置优先，回落到 eduai 自身配置。
        // 前端只用 available/reason，这两个字段是给人看的，取生效值才不会误导。
        String provider = firstNonBlank(
                aiChatProperties == null ? null : aiChatProperties.provider(),
                properties.getAi().getProvider());
        String model = firstNonBlank(
                aiChatProperties == null ? null : aiChatProperties.model(),
                properties.getAi().getModel());

        if (!properties.getAi().isEnabled()) {
            return new EduAiCapabilityVO(false, "AI 总开关已关闭（eduai.ai.enabled=false）", provider, model);
        }
        if (aiChatProperties != null && aiChatProperties.enabled()
                && !StringUtils.hasText(aiChatProperties.appKey())) {
            return new EduAiCapabilityVO(false,
                    "YAML 已开启 AI 对话客户端（chua.ai.chat.enabled=true）但未配置密钥，"
                            + "客户端未创建；请设置 chua.ai.chat.app-key 或环境变量 AI_CHAT_APP_KEY",
                    provider, model);
        }
        if (resolveClient() == null) {
            return new EduAiCapabilityVO(false,
                    "后端未提供可用的 AI 对话客户端（未引入 spring-support-ai-starter，或没有任何 ChatClient 实现）",
                    provider, model);
        }
        return new EduAiCapabilityVO(true, "AI 组卷可用", provider, model);
    }

    /**
     * 取第一个非空白字符串。
     *
     * @param first  首选值，可为 null
     * @param second 回落值，可为 null
     * @return 首个非空白值；都为空白时返回 null
     */
    private static String firstNonBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        return StringUtils.hasText(second) ? second.trim() : null;
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

                输出 JSON Schema:
                {
                  "questionIds": [101, 102, 103]
                }

                注意：
                1. 只返回 JSON，不要 ``` 包裹
                2. questionIds 中的数字是题库中的题目ID，必须是整数
                3. 如果无法满足要求，questionIds 返回空数组
                """,
                subject, gradeLevel, grade,
                String.join(",", types == null ? List.of() : types),
                knowledgeIds == null ? "" : knowledgeIds.toString(),
                count
        );

        try {
            ChatResponse response = call(systemPrompt, userPrompt);
            List<Long> ids = parseLongArray(response.content(), "questionIds");
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
        boolean exact = standardAnswer != null
                && standardAnswer.trim().equalsIgnoreCase(studentAnswer.trim());
        if (exact) {
            fallback.setScore(maxScore);
            fallback.setComment("与标准答案一致");
        } else {
            fallback.setScore(0);
            fallback.setComment("与标准答案不一致");
        }
        fallback.setFallbackReason("字符串匹配结果");
        return fallback;
    }

    /**
     * AI 阅卷 — 实际调用大模型，结果不可用时回退到 {@link #markQuestion} 的字符串比较结果。
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
            // 客观题直接使用字符串匹配，避免 AI 幻觉
            return fallback;
        }

        String systemPrompt = """
                你是资深阅卷老师，擅长根据标准答案和题目，对学生的作答进行客观、严格、公正的评分。
                你必须只返回 JSON，禁止任何解释。
                """;
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
            ChatResponse response = call(systemPrompt, userPrompt);
            JsonNode node = objectMapper.readTree(response.content());
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
            log.warn("[EduAi] AI 阅卷失败，回退字符串匹配: {}", e.getMessage());
            return fallback;
        }
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
            return "AI 模块未启用，无法回答";
        }
        try {
            ChatResponse response = call(systemPrompt, userPrompt);
            return response.content();
        } catch (Exception e) {
            log.warn("[EduAi] AI 问答异常: {}", e.getMessage());
            return "AI 响应失败: " + e.getMessage();
        }
    }

    /**
     * 解析 对话客户端 并发起一次同步调用。
     * @param systemPrompt 系统提示符
     * @param userPrompt 用户提示符
     * @return call的结果
     */
    private ChatResponse call(String systemPrompt, String userPrompt) {
        ChatClient commonClient = resolveClient();
        if (commonClient == null) {
            return new ChatResponse("");
        }

 // 对话客户端registry 提供的是 通用 对话客户端，调用 对话同步 即可
        long timeout = properties.getMarking().getTimeout() * 1000L;
        String text = timeout > 0 ? commonClient.chatSync(userPrompt, timeout) : commonClient.chatSync(userPrompt);
        /**
         * 对话响应。
         * @param text 文本
         * @return 对话响应的结果
         */
        return new ChatResponse(text);
    }

    /**
     * 解析 对话客户端。
     *
     * <p>默认直接用注入的聚合 {@link ChatClient} bean —— 业务不关心背后是哪个厂商或形态。
     * 仅当显式配置了 {@code eduai.ai.provider} 时，才优先取该 provider 对应的客户端，
     * 取不到就仍回落到聚合 bean。</p>
     *
     * @return 可用的对话客户端；都没有时返回 null
     */
    private ChatClient resolveClient() {
        String provider = properties.getAi().getProvider();
        if (chatClientRegistry != null && StringUtils.isNotBlank(provider)) {
            ChatClient byProvider = chatClientRegistry.getByProvider(provider);
            if (byProvider != null) {
                return byProvider;
            }
        }
        return chatClient;
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
    }

    /**
     * 用于返回拓展信息。
     * @return 调试信息的结果
     */
    public Map<String, Object> debugInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("enabled", properties.getAi().isEnabled());
        info.put("provider", properties.getAi().getProvider());
        info.put("model", properties.getAi().getModel());
        info.put("available", isAvailable());
        if (chatClientRegistry != null) {
            info.put("registeredProviders", chatClientRegistry.getProviders());
        } else {
            info.put("registeredProviders", java.util.Collections.emptyList());
        }
        return info;
    }
}

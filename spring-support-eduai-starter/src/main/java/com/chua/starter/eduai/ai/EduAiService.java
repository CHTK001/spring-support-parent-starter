package com.chua.starter.eduai.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.chat.ChatResponse;
import com.chua.starter.ai.support.registry.ChatClientRegistry;
import com.chua.starter.eduai.configuration.EduAiProperties;
import com.chua.starter.eduai.model.vo.EduAiCapabilityVO;
import com.chua.starter.eduai.model.vo.EduAiProbeVO;
import com.chua.starter.eduai.model.vo.MarkingSettingVO;
import com.chua.starter.eduai.service.MarkingSettingService;
import com.chua.starter.eduai.support.util.AnswerSimilarity;
import com.chua.starter.ai.support.configuration.chat.AiChatProperties;
import com.chua.starter.ai.support.configuration.client.EffectiveChatModelResolver;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.chat.MemoryChatClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.springframework.web.client.RestTemplate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
     * AI 对话客户端（兜底）。
     *
     * <p>spring-support-ai-starter 的 {@code AiConfiguration} 会把类路径上全部 ChatClient
     * 实现聚合为一个 bean。它只在 {@link #resolveClient()} 前三级都取不到客户端时使用
     * （即 AI 控制台完全没配过模型的情况）。</p>
     */
    @Autowired(required = false)
    private ChatClient chatClient;

    /**
     * YAML 配置（{@code plugin.ai.chat}）。
     *
     * <p>只为「能力探测」回显实际生效的厂商/模型、并识别「开了却没配密钥」用；
     * 没有该配置时保持为 null，不影响原有判定。</p>
     */
    @Autowired(required = false)
    private AiChatProperties aiChatProperties;
    /**
     * 生效对话模型解析器（AI 控制台 UI 配置的唯一解析入口）。
     *
     * <p>用于「能力探测」回显 UI 里真正生效的厂商/模型；缺该 bean 时回退 YAML 口径。</p>
     */
    @Autowired(required = false)
    private EffectiveChatModelResolver effectiveChatModelResolver;
    /**
     * 全局设置读取器（AI 控制台 UI 配置落库后的统一读取入口）。
     *
     * <p>用于读取 {@code runtime} 分组（LLM 主链路：provider/api_key/base_url/model），
     * 与 {@code RagClientManager} 保持同一口径。</p>
     */
    @Autowired(required = false)
    private GlobalSettingReader globalSettingReader;
    /**
     * runtime 主链路配置签名（provider|baseUrl|model|apiKey），用于配置变更后重建客户端。
     */
    private volatile String runtimeClientSignature;
    /**
     * 按 runtime 主链路配置构建的对话客户端缓存。
     */
    private volatile ChatClient runtimeClientCache;
    /**
     * 对话客户端注册表。
     *
     * <p>两处用途：① 显式指定 {@code eduai.ai.provider} 时按 provider 取客户端；
     * ② 取注册中心默认客户端 —— AI 控制台「默认模型」保存时由 {@code ChatClientHotUpdater}
     * 热更新登记的那个。</p>
     */
    @Autowired(required = false)
    private ChatClientRegistry chatClientRegistry;
    /**
     * 属性
    */
    private final EduAiProperties properties;

    /**
     * 阅卷运行时设置（重试次数 / 退避曲线）。
     *
     * <p>可选项：读到系统配置弹窗保存的运行时覆盖值；bean 缺失（如精简上下文）时
     * 完全退回 {@link EduAiProperties} 的 YAML 默认值，不影响原行为。</p>
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
     * <p>判定只看一件事：{@link #resolveClient()} 能不能解析出客户端 —— 它会依次尝试
     * {@code eduai.ai.provider}、AI 控制台 runtime 主链路、注册中心默认客户端、注入的
     * {@link ChatClient} bean。该链路同时覆盖了「没依赖 spring-support-ai-starter」与
     * 「依赖了但一个实现都没有、UI 也没配」两种情况。业务侧不区分底层是本地模型还是
     * 远端大模型、是哪个厂商 —— 那是 spring-support-ai-starter 的事。</p>
     *
     * <p>回显的厂商/模型取「实际生效」值：AI 控制台「系统设置」runtime 分组 &gt; UI 默认模型
     * &gt; 旧 YAML（{@code plugin.ai.chat}，已不再推荐）&gt; 本模块自身配置。</p>
     *
     * <p>另外仍兼容检查旧 YAML 配置（{@code plugin.ai.chat}）是否「开了却没配密钥」：
     * 那种情形下 ai-starter 按条件不创建客户端，这里给出**可操作**的原因。</p>
     *
     * @return AI 能力探测结果
     */
    public EduAiCapabilityVO capability() {
        // 回显「实际生效」的厂商与模型：以 AI 控制台 UI 配置（DB）为准 ——
        // EffectiveChatModelResolver 是「该用哪个模型对话」的全项目唯一解析入口
        // （runtime/default_model > 兼容旧键 models.defaultChatModelId）。
        // 缺 UI 解析器、或 UI 未配置时，回落到 YAML（plugin.ai.chat）与 eduai 自身配置。
        // 前端只用 available/reason，这两个字段是给人看的，取生效值才不会误导。
        String effective = null;
        if (effectiveChatModelResolver != null) {
            try {
                effective = effectiveChatModelResolver.resolve(null);
            } catch (Exception e) {
                log.debug("[EduAi] 解析 UI 默认对话模型失败: {}", e.getMessage());
            }
        }
        // runtime 主链路只有 provider + api_key 都配了才会被 resolveClient() 采用，
        // 回显必须与之同判据，否则会出现「显示 openai/gpt-4o、实际用的是别的」的误导。
        String runtimeProvider = runtimeSetting("provider");
        String runtimeModel = runtimeSetting("default_model");
        boolean runtimeUsable = StringUtils.hasText(runtimeProvider)
                && StringUtils.hasText(runtimeSetting("provider_api_key"));
        String provider = firstNonBlank(
                runtimeUsable ? runtimeProvider : null,
                headOf(effective),
                aiChatProperties == null ? null : aiChatProperties.provider(),
                properties.getAi().getProvider());
        String model = firstNonBlank(
                runtimeUsable ? runtimeModel : null,
                tailOf(effective),
                aiChatProperties == null ? null : aiChatProperties.model(),
                properties.getAi().getModel());

        if (!properties.getAi().isEnabled()) {
            return new EduAiCapabilityVO(false, "AI 总开关已关闭（eduai.ai.enabled=false）", provider, model);
        }
        if (aiChatProperties != null && aiChatProperties.enabled()
                && !StringUtils.hasText(aiChatProperties.appKey())) {
            return new EduAiCapabilityVO(false,
                    "YAML 已开启 AI 对话客户端（plugin.ai.chat.enabled=true）但未配置密钥，"
                            + "客户端未创建；请设置 plugin.ai.chat.app-key 或环境变量 AI_CHAT_APP_KEY",
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
     * 测试 AI 连通性：用当前生效的客户端真实发一条最小请求。
     *
     * <p>为什么需要它：{@link #capability()} 只判断「解析得出客户端」，不产生外部调用。
     * 密钥过期、网关不可达、模型名写错、厂商限流这几种情况能力探测都会显示「可用」，
     * 用户只有等到真正组卷/阅卷失败时才知道配置有问题，且报错发生在业务链路里、难以归因。
     * 这里把同一条解析链（{@link #resolveClient()}）跑一遍并立刻调用，让问题在配置页暴露。</p>
     *
     * <p>超时沿用阅卷的超时配置，避免探测请求长时间挂起把前端按钮卡住。</p>
     *
     * @return 连通性测试结果
     */
    public EduAiProbeVO probe() {
        if (!properties.getAi().isEnabled()) {
            return new EduAiProbeVO(false, "AI 总开关已关闭（eduai.ai.enabled=false）", null, null, 0L, null);
        }
        ChatClient client = resolveClient();
        if (client == null) {
            return new EduAiProbeVO(false,
                    "没有可用的 AI 对话客户端，请先在「AI 配置」里填写提供方、密钥与默认模型",
                    null, null, 0L, null);
        }
        EduAiCapabilityVO effective = capability();
        long started = System.currentTimeMillis();
        try {
            long timeout = Math.max(5000L, properties.getMarking().getTimeout() * 1000L);
            String reply = client.chatSync("只回复两个字：可用", timeout);
            long cost = System.currentTimeMillis() - started;
            String sample = reply == null ? "" : reply.trim();
            if (sample.length() > 120) {
                sample = sample.substring(0, 120);
            }
            return new EduAiProbeVO(true, "连通正常", effective.provider(), effective.model(), cost, sample);
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - started;
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.warn("[EduAi] AI 连通性测试失败: {}", reason);
            return new EduAiProbeVO(false, "调用失败：" + reason,
                    effective.provider(), effective.model(), cost, null);
        }
    }

    /**
     * 取第一个非空白字符串。
     *
     * @param first  首选值，可为 null
     * @param second 回落值，可为 null
     * @return 首个非空白值；都为空白时返回 null
     */
    private static String firstNonBlank(String... values) {
        if (values != null) {
            for (String value : values) {
                if (StringUtils.hasText(value)) {
                    return value.trim();
                }
            }
        }
        return null;
    }

    /**
     * 读取 AI 控制台「系统设置」{@code runtime} 分组的字段值。
     *
     * @param field 字段名，如 {@code provider} / {@code default_model}
     * @return 字段值；未注入读取器、未配置或读取异常时返回 null
     */
    private String runtimeSetting(String field) {
        if (globalSettingReader == null) {
            return null;
        }
        try {
            return globalSettingReader.getStr("runtime", field);
        } catch (Exception e) {
            log.debug("[EduAi] 读取 runtime.{} 失败: {}", field, e.getMessage());
            return null;
        }
    }

    /**
     * 取「provider:model」复合取值的 provider 前缀。
     *
     * <p>UI 的取值两种写法都可能出现：{@code agnes:agnes-3.0-flash}（provider 名前缀）
     * 与 {@code 1789547286889:agnes-3.0-flash}（数字 providerId 前缀）。</p>
     *
     * @param composite 复合取值，可为 null
     * @return 前缀；无前缀时返回 null
     */
    private static String headOf(String composite) {
        if (composite == null) {
            return null;
        }
        int idx = composite.indexOf(':');
        return idx > 0 ? composite.substring(0, idx).trim() : null;
    }

    /**
     * 取「provider:model」复合取值的模型名；无前缀时原样返回。
     *
     * @param composite 复合取值，可为 null
     * @return 模型名；空值时返回 null
     */
    private static String tailOf(String composite) {
        if (composite == null) {
            return null;
        }
        int idx = composite.indexOf(':');
        String v = idx > 0 ? composite.substring(idx + 1) : composite;
        return v.isBlank() ? null : v.trim();
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
        String candidateIdList = fallbackIds == null || fallbackIds.isEmpty()
                ? ""
                : fallbackIds.stream().map(String::valueOf).collect(Collectors.joining(", "));
        String exampleIds = fallbackIds == null || fallbackIds.isEmpty()
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
            ChatResponse response = call(systemPrompt, userPrompt);
            List<Long> ids = parseLongArray(response.content(), "questionIds");
            // 只信任候选池里的题号：模型可能编造或照抄示例。候选池之外的 ID 一律丢弃，
            // 否则会落库成悬空引用/跨学科混题，破坏试卷结构（数据完整性）。
            if (fallbackIds != null && !fallbackIds.isEmpty()) {
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
            ChatResponse response = callWithRateLimitRetry(systemPrompt, userPrompt);
            JsonNode node = objectMapper.readTree(extractJson(response.content()));
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
            return "AI 模块未启用，无法回答";
        }
        try {
            ChatResponse response = callWithRateLimitRetry(systemPrompt, userPrompt);
            return response.content();
        } catch (Exception e) {
            log.warn("[EduAi] AI 问答异常: {}", e.getMessage());
            return "AI 响应失败，请稍后重试";
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
        // ChatClient 只有单段提示词入口，必须把 system 指令并进 user 提示词：
        // 原实现直接丢弃 systemPrompt（只发 userPrompt），会让「只返回 JSON」这类约束静默失效
        String combined = StringUtils.isBlank(systemPrompt) ? userPrompt : systemPrompt + "\n\n" + userPrompt;
        String text = timeout > 0 ? commonClient.chatSync(combined, timeout) : commonClient.chatSync(combined);
        /**
         * 对话响应。
         * @param text 文本
         * @return 对话响应的结果
         */
        return new ChatResponse(text);
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
        EduAiProperties.Alert alert = properties.getAlert();
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
    private ChatResponse callWithRateLimitRetry(String systemPrompt, String userPrompt) {
        EduAiProperties.Retry retry = properties.getAi().getRetry();
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
        for (int attempt = 0; ; attempt++) {
            try {
                return call(systemPrompt, userPrompt);
            } catch (Exception e) {
                boolean retryable = isRateLimited(e)
                        || (retryOnTransient && isTransientFailure(e));
                if (!retryable || attempt >= maxRetry) {
                    // 耗尽前发送告警（异步，不阻断抛出）
                    fireAlertOnRetryExhausted(e, attempt + 1, systemPrompt);
                    if (e instanceof RuntimeException runtime) {
                        throw runtime;
                    }
                    throw new IllegalStateException(e.getMessage(), e);
                }
                log.warn("[EduAi] 模型调用失败，第 {} 次重试（退避 {}ms，原因: {}）",
                        attempt + 1, backoff, e.getMessage());
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e instanceof RuntimeException runtime ? runtime : new IllegalStateException(e);
                }
                backoff = Math.min(cap, (long) (backoff * multiplier));
            }
        }
    }

    /**
     * 判断异常链是否为「瞬时故障」：超时 / 502 / 503 / 连接重置等。
     *
     * <p>与限流区分开：鉴权失败、模型不存在、参数非法这类重试没有意义，不在其列。</p>
     *
     * @param error 异常
     * @return true 表示值得重试
     */
    private static boolean isTransientFailure(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            String name = t.getClass().getSimpleName().toLowerCase(Locale.ROOT);
            if (name.contains("timeout") || name.contains("connect")) {
                return true;
            }
            String message = t.getMessage();
            if (message == null) {
                continue;
            }
            String lower = message.toLowerCase(Locale.ROOT);
            if (lower.contains("timeout") || lower.contains("timed out")
                    || lower.contains(" 502") || lower.contains(" 503") || lower.contains(" 504")
                    || lower.contains("connection reset") || lower.contains("temporarily")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断异常链里是否出现限流（429 / rate limit）。
     *
     * <p>不同厂商的异常类型不一致（OpenAI SDK 抛 {@code RateLimitException}，
     * 其他网关可能只把 429 写在消息里），因此类名与消息都匹配。</p>
     *
     * @param error 异常
     * @return true 表示是限流
     */
    private static boolean isRateLimited(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t.getClass().getSimpleName().toLowerCase(Locale.ROOT).contains("ratelimit")) {
                return true;
            }
            String message = t.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("rate limit")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析对话客户端。
     *
     * <p>解析顺序（第一个可用者胜出）：</p>
     * <ol>
     *   <li>显式配置了 {@code eduai.ai.provider} 时，取该 provider 对应的客户端；</li>
     *   <li>AI 控制台「系统设置」的 {@code runtime} 分组（LLM 主链路：provider / api_key /
     *       base_url / model），配置齐全时直接按它构建；</li>
     *   <li>{@link ChatClientRegistry} 的默认客户端 —— AI 控制台「默认模型」保存时由
     *       {@code ChatClientHotUpdater} 按「厂商表 + Key」热更新登记；</li>
     *   <li>兜底：容器注入的 {@link ChatClient} bean（UI 未配置时的聚合客户端）。</li>
     * </ol>
     *
     * <p>由此保证「AI 控制台配置页改完即生效」，且不再依赖 {@code plugin.ai.chat} YAML。</p>
     *
     * @return 可用的对话客户端；都没有时返回 null
     */
    private ChatClient resolveClient() {
        // ① 显式指定 eduai.ai.provider 时，优先取该 provider 对应的客户端
        String provider = properties.getAi().getProvider();
        if (chatClientRegistry != null && StringUtils.isNotBlank(provider)) {
            ChatClient byProvider = chatClientRegistry.getByProvider(provider);
            if (byProvider != null) {
                return byProvider;
            }
        }
        // ② AI 控制台「系统设置」的 runtime 分组 = LLM 主链路配置。
        //    与 RagClientManager#buildRuntimeChatClient 同口径：provider/apiKey/baseUrl/model
        //    齐备时直接按它构建真实客户端 —— 不依赖厂商表，也不会被「聚合客户端」抢先。
        ChatClient runtimeClient = buildRuntimeChatClient();
        if (runtimeClient != null) {
            return runtimeClient;
        }
        // ③ 注册中心默认客户端：AI 控制台「默认模型」保存时由 ChatClientHotUpdater 热更新登记
        //    （models.defaultChatModelId -> 厂商表 + Key -> ChatClient -> setDefault）。
        if (chatClientRegistry != null) {
            ChatClient registryDefault = chatClientRegistry.getDefault();
            if (registryDefault != null && !(registryDefault instanceof MemoryChatClient)) {
                return registryDefault;
            }
        }
        // ④ 兜底：容器里注入的 ChatClient（UI 未配置时的聚合客户端）
        return chatClient;
    }

    /**
     * 按 AI 控制台「系统设置」的 runtime 分组构建真实对话客户端。
     *
     * <p>与 {@code RagClientManager#buildRuntimeChatClient} 保持同一口径：读取
     * {@code provider}/{@code provider_api_key}/{@code provider_base_url}/{@code default_model}
     * 及可选的自定义认证方案。配置不完整（缺 provider 或 api_key）时返回 {@code null}，
     * 由 {@link #resolveClient()} 继续回落。</p>
     *
     * <p>客户端按配置签名缓存：UI 改了 provider/模型/密钥任一字段，签名变化即自动重建，
     * 无需重启后端。</p>
     *
     * @return 可用的对话客户端；未配置或构建失败时返回 null
     */
    private ChatClient buildRuntimeChatClient() {
        if (globalSettingReader == null) {
            return null;
        }
        String provider;
        String apiKey;
        String baseUrl;
        String model;
        try {
            provider = globalSettingReader.getStr("runtime", "provider");
            apiKey = globalSettingReader.getStr("runtime", "provider_api_key");
            baseUrl = globalSettingReader.getStr("runtime", "provider_base_url");
            model = globalSettingReader.getStr("runtime", "default_model");
        } catch (Exception e) {
            log.debug("[EduAi] 读取 runtime 主链路配置失败: {}", e.getMessage());
            return null;
        }
        if (!StringUtils.hasText(provider) || !StringUtils.hasText(apiKey)) {
            return null;
        }
        String signature = provider + "|" + baseUrl + "|" + model + "|" + apiKey;
        if (signature.equals(runtimeClientSignature) && runtimeClientCache != null) {
            return runtimeClientCache;
        }
        try {
            ChatClientSetting setting = ChatClientSetting.builder()
                    .provider(provider.trim())
                    .appKey(apiKey.trim())
                    .baseUrl(StringUtils.hasText(baseUrl) ? baseUrl.trim() : null)
                    .model(StringUtils.hasText(model) ? model.trim() : null)
                    .authScheme(globalSettingReader.getStr("runtime", "provider_auth_scheme"))
                    .authHeader(globalSettingReader.getStr("runtime", "provider_auth_header"))
                    .authPrefix(globalSettingReader.getStr("runtime", "provider_auth_prefix"))
                    .build();
            ChatClient client = ChatClient.create(setting);
            if (client != null) {
                runtimeClientSignature = signature;
                runtimeClientCache = client;
                log.info("[EduAi] 已按 AI 控制台 runtime 配置创建对话客户端: provider={}, model={}", provider, model);
            }
            return client;
        } catch (Exception e) {
            log.warn("[EduAi] 按 runtime 配置创建对话客户端失败: {}", e.getMessage());
            return null;
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

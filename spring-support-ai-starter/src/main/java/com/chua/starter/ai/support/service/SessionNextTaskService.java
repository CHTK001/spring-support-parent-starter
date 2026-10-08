package com.chua.starter.ai.support.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.configuration.client.EffectiveChatModelResolver;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.entity.SysAiMessage;
import com.chua.starter.ai.support.entity.SysAiSession;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.mapper.SysAiMessageMapper;
import com.chua.starter.ai.support.mapper.SysAiSessionMapper;
import com.chua.starter.ai.support.model.AiChatRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 会话「AI 联想下一个任务」服务。
 *
 * <p><b>开关的生效口径 = 会话保留自己的原值</b>（用户要求「会话保留原始信息」）：</p>
 * <ol>
 *   <li>会话 {@code settings.aiNextTask.enabled} 有值 → 直接用它；</li>
 *   <li>没有 → 取「全局默认 + 本人覆盖」的 {@code session/ai_next_task_enabled}，
 *       并<b>立刻钉回会话</b>（{@code pinnedAt}），此后管理员改默认值不再影响这个会话；</li>
 *   <li>两端都没有 → false（默认关闭，绝不隐式打开）。</li>
 * </ol>
 * <p>{@code PUT} 传 {@code enabled=null} 表示<b>解除钉住</b>，回到「跟随默认值」——
 * 与设置中心其它字段「传 null = 重置」的语义一致。</p>
 *
 * <p>联想本身是一次<b>同步的一次性模型调用</b>：模型名走
 * {@link EffectiveChatModelResolver}（会话模型 → bot 默认 → 当前用户默认 → 全局默认），
 * 不写死任何模型名；模型不可用、对话为空、模型没给出可执行任务时都<b>如实返回空列表 +
 * 原因文案</b>，不伪造建议。</p>
 *
 * <p>联想结果落在会话 {@code settings.aiNextTask}（tasks / generatedAt /
 * basedOnMessageId），刷新页面读回同一份，不必重新调用模型。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionNextTaskService {

    /**
     * 会话设置里的节点名（{@code settings.aiNextTask}）
     */
    public static final String SETTINGS_KEY = "aiNextTask";

    /**
     * 配置分组（会话级配置）
     */
    private static final String GROUP = "session";

    /**
     * 「是否允许 AI 联想下一个任务」字段名
     */
    private static final String FIELD_ENABLED = "ai_next_task_enabled";

    /**
     * 「最多联想几条」字段名
     */
    private static final String FIELD_MAX = "ai_next_task_max";

    /**
     * 联想条数未配置时的默认档位
     */
    private static final int DEFAULT_MAX = 3;

    /**
     * 联想条数上限：模型偶尔会超量输出，按上限截断，避免面板被撑成很长的列表
     */
    private static final int MAX_LIMIT = 10;

    /**
     * 参与联想的最近消息条数
     *
     * <p>只取最近若干条：任务是「接下来做什么」，靠的是刚刚发生的上下文，
     * 把整段长会话全塞进去既超支也稀释重点。</p>
     */
    private static final int TRANSCRIPT_MESSAGES = 12;

    /**
     * 单条消息参与联想的最大字符数（超长内容按前缀截断）
     */
    private static final int TRANSCRIPT_CHARS_PER_MESSAGE = 800;

    /**
     * 单条任务标题的最大字符数（模型偶尔不守规矩，这里兜底）
     */
    private static final int TITLE_LIMIT = 40;

    /**
     * 单条任务说明的最大字符数
     */
    private static final int DETAIL_LIMIT = 120;

    /**
     * 会话映射
     */
    private final SysAiSessionMapper sessionMapper;

    /**
     * 消息映射
     */
    private final SysAiMessageMapper messageMapper;

    /**
     * 全局设置读取器（用户覆盖优先）
     */
    private final GlobalSettingReader globalSettingReader;

    /**
     * 生效对话模型解析器（联想用哪个模型由它决定，不写死）
     */
    private final EffectiveChatModelResolver effectiveChatModelResolver;

    /**
     * 对话门面（同步一次性调用）
     */
    private final AiChatFacadeService aiChatFacadeService;

    /**
     * JSON 工具
     */
    private final ObjectMapper objectMapper;

    /**
     * 一条「下一步任务」建议。
     *
     * @param title  任务标题（可点击执行的那句话）
     * @param detail 一句话说明
     * @param order  展示顺序，从 1 开始
     */
    public record NextTask(String title, String detail, int order) {

        /**
         * 规范构造器：标题语义必填，说明可空。
         *
         * @param title  任务标题
         * @param detail 一句话说明
         * @param order  展示顺序
         */
        public NextTask {
            title = Objects.requireNonNull(title, "title 不能为 null").trim();
            detail = detail == null ? "" : detail.trim();
        }
    }

    /**
     * 「AI 联想下一个任务」的完整状态。
     *
     * <p>{@code tasks} 是深拷贝的不可变列表（组件允许 null 元素的前提不成立：
     * 解析阶段已过滤空标题），避免调用方改到内部引用。</p>
     *
     * @param enabled          该会话是否开启联想
     * @param pinned           该值是否已钉在会话上（false = 仍跟随默认值）
     * @param defaultValue     当前默认值（用于前端展示「跟随默认：开/关」）
     * @param tasks            已联想出的任务列表
     * @param generatedAt      联想生成时间（毫秒；未生成过为 null）
     * @param basedOnMessageId 生成时依据的最后一条消息 id（未生成过为 null）
     * @param message          不可用时的原因文案（正常时为 null）
     */
    public record NextTaskState(boolean enabled, boolean pinned, boolean defaultValue,
                                List<NextTask> tasks, Long generatedAt, Long basedOnMessageId,
                                String message) {

        /**
         * 规范构造器：列表深拷贝，避免外部持有内部引用。
         *
         * @param enabled          是否开启
         * @param pinned           是否已钉住
         * @param defaultValue     默认值
         * @param tasks            任务列表
         * @param generatedAt      生成时间
         * @param basedOnMessageId 依据消息 id
         * @param message          原因文案
         */
        public NextTaskState {
            tasks = List.copyOf(tasks);
        }
    }

    /**
     * 读会话的联想状态；会话尚无自己的取值时把当前默认值**钉进会话**。
     *
     * @param sessionId 会话 id
     * @return 联想状态
     */
    public NextTaskState state(Long sessionId) {
        SysAiSession session = requireSession(sessionId);
        Map<String, Object> settings = readSettings(session.getSettings());
        Map<String, Object> node = asMap(settings.get(SETTINGS_KEY));
        boolean defaultValue = defaultEnabled(session);
        Boolean own = asBoolean(node.get("enabled"));

        if (own != null) {
            return assemble(own, true, defaultValue, node);
        }

        // 会话还没有自己的取值：按「会话保留原始信息」把当时的默认值钉下来，
        // 之后管理员改全局/本人默认值都不再回溯影响这个会话。
        settings.put(SETTINGS_KEY, withPinned(node, defaultValue));
        persist(session, settings);
        return assemble(defaultValue, true, defaultValue, withPinned(node, defaultValue));
    }

    /**
     * 设置（true/false）或解除（null）该会话自己的取值。
     *
     * @param sessionId 会话 id
     * @param enabled   true/false 写入会话；null 表示解除钉住、回到跟随默认值
     * @return 设置后的联想状态
     */
    public NextTaskState setEnabled(Long sessionId, Boolean enabled) {
        SysAiSession session = requireSession(sessionId);
        Map<String, Object> settings = readSettings(session.getSettings());
        Map<String, Object> node = asMap(settings.get(SETTINGS_KEY));
        boolean defaultValue = defaultEnabled(session);

        if (enabled == null) {
            node.remove("enabled");
            node.remove("pinnedAt");
            // 解除钉住后不再保留旧联想结果：它是在旧开关语义下生成的，留着会误导。
            node.remove("tasks");
            node.remove("generatedAt");
            node.remove("basedOnMessageId");
        } else {
            node.put("enabled", enabled);
            node.put("pinnedAt", System.currentTimeMillis());
        }
        settings.put(SETTINGS_KEY, node);
        persist(session, settings);
        return assemble(enabled != null ? enabled : defaultValue, enabled != null, defaultValue, node);
    }

    /**
     * 重新联想下一步任务（同步调用一次模型）。
     *
     * @param sessionId 会话 id
     * @return 联想后的状态；不可用时 tasks 为空且 message 说明原因
     */
    public NextTaskState refresh(Long sessionId) {
        SysAiSession session = requireSession(sessionId);
        Map<String, Object> settings = readSettings(session.getSettings());
        Map<String, Object> node = asMap(settings.get(SETTINGS_KEY));
        boolean defaultValue = defaultEnabled(session);
        boolean enabled = asBoolean(node.get("enabled")) != null
                ? asBoolean(node.get("enabled")) : defaultValue;

        List<SysAiMessage> messages = recentMessages(sessionId);
        if (messages.isEmpty()) {
            return assembleWithMessage(enabled, node, defaultValue, "会话还没有消息，无法联想下一步任务");
        }
        String model = effectiveChatModelResolver.resolve(session.getModelId());
        if (model == null || model.isBlank()) {
            return assembleWithMessage(enabled, node, defaultValue, "未解析到可用模型，无法联想下一步任务");
        }

        int max = maxCount(session);
        String transcript = buildTranscript(messages);
        String text;
        try {
            AiChatRequest request = new AiChatRequest();
            request.setModel(model);
            request.setStream(false);
            request.setSystemPrompt(systemPrompt(max));
            request.setMessages(List.of(Map.of("role", "user", "content",
                    "以下是本次会话的最近对话：\n\n" + transcript + "\n\n请给出接下来最值得做的任务。")));
            Map<String, Object> response = aiChatFacadeService.call(request);
            Object content = response == null ? null : response.get("text");
            text = content == null ? "" : String.valueOf(content).trim();
        } catch (Exception e) {
            log.warn("[NextTask] 联想失败 sessionId={} model={}: {}", sessionId, model, e.getMessage());
            return assembleWithMessage(enabled, node, defaultValue, "联想失败：" + e.getMessage());
        }

        List<NextTask> tasks = parseTasks(text, max);
        if (tasks.isEmpty()) {
            // 没有解析出可执行任务时**保留上一次的结果**：清空会让面板突然空掉，
            // 看起来像功能坏了。改成带原因返回，由前端提示用户可点刷新重试。
            return assembleWithMessage(enabled, node, defaultValue, "模型没有给出可执行的下一步任务，可点刷新重试");
        }

        node.put("tasks", tasks.stream().map(task -> {
            Map<String, Object> item = new HashMap<>(4);
            item.put("title", task.title());
            item.put("detail", task.detail());
            item.put("order", task.order());
            return item;
        }).toList());
        node.put("generatedAt", System.currentTimeMillis());
        node.put("basedOnMessageId", messages.get(messages.size() - 1).getId());
        // 未钉住时顺手钉住：本次会话已经在用这个开关语义了。
        if (node.get("enabled") == null) {
            node.put("enabled", enabled);
            node.put("pinnedAt", System.currentTimeMillis());
        }
        settings.put(SETTINGS_KEY, node);
        persist(session, settings);
        return assemble(enabled, true, defaultValue, node);
    }

    /**
     * 拼装状态（不带原因文案）。
     *
     * @param enabled      生效开关
     * @param pinned       是否已钉在会话上
     * @param defaultValue 当前默认值
     * @param node         会话设置节点
     * @return 状态
     */
    private NextTaskState assemble(boolean enabled, boolean pinned, boolean defaultValue,
                                   Map<String, Object> node) {
        return new NextTaskState(enabled, pinned, defaultValue, parseStoredTasks(node),
                asLong(node.get("generatedAt")), asLong(node.get("basedOnMessageId")), null);
    }

    /**
     * 拼装状态（带原因文案，沿用已有联想结果）。
     *
     * @param enabled      生效开关
     * @param node         会话设置节点
     * @param defaultValue 当前默认值
     * @param message      原因文案
     * @return 状态
     */
    private NextTaskState assembleWithMessage(boolean enabled, Map<String, Object> node,
                                              boolean defaultValue, String message) {
        NextTaskState state = assemble(enabled, node.get("enabled") != null, defaultValue, node);
        return new NextTaskState(state.enabled(), state.pinned(), state.defaultValue(),
                state.tasks(), state.generatedAt(), state.basedOnMessageId(), message);
    }

    /**
     * 「不是下一步任务」的元叙述模式。
     *
     * <p>模型在没给出任务时爱写"等待用户进一步提问 / 用户已完成简单问候，建议等待…"这类
     * 总结式句子。它们被当成任务落库后，面板就会显示一句"思考体"（用户报障：
     * "这个是 ai 建议还是思考？怎么会在 ai 建议里"）。读侧与写侧都过滤。</p>
     */
    private static final java.util.regex.Pattern META_TASK = java.util.regex.Pattern.compile(
            "等待用户|等待您|建议等待|请提出|无具体|没有具体|无法确定|暂不|无需|寒暄|问候"
                    + "|waiting for the user|no specific|no further");

    /**
     * 判断是否是"元叙述"而非可执行任务。
     *
     * @param title  标题
     * @param detail 说明
     * @return true=应被过滤掉
     */
    private static boolean isMetaTask(String title, String detail) {
        String t = title == null ? "" : title.trim();
        if (t.isEmpty()) {
            return true;
        }
        String both = t + " " + (detail == null ? "" : detail.trim());
        return META_TASK.matcher(both).find();
    }

    /**
     * 读取会话设置里已存的联想结果。
     *
     * @param node 会话设置节点
     * @return 任务列表；无数据时为空列表
     */
    private List<NextTask> parseStoredTasks(Map<String, Object> node) {
        List<NextTask> result = new ArrayList<>();
        Object raw = node.get("tasks");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        for (Object element : list) {
            Map<String, Object> item = asMap(element);
            String title = firstText(item, "title", "task", "name");
            String detail = firstText(item, "detail", "description", "desc");
            // 读侧同样过滤元叙述：历史脏数据（如"等待用户进一步提问"）不再显示在建议里
            if (isMetaTask(title, detail)) {
                continue;
            }
            result.add(new NextTask(title, detail, result.size() + 1));
        }
        return List.copyOf(result);
    }

    /**
     * 解析模型返回的 JSON 数组。
     *
     * <p>模型经常不守规矩：包 ```json 围栏、先写一句「好的，建议如下：」、
     * 甚至直接返回对象而不是数组。这里按「取第一个 {@code [} 到最后一个 {@code ]}」的
     * 方式兜底，再逐项兼容字符串/对象两种形态；解析不出来就返回空列表，
     * 由调用方给出原因文案，而不是抛异常把整个面板打成错误态。</p>
     *
     * @param text 模型返回文本
     * @param max  条数上限
     * @return 解析出的任务列表
     */
    private List<NextTask> parseTasks(String text, int max) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String cleaned = text.replace("```json", "").replace("```JSON", "").replace("```", "");
        int start = cleaned.indexOf('[');
        int end = cleaned.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return List.of();
        }
        List<NextTask> result = new ArrayList<>();
        try {
            JsonNode array = objectMapper.readTree(cleaned.substring(start, end + 1));
            if (!array.isArray()) {
                return List.of();
            }
            for (JsonNode element : array) {
                if (result.size() >= max) {
                    break;
                }
                String title;
                String detail;
                if (element.isObject()) {
                    title = firstText(element, "title", "task", "name");
                    detail = firstText(element, "detail", "description", "desc");
                } else if (element.isTextual()) {
                    title = element.asText();
                    detail = "";
                } else {
                    continue;
                }
                title = truncate(title, TITLE_LIMIT);
                // 写侧过滤：模型没给出任务时写的"等待用户…/建议等待…"一律不进库
                if (isMetaTask(title, detail)) {
                    continue;
                }
                result.add(new NextTask(title, truncate(detail, DETAIL_LIMIT), result.size() + 1));
            }
        } catch (Exception e) {
            log.warn("[NextTask] 解析模型输出失败: {}", e.getMessage());
            return List.of();
        }
        return List.copyOf(result);
    }

    /**
     * 构造联想用的系统提示词（条数来自配置，不写死）。
     *
     * @param max 最多几条
     * @return 系统提示词
     */
    private String systemPrompt(int max) {
        return """
                你是一名会话任务规划助手。请根据给定的对话，判断用户接下来最可能做的事，\
                给出可以直接执行的下一步任务建议。
                硬性要求：
                1. 只输出一个 JSON 数组，不要输出解释、前后缀或代码块围栏；
                2. 数组每项形如 {"title":"任务标题（不超过20字）","detail":"一句话说明怎么做（不超过40字）"}；
                3. title 必须是一个可执行的动作，禁止空泛（例如"继续优化""再看看"）；
                4. 不要重复对话里已经完成的事情；
                5. 最多 %d 条，宁缺毋滥；没有明确下一步时输出 []。
                """.formatted(max);
    }

    /**
     * 拼装参与联想的对话文本。
     *
     * @param messages 最近消息
     * @return 对话文本
     */
    private String buildTranscript(List<SysAiMessage> messages) {
        StringBuilder transcript = new StringBuilder(1024);
        for (SysAiMessage message : messages) {
            String content = message.getContent() == null ? "" : message.getContent().trim();
            if (content.isEmpty()) {
                // 空内容用户消息不参与：会污染联想（历史上曾落过内容为空的行）。
                continue;
            }
            transcript.append("user".equals(message.getRole()) ? "[用户] " : "[助手] ")
                    .append(truncate(content, TRANSCRIPT_CHARS_PER_MESSAGE))
                    .append('\n');
        }
        return transcript.toString();
    }

    /**
     * 取最近若干条消息（按 id 升序）。
     *
     * @param sessionId 会话 id
     * @return 消息列表
     */
    private List<SysAiMessage> recentMessages(Long sessionId) {
        LambdaQueryWrapper<SysAiMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysAiMessage::getSysAiSessionId, sessionId)
                .orderByDesc(SysAiMessage::getId)
                .last("LIMIT " + TRANSCRIPT_MESSAGES);
        List<SysAiMessage> desc = messageMapper.selectList(wrapper);
        List<SysAiMessage> ordered = new ArrayList<>(desc);
        java.util.Collections.reverse(ordered);
        return ordered;
    }

    /**
     * 取该会话的默认值：用户覆盖优先，全局兜底，默认关闭。
     *
     * @param session 会话
     * @return 默认开关值
     */
    private boolean defaultEnabled(SysAiSession session) {
        String value = globalSettingReader.getStrAs(session.getSysUserId(), GROUP, FIELD_ENABLED);
        if (value == null || value.isBlank()) {
            return false;
        }
        String lower = value.trim().toLowerCase(java.util.Locale.ROOT);
        return "true".equals(lower) || "1".equals(lower) || "yes".equals(lower);
    }

    /**
     * 取联想条数上限（配置缺失/非法时回落到默认档位，并夹在 1-10 之间）。
     *
     * @param session 会话
     * @return 条数上限
     */
    private int maxCount(SysAiSession session) {
        String value = globalSettingReader.getStrAs(session.getSysUserId(), GROUP, FIELD_MAX);
        int parsed = DEFAULT_MAX;
        if (value != null && !value.isBlank()) {
            try {
                parsed = Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                log.debug("[NextTask] {} 非整数: {}", FIELD_MAX, value);
                parsed = DEFAULT_MAX;
            }
        }
        return Math.min(Math.max(parsed, 1), MAX_LIMIT);
    }

    /**
     * 给会话设置节点补上钉住信息。
     *
     * @param node    现有节点
     * @param enabled 生效值
     * @return 补齐后的节点（同一实例，调用方负责放回 Map）
     */
    private Map<String, Object> withPinned(Map<String, Object> node, boolean enabled) {
        Map<String, Object> merged = new HashMap<>(node);
        merged.put("enabled", enabled);
        merged.putIfAbsent("pinnedAt", System.currentTimeMillis());
        return merged;
    }

    /**
     * 持久化会话设置。
     *
     * @param session  会话
     * @param settings 会话设置
     */
    private void persist(SysAiSession session, Map<String, Object> settings) {
        session.setSettings(writeSettings(settings));
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(session);
    }

    /**
     * 取会话，不存在时 404。
     *
     * @param sessionId 会话 id
     * @return 会话
     */
    private SysAiSession requireSession(Long sessionId) {
        SysAiSession session = sessionId == null ? null : sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话不存在");
        }
        return session;
    }

    /**
     * 解析会话 settings JSON。
     *
     * @param json settings JSON
     * @return 解析结果；为空或损坏时返回空 Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> readSettings(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, Map.class);
            return parsed != null ? new HashMap<>(parsed) : new HashMap<>();
        } catch (Exception e) {
            log.warn("[NextTask] 解析会话 settings 失败: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * 序列化会话 settings。
     *
     * @param settings 会话设置
     * @return JSON；失败时返回 null（保持原值不被清空）
     */
    private String writeSettings(Map<String, Object> settings) {
        try {
            return objectMapper.writeValueAsString(settings);
        } catch (Exception e) {
            log.warn("[NextTask] 序列化会话 settings 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 取 Map 形态的子对象。
     *
     * @param value 任意值
     * @return 子对象 Map；非 Map 时返回可写空 Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return new HashMap<>((Map<String, Object>) map);
        }
        return new HashMap<>();
    }

    /**
     * 取布尔值。
     *
     * @param value 任意值
     * @return 布尔值；无法解析时返回 null（表示「没有显式取值」）
     */
    private Boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim().toLowerCase(java.util.Locale.ROOT);
        if (text.isEmpty()) {
            return null;
        }
        return "true".equals(text) || "1".equals(text) || "yes".equals(text);
    }

    /**
     * 取长整型值。
     *
     * @param value 任意值
     * @return 数值；无法解析时返回 null
     */
    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 按候选字段名顺序取第一个非空文本。
     *
     * @param source  Map 形态对象
     * @param keys    候选字段名
     * @return 文本；都取不到时返回空串
     */
    private String firstText(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return "";
    }

    /**
     * 按候选字段名顺序取第一个非空文本（JsonNode 版）。
     *
     * @param node Json 节点
     * @param keys 候选字段名
     * @return 文本；都取不到时返回空串
     */
    private String firstText(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return "";
    }

    /**
     * 截断文本（超长时补省略号）。
     *
     * @param text  原文
     * @param limit 上限
     * @return 截断后的文本
     */
    private String truncate(String text, int limit) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= limit ? trimmed : trimmed.substring(0, limit) + "…";
    }
}
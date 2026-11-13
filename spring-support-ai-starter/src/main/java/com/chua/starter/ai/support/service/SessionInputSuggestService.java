package com.chua.starter.ai.support.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.configuration.client.EffectiveChatModelResolver;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.entity.SysAiMessage;
import com.chua.starter.ai.support.entity.SysAiSession;
import com.chua.starter.ai.support.mapper.SysAiMessageMapper;
import com.chua.starter.ai.support.mapper.SysAiSessionMapper;
import com.chua.starter.ai.support.model.AiChatRequest;
import com.chua.starter.ai.support.model.InputSuggestResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 会话输入框预测服务。
 *
 * <p>用户在会话页把输入框留在空态时，用模型根据最近几轮对话预测「他下一句最可能说什么」，
 * 前端以灰色幽灵文本呈现，<b>只有用户按 Tab 才填入输入框，填入后仍是可编辑草稿，
 * 由用户自己确认发送</b> —— 预测本身绝不直接发出任何消息。</p>
 *
 * <p><b>模型口径（用户需求 1.1）</b>：</p>
 * <ol>
 *   <li>{@code input_suggest.suggest_model}（个人覆盖优先于全局）有值 → 用它；</li>
 *   <li>否则用调用方带来的当前会话模型（{@link #suggest(Long, String)} 的 {@code requestedModel}）；</li>
 *   <li>再兜底 {@link EffectiveChatModelResolver}（会话模型 → bot 默认 → 用户默认 → 全局默认）。</li>
 * </ol>
 *
 * <p>预测是一次<b>短调用</b>：只取最近若干条消息、限制输出 token 与字数。
 * 未开启、没有历史消息、模型不可用、调用失败、模型没给出可用内容时，都返回空建议 + 原因文案，
 * 不抛异常、不伪造内容。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionInputSuggestService {

    /**
     * 配置分组
     */
    private static final String GROUP = "input_suggest";

    /**
     * 「预测模型」字段名
     */
    private static final String FIELD_MODEL = "suggest_model";

    /**
     * 「是否启用」字段名
     */
    private static final String FIELD_ENABLED = "suggest_enabled";

    /**
     * 「自定义提示词」字段名
     */
    private static final String FIELD_PROMPT = "suggest_prompt";

    /**
     * 「温度」字段名
     */
    private static final String FIELD_TEMPERATURE = "suggest_temperature";

    /**
     * 「参与预测的最近消息条数」字段名
     */
    private static final String FIELD_CONTEXT_MESSAGES = "suggest_context_messages";

    /**
     * 参与预测的默认消息条数
     */
    private static final int DEFAULT_CONTEXT_MESSAGES = 8;

    /**
     * 参与预测的消息条数上限：再多也只是把旧话题重新拖进来
     */
    private static final int MAX_CONTEXT_MESSAGES = 20;

    /**
     * 单条消息参与预测的最大字符数（长文档/长回答按前缀截断）
     */
    private static final int TRANSCRIPT_CHARS_PER_MESSAGE = 600;

    /**
     * 默认温度：预测要稳，不需要发散
     */
    private static final double DEFAULT_TEMPERATURE = 0.4;

    /**
     * 输出 token 上限：一句话的预测，给多了纯属浪费
     */
    private static final int MAX_TOKENS = 96;

    /**
     * 预测文本的字数上限（模型偶尔会写一整段，这里兜底）
     */
    private static final int SUGGESTION_LIMIT = 120;

    /**
     * 内置默认预测指令
     */
    private static final String DEFAULT_PROMPT = """
            你是聊天输入框的联想助手。根据下面的会话上下文，预测用户下一条最可能发送的消息。
            硬性要求：
            1. 站在用户第一人称，写成用户可以立刻发出去的完整一句话；
            2. 与上下文使用同一种语言，不超过 40 个字；
            3. 只输出这句话本身：不要解释、不要引号、不要代码块、不要「预测：」之类的前缀；
            4. 不要重复用户已经说过的话，也不要总结助手做过的事；
            5. 上下文不足以推断时，输出空内容。
            """;

    /**
     * 模型输出里常见的「前缀」：模型不守规矩时会加上，统一剥掉
     */
    private static final String[] LEADING_PREFIXES = {
            "预测内容：", "预测：", "建议：", "下一句：", "用户：", "回答：",
            "suggestion:", "prediction:", "user:"
    };

    /**
     * 会话映射
     */
    private final SysAiSessionMapper sessionMapper;

    /**
     * 消息映射
     */
    private final SysAiMessageMapper messageMapper;

    /**
     * 全局设置读取器（个人覆盖优先）
     */
    private final GlobalSettingReader globalSettingReader;

    /**
     * 生效对话模型解析器
     */
    private final EffectiveChatModelResolver effectiveChatModelResolver;

    /**
     * 对话门面（同步一次性调用）
     */
    private final AiChatFacadeService aiChatFacadeService;

    /**
     * 预测会话的下一条用户消息。
     *
     * @param sessionId      会话 id
     * @param requestedModel 调用方带来的当前模型（可空）
     * @return 预测结果；不可用时 {@code suggestion} 为空且 {@code message} 说明原因
     */
    public InputSuggestResponse suggest(Long sessionId, String requestedModel) {
        long start = System.currentTimeMillis();
        SysAiSession session = sessionId == null ? null : sessionMapper.selectById(sessionId);
        if (session == null) {
            return InputSuggestResponse.of("", "", System.currentTimeMillis() - start, "会话不存在");
        }
        String userId = session.getSysUserId();
        if (!enabled(userId)) {
            return InputSuggestResponse.of("", "", System.currentTimeMillis() - start, "输入预测已关闭");
        }

        List<SysAiMessage> messages = recentMessages(sessionId, contextMessages(userId));
        if (messages.isEmpty()) {
            return InputSuggestResponse.of("", "", System.currentTimeMillis() - start,
                    "会话还没有消息，暂不预测");
        }

        String model = resolveModel(userId, session, requestedModel);
        if (model == null || model.isBlank()) {
            return InputSuggestResponse.of("", "", System.currentTimeMillis() - start,
                    "未解析到可用的预测模型");
        }

        String text;
        try {
            AiChatRequest request = new AiChatRequest();
            request.setModel(model);
            request.setStream(false);
            request.setSystemPrompt(systemPrompt(userId));
            request.setTemperature(temperature(userId));
            request.setMaxTokens(MAX_TOKENS);
            request.setMessages(List.of(Map.of("role", "user", "content", userPrompt(messages))));
            Map<String, Object> response = aiChatFacadeService.call(request);
            Object content = response == null ? null : response.get("text");
            text = content == null ? "" : String.valueOf(content);
        } catch (Exception e) {
            log.warn("[InputSuggest] 预测失败 sessionId={} model={}: {}", sessionId, model, e.getMessage());
            return InputSuggestResponse.of("", model, System.currentTimeMillis() - start,
                    "预测失败：" + e.getMessage());
        }

        String suggestion = sanitize(text);
        if (suggestion.isEmpty()) {
            return InputSuggestResponse.of("", model, System.currentTimeMillis() - start,
                    "模型没有给出可用的预测");
        }
        return InputSuggestResponse.of(suggestion, model, System.currentTimeMillis() - start, null);
    }

    /**
     * 是否启用输入预测（默认开）。
     *
     * @param userId 会话归属账号
     * @return true 表示启用
     */
    private boolean enabled(String userId) {
        return parseBool(globalSettingReader.getStrAs(userId, GROUP, FIELD_ENABLED), true);
    }

    /**
     * 解析预测模型。
     *
     * @param userId         会话归属账号
     * @param session        会话
     * @param requestedModel 调用方带来的当前模型
     * @return 预测模型；都无法解析时返回 null
     */
    private String resolveModel(String userId, SysAiSession session, String requestedModel) {
        String configured = globalSettingReader.getStrAs(userId, GROUP, FIELD_MODEL);
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        if (requestedModel != null && !requestedModel.isBlank()) {
            return requestedModel.trim();
        }
        return effectiveChatModelResolver.resolve(session.getModelId());
    }

    /**
     * 读取预测提示词：配置优先，空则用内置默认。
     *
     * @param userId 会话归属账号
     * @return 系统提示词
     */
    private String systemPrompt(String userId) {
        String configured = globalSettingReader.getStrAs(userId, GROUP, FIELD_PROMPT);
        return configured == null || configured.isBlank() ? DEFAULT_PROMPT : configured;
    }

    /**
     * 读取预测温度，并在合理区间内收口。
     *
     * @param userId 会话归属账号
     * @return 温度
     */
    private double temperature(String userId) {
        String raw = globalSettingReader.getStrAs(userId, GROUP, FIELD_TEMPERATURE);
        if (raw == null || raw.isBlank()) {
            return DEFAULT_TEMPERATURE;
        }
        try {
            double value = Double.parseDouble(raw.trim());
            if (value < 0D || value > 1D) {
                return DEFAULT_TEMPERATURE;
            }
            return value;
        } catch (NumberFormatException e) {
            return DEFAULT_TEMPERATURE;
        }
    }

    /**
     * 读取参与预测的消息条数，夹在 1-{@value #MAX_CONTEXT_MESSAGES} 之间。
     *
     * @param userId 会话归属账号
     * @return 条数
     */
    private int contextMessages(String userId) {
        String raw = globalSettingReader.getStrAs(userId, GROUP, FIELD_CONTEXT_MESSAGES);
        if (raw == null || raw.isBlank()) {
            return DEFAULT_CONTEXT_MESSAGES;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return Math.min(Math.max(value, 1), MAX_CONTEXT_MESSAGES);
        } catch (NumberFormatException e) {
            return DEFAULT_CONTEXT_MESSAGES;
        }
    }

    /**
     * 取最近若干条消息（按 id 升序返回，便于按时间读）。
     *
     * @param sessionId 会话 id
     * @param limit     条数
     * @return 消息列表
     */
    private List<SysAiMessage> recentMessages(Long sessionId, int limit) {
        LambdaQueryWrapper<SysAiMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysAiMessage::getSysAiSessionId, sessionId)
                .orderByDesc(SysAiMessage::getId)
                .last("LIMIT " + limit);
        List<SysAiMessage> desc = messageMapper.selectList(wrapper);
        if (desc == null || desc.isEmpty()) {
            return List.of();
        }
        List<SysAiMessage> ordered = new ArrayList<>(desc);
        Collections.reverse(ordered);
        return ordered;
    }

    /**
     * 拼装用户消息：对话转写 + 明确的「只要一句话」要求。
     *
     * @param messages 最近消息
     * @return 用户提示词
     */
    private String userPrompt(List<SysAiMessage> messages) {
        // [P3C A20 豁免]
        StringBuilder transcript = new StringBuilder(1024);
        for (SysAiMessage message : messages) {
            String content = message.getContent() == null ? "" : message.getContent().trim();
            if (content.isEmpty()) {
                continue;
            }
            transcript.append("user".equals(message.getRole()) ? "[用户] " : "[助手] ")
                    .append(truncate(content, TRANSCRIPT_CHARS_PER_MESSAGE))
                    .append('\n');
        }
        return "以下是本次会话最近的对话：\n\n" + transcript
                + "\n现在用户把输入框留空了，请预测他下一条会发送的消息。只输出那句话本身。";
    }

    /**
     * 清洗模型输出：去围栏、去引号、去前缀、压成一行、按上限截断。
     *
     * @param text 模型原始输出
     * @return 可直接填入输入框的建议；无有效内容时为空串
     */
    private String sanitize(String text) {
        if (text == null) {
            return "";
        }
        String cleaned = text.replace("```markdown", "").replace("```text", "")
                .replace("```", "").trim();
        // 只取第一段非空行：模型偶尔先来一句「好的，建议如下：」再给正文
        String firstLine = "";
        for (String line : cleaned.split("\\R")) {
            if (!line.isBlank()) {
                firstLine = line.trim();
                break;
            }
        }
        if (firstLine.isEmpty()) {
            return "";
        }
        firstLine = stripQuotes(firstLine);
        firstLine = stripPrefix(firstLine);
        firstLine = firstLine.replaceAll("\\s+", " ").trim();
        firstLine = stripQuotes(firstLine);
        if (firstLine.isEmpty()) {
            return "";
        }
        return firstLine.length() <= SUGGESTION_LIMIT
                ? firstLine : firstLine.substring(0, SUGGESTION_LIMIT) + "…";
    }

    /**
     * 剥掉成对的首尾引号/反引号。
     *
     * @param text 文本
     * @return 去引号后的文本
     */
    private String stripQuotes(String text) {
        String value = text.trim();
        if (value.length() < 2) {
            return value;
        }
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        boolean pair = (first == '"' && last == '"')
                || (first == '\'' && last == '\'')
                || (first == '`' && last == '`')
                || (first == '“' && last == '”')
                || (first == '「' && last == '」')
                || (first == '『' && last == '』');
        return pair ? value.substring(1, value.length() - 1).trim() : value;
    }

    /**
     * 剥掉模型爱加的「预测：」之类前缀（大小写不敏感）。
     *
     * @param text 文本
     * @return 去前缀后的文本
     */
    private String stripPrefix(String text) {
        String value = text.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        for (String prefix : LEADING_PREFIXES) {
            String prefixLower = prefix.toLowerCase(Locale.ROOT);
            if (lower.startsWith(prefixLower)) {
                return value.substring(prefix.length()).trim();
            }
        }
        return value;
    }

    /**
     * 解析布尔配置。
     *
     * @param raw          原始值
     * @param defaultValue 解析失败时的默认值
     * @return 布尔值
     */
    private boolean parseBool(String raw, boolean defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(value) || "1".equals(value) || "yes".equals(value) || "on".equals(value)) {
            return true;
        }
        if ("false".equals(value) || "0".equals(value) || "no".equals(value) || "off".equals(value)) {
            return false;
        }
        return defaultValue;
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

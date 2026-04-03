package com.chua.starter.ai.support.agent;

import com.chua.common.support.ai.agent.AgentContext;
import com.chua.common.support.ai.agent.AgentOptions;
import com.chua.common.support.core.utils.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent 请求适配器。
 *
 * @author CH
 * @since 2026/04/03
 */
public final class AgentRequestAdapter {

    private AgentRequestAdapter() {
    }

    /**
     * 将 common AgentRequest 适配为 Spring 层请求。
     *
     * @param request common Agent 请求
     * @return Spring 层请求
     */
    public static AgentRequest from(com.chua.common.support.ai.agent.AgentRequest request) {
        if (request == null) {
            return AgentRequest.builder().build();
        }
        AgentContext context = request.getContext();
        AgentOptions options = request.getOptions();
        return AgentRequest.builder()
                .requestId(request.getRequestId())
                .sessionId(context == null ? null : context.getSessionId())
                .input(request.getInput())
                .model(options == null ? null : options.getModel())
                .systemPrompt(context == null ? null : context.getSystemPrompt())
                .temperature(options == null ? null : options.getTemperature())
                .timeoutMillis(options == null ? null : options.getTimeoutMillis())
                .reasoningEffort(stringAttribute(request.getAttributes(), "reasoningEffort", "reasoningStrength"))
                .userAgent(stringAttribute(request.getAttributes(), "userAgent", "User-Agent"))
                .taskMode(taskMode(request.getAttributes().get("taskMode")))
                .messages(request.getMessages())
                .context(context)
                .inputOptimizationEnabled(options != null && options.getInputOptimization() != null
                        && options.getInputOptimization().isEnabled())
                .contextCompressionEnabled(options != null && options.isContextCompressionEnabled())
                .attributes(request.getAttributes())
                .build();
    }

    /**
     * 将 Spring 层请求适配为 common AgentRequest。
     *
     * @param request Spring 层请求
     * @param options 运行选项
     * @param context 当前上下文
     * @return common AgentRequest
     */
    public static com.chua.common.support.ai.agent.AgentRequest toCommon(AgentRequest request,
                                                                         AgentOptions options,
                                                                         AgentContext context) {
        AgentRequest source = request == null ? AgentRequest.builder().build() : request;
        Map<String, Object> attributes = new LinkedHashMap<>(source.getAttributes());
        if (StringUtils.isNotBlank(source.getReasoningEffort())) {
            attributes.put("reasoningEffort", source.getReasoningEffort());
        }
        if (StringUtils.isNotBlank(source.getUserAgent())) {
            attributes.put("userAgent", source.getUserAgent());
        }
        if (source.getTaskMode() != null) {
            attributes.put("taskMode", source.getTaskMode().name());
        }
        return com.chua.common.support.ai.agent.AgentRequest.builder()
                .requestId(source.getRequestId())
                .input(source.getInput())
                .messages(source.getMessages())
                .context(context)
                .options(options)
                .attributes(attributes)
                .build();
    }

    private static String stringAttribute(Map<String, Object> attributes, String... keys) {
        if (attributes == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            Object value = attributes.get(key);
            if (value != null && StringUtils.isNotBlank(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private static AgentTaskMode taskMode(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return AgentTaskMode.valueOf(String.valueOf(value).trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}

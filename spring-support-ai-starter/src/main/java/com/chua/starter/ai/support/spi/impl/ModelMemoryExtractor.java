// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.spi.impl;

import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.configuration.memory.MemoryGlobalSettingProvider;
import com.chua.starter.ai.support.model.AiChatRequest;
import com.chua.starter.ai.support.model.AiChatResult;
import com.chua.starter.ai.support.model.MemoryCandidateDTO;
import com.chua.starter.ai.support.model.MemoryExtractionContext;
import com.chua.starter.ai.support.service.AiChatFacadeService;
import com.chua.starter.ai.support.spi.MemoryExtractor;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 模型记忆抽取器 —— 让模型自己判断一轮对话里值得长期记住什么。
 *
 * <p>规则抽取只能覆盖显式句式，本抽取器补上隐含信息（例如用户反复纠正的口径、项目背景约束）。
 * 输出被约束成 JSON 数组，解析失败或模型不可用时直接返回空列表，不影响对话。</p>
 *
 * <p>依赖可用模型，因此默认走设置项 {@code memory_extract_model} 指定的模型；留空则回落到本轮对话
 * 实际使用的模型。两者都拿不到时 {@link #supports(MemoryExtractionContext)} 返回假，一次模型调用也不会发出。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class ModelMemoryExtractor implements MemoryExtractor {

    /**
     * 抽取提示词
     */
    private static final String EXTRACT_PROMPT = """
            你是记忆管理员。阅读下面一轮对话，只输出值得在后续会话中长期使用的记忆，\
            严格输出 JSON 数组（不要任何解释、不要代码块标记），每项字段为：
            kind(preference|fact|instruction|episode)、title(不超过 20 字的摘要标题)、\
            content(自包含的一句话，不依赖本轮上下文也能读懂)、keywords(字符串数组，不超过 6 个)、\
            importance(0 到 1 的小数)。
            没有值得长期记住的内容时输出 []。不要把一次性提问、模型自己的推测写成记忆。
            """;

    /**
     * JSON 解析器（线程安全，复用单例）
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 对话门面（用于同步调用模型）
     */
    private final ObjectProvider<AiChatFacadeService> facadeProvider;

    /**
     * 全局设置读取器
     */
    private final GlobalSettingReader settings;

    /**
     * 构造模型抽取器。
     *
     * @param facadeProvider 对话门面提供者（延迟获取以避免与对话链路形成循环依赖）
     * @param settings       全局设置读取器
     */
    public ModelMemoryExtractor(ObjectProvider<AiChatFacadeService> facadeProvider, GlobalSettingReader settings) {
        this.facadeProvider = facadeProvider;
        this.settings = settings;
    }

    @Override
    /**
     * 抽取器名称。
     *
     * @return 名称
     */
    public String name() {
        return "模型抽取（总结值得长期记住的内容）";
    }

    @Override
    /**
     * 是否有可用模型来完成本轮抽取。
     *
     * @param context 抽取上下文
     * @return true 表示执行抽取
     */
    public boolean supports(MemoryExtractionContext context) {
        if (context == null || context.userMessage() == null || context.userMessage().isBlank()) {
            return false;
        }
        if (resolveModel(context) == null) {
            return false;
        }
        // 过短的输入没有总结价值，省掉一次模型调用
        return context.userMessage().trim().length() >= 8;
    }

    @Override
    /**
     * 调用模型抽取候选记忆。
     *
     * @param context 抽取上下文
     * @return 候选记忆列表，模型不可用或输出不可解析时为空
     */
    public List<MemoryCandidateDTO> extract(MemoryExtractionContext context) {
        AiChatFacadeService facade = facadeProvider.getIfAvailable();
        if (facade == null) {
            return List.of();
        }
        String extractModel = resolveModel(context);
        if (extractModel == null) {
            return List.of();
        }
        AiChatRequest request = new AiChatRequest();
        request.setSystemPrompt(EXTRACT_PROMPT);
        request.setPrompt(renderTurn(context));
        request.setStream(false);
        request.setCallType("memory-extract");
        request.setModel(extractModel);
        try {
            AiChatResult result = facade.callForResult(request);
            if (result == null || !result.success() || result.text() == null) {
                log.debug("[Memory] 模型抽取无可用结果: {}", result == null ? "null" : result.errorMessage());
                return List.of();
            }
            return parse(result.text());
        } catch (Exception e) {
            log.warn("[Memory] 模型抽取失败（忽略，不影响对话）: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 解析本轮抽取使用的模型：设置项优先，缺省回落到本轮对话实际使用的模型。
     *
     * @param context 抽取上下文
     * @return 模型名称，两者均未提供时返回 {@code null}
     */
    private String resolveModel(MemoryExtractionContext context) {
        String configured = settings.getStr(MemoryGlobalSettingProvider.GROUP, "memory_extract_model");
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        String turnModel = context.model();
        return turnModel != null && !turnModel.isBlank() ? turnModel.trim() : null;
    }

    /**
     * 渲染交给模型的对话轮次文本。
     *
     * @param context 抽取上下文
     * @return 提示词正文
     */
    private String renderTurn(MemoryExtractionContext context) {
        String reply = context.assistantReply();
        StringBuilder sb = new StringBuilder();
        sb.append("用户：").append(context.userMessage().trim()).append('\n');
        if (reply != null && !reply.isBlank()) {
            String clipped = reply.length() > 1500 ? reply.substring(0, 1500) : reply;
            sb.append("助手：").append(clipped.trim()).append('\n');
        }
        return sb.toString();
    }

    /**
     * 解析模型输出的 JSON 数组，容忍代码块与前后缀噪声。
     *
     * @param raw 模型原始输出
     * @return 候选记忆列表
     */
    @SuppressWarnings("unchecked")
    private List<MemoryCandidateDTO> parse(String raw) {
        String json = extractArray(raw);
        if (json == null) {
            return List.of();
        }
        List<MemoryCandidateDTO> candidates = new ArrayList<>();
        try {
            List<Object> rows = MAPPER.readValue(json, List.class);
            for (Object row : rows) {
                if (!(row instanceof Map)) {
                    continue;
                }
                Map<String, Object> map = (Map<String, Object>) row;
                String content = asText(map.get("content"));
                if (content == null || content.isBlank()) {
                    continue;
                }
                candidates.add(new MemoryCandidateDTO(
                        asText(map.getOrDefault("kind", "fact")),
                        asText(map.get("title")),
                        content,
                        asKeywords(map.get("keywords")),
                        asDouble(map.get("importance")),
                        null,
                        null));
            }
        } catch (Exception e) {
            log.debug("[Memory] 模型抽取输出无法解析: {}", e.getMessage());
        }
        return candidates;
    }

    /**
     * 截取第一个 '[' 到最后一个 ']' 之间的数组文本。
     *
     * @param raw 模型原始输出
     * @return 数组文本，不含数组时返回 {@code null}
     */
    private String extractArray(String raw) {
        if (raw == null) {
            return null;
        }
        int start = raw.indexOf('[');
        int end = raw.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return null;
        }
        return raw.substring(start, end + 1);
    }

    /**
     * 关键词字段转列表。
     *
     * @param value 原始字段
     * @return 关键词列表
     */
    private List<String> asKeywords(Object value) {
        List<String> keywords = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                String text = asText(item);
                if (text != null && !text.isBlank()) {
                    keywords.add(text);
                }
            }
        } else {
            String text = asText(value);
            if (text != null && !text.isBlank()) {
                keywords.add(text);
            }
        }
        return keywords;
    }

    /**
     * 文本字段安全取值。
     *
     * @param value 原始字段
     * @return 文本，空值返回 {@code null}
     */
    private String asText(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    /**
     * 数值字段安全取值。
     *
     * @param value 原始字段
     * @return 数值，无法解析返回 {@code null}
     */
    private Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? null : Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

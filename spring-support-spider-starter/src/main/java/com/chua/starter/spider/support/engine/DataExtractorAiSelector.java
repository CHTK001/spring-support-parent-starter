package com.chua.starter.spider.support.engine;

import com.chua.spider.support.brain.SpiderBrainRuntime;
import com.chua.starter.spider.support.domain.SpiderFlowNode;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * DATA_EXTRACTOR 节点 AI 选择器处理器。
 *
 * <p>当节点 config 中 {@code selectorType=AI} 时，使用 {@link SpiderBrainRuntime#select(String, String, String, String, boolean)}
 * 以自然语言描述（config 键 {@code aiDescription}）从页面 HTML 中提取数据。
 * 若 {@link SpiderBrainRuntime} 不可用（{@code isEnabled()==false}），则回退到 CSS 选择器。</p>
 *
 * <p>节点 config 支持以下键：
 * <ul>
 *   <li>{@code selectorType}：选择器类型，值为 {@code "AI"} 时启用 AI 选择</li>
 *   <li>{@code aiDescription}：自然语言描述，告知 AI 需要提取什么内容</li>
 *   <li>{@code selector}：CSS/XPath 选择器，AI 不可用时的回退选择器</li>
 *   <li>{@code attribute}：提取的属性名，默认 {@code "text"}</li>
 *   <li>{@code multi}：是否提取多值，默认 {@code true}</li>
 * </ul>
 * </p>
 *
 * @author CH
 */
@Slf4j
public class DataExtractorAiSelector {

    private static final String SELECTOR_TYPE_AI = "AI";

    private final SpiderFlowNode node;
    private final SpiderBrainRuntime brainRuntime;

    public DataExtractorAiSelector(SpiderFlowNode node, SpiderBrainRuntime brainRuntime) {
        this.node = node;
        this.brainRuntime = brainRuntime != null ? brainRuntime : SpiderBrainRuntime.disabled();
    }

    /**
     * 判断当前节点是否配置了 AI 选择器类型。
     *
     * @return {@code true} 表示 selectorType=AI
     */
    public boolean isAiSelectorEnabled() {
        Map<String, Object> config = node.getConfig();
        if (config == null) return false;
        Object selectorType = config.get("selectorType");
        return selectorType != null && SELECTOR_TYPE_AI.equalsIgnoreCase(selectorType.toString());
    }

    /**
     * 执行数据提取。
     *
     * <p>若 selectorType=AI 且 {@link SpiderBrainRuntime} 可用，则调用 AI 选择；
     * 否则回退到 CSS 选择器（由调用方处理）。</p>
     *
     * @param rawHtml 页面原始 HTML
     * @param pageUrl 页面 URL（用于 AI 上下文）
     * @return 提取到的值列表；若回退到 CSS 则返回空列表（调用方应使用 CSS 选择器）
     */
    public List<String> extract(String rawHtml, String pageUrl) {
        Map<String, Object> config = node.getConfig() != null ? node.getConfig() : Map.of();

        if (!isAiSelectorEnabled()) {
            return List.of();
        }

        String aiDescription = resolveString(config, "aiDescription", "");
        String attribute = resolveString(config, "attribute", "text");
        boolean multi = resolveBoolean(config, "multi", true);

        // AI 可用时调用 SpiderBrainRuntime.select()
        if (brainRuntime.isEnabled()) {
            log.debug("[Spider][DATA_EXTRACTOR][AI] nodeId={} 使用 AI 选择器，描述={}",
                    node.getNodeId(), aiDescription);
            try {
                List<String> result = brainRuntime.select(rawHtml, pageUrl, aiDescription, attribute, multi);
                log.debug("[Spider][DATA_EXTRACTOR][AI] nodeId={} AI 提取结果数量={}", node.getNodeId(), result.size());
                return result;
            } catch (Exception e) {
                log.warn("[Spider][DATA_EXTRACTOR][AI] nodeId={} AI 选择失败，回退到 CSS 选择器: {}",
                        node.getNodeId(), e.getMessage());
                return fallbackToCss(config, rawHtml, pageUrl);
            }
        }

        // AI 不可用，回退到 CSS 选择器
        log.warn("[Spider][DATA_EXTRACTOR][AI] nodeId={} SpiderBrainRuntime 不可用，回退到 CSS 选择器",
                node.getNodeId());
        return fallbackToCss(config, rawHtml, pageUrl);
    }

    /**
     * 回退到 CSS 选择器提取（返回空列表，由调用方使用 config["selector"] 执行 CSS 提取）。
     */
    private List<String> fallbackToCss(Map<String, Object> config, String rawHtml, String pageUrl) {
        // 回退逻辑：返回空列表，调用方应检查 isAiFallback() 并使用 CSS 选择器
        log.debug("[Spider][DATA_EXTRACTOR][AI] nodeId={} 回退到 CSS 选择器", node.getNodeId());
        return List.of();
    }

    /**
     * 是否应回退到 CSS 选择器（AI 不可用时）。
     *
     * @return {@code true} 表示 AI 不可用，应使用 CSS 选择器
     */
    public boolean shouldFallbackToCss() {
        return isAiSelectorEnabled() && !brainRuntime.isEnabled();
    }

    private String resolveString(Map<String, Object> config, String key, String defaultValue) {
        Object val = config.get(key);
        return (val != null && !val.toString().isBlank()) ? val.toString() : defaultValue;
    }

    private boolean resolveBoolean(Map<String, Object> config, String key, boolean defaultValue) {
        Object val = config.get(key);
        if (val == null) return defaultValue;
        if (val instanceof Boolean b) return b;
        return Boolean.parseBoolean(val.toString());
    }
}

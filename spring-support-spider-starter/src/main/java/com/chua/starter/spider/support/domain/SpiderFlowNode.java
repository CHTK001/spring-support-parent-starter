package com.chua.starter.spider.support.domain;

import com.alibaba.fastjson2.JSON;
import com.chua.starter.spider.support.domain.enums.SpiderNodeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 编排流程节点。
 *
 * <p>节点级 AI 配置（aiProfile）可通过两种方式存储：
 * <ol>
 *   <li>直接设置 {@link #aiAssistantConfig} 字段（推荐，强类型）</li>
 *   <li>在 {@link #config} Map 中以键 {@code "aiProfile"} 存储嵌套 Map（兼容前端 JSON 编排格式）</li>
 * </ol>
 * 使用 {@link #resolveAiProfile()} 可统一读取，优先级：{@code aiAssistantConfig} > {@code config["aiProfile"]}。
 * </p>
 *
 * <p>config["aiProfile"] 嵌套 Map 格式示例：
 * <pre>{@code
 * {
 *   "aiProfile": {
 *     "provider": "openai",
 *     "model": "gpt-4o",
 *     "enabled": true
 *   }
 * }
 * }</pre>
 * </p>
 */
@Slf4j
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpiderFlowNode {

    /** 节点唯一 ID */
    private String nodeId;

    /** 节点类型 */
    private SpiderNodeType nodeType;

    /** 节点显示标签 */
    private String label;

    /**
     * 节点配置（键值对）。
     *
     * <p>支持在此 Map 中以键 {@code "aiProfile"} 存储节点级 AI 配置（嵌套 Map 或 JSON 字符串），
     * 可通过 {@link #resolveAiProfile()} 统一读取。</p>
     */
    private Map<String, Object> config;

    /**
     * 节点级 AI 助手配置（强类型，优先于 config["aiProfile"]）。
     *
     * <p>字段包含：provider（提供商）、model（模型）、enabled（是否启用）等。</p>
     */
    private SpiderAiProfile aiAssistantConfig;

    /** 节点在画布上的 X 坐标 */
    private Double positionX;

    /** 节点在画布上的 Y 坐标 */
    private Double positionY;

    /**
     * 统一读取节点级 AI 配置。
     *
     * <p>优先级：{@link #aiAssistantConfig} > {@code config["aiProfile"]}（嵌套 Map 或 JSON 字符串）。</p>
     *
     * @return 节点级 {@link SpiderAiProfile}；若未配置则返回 {@code null}
     */
    public SpiderAiProfile resolveAiProfile() {
        if (aiAssistantConfig != null) {
            return aiAssistantConfig;
        }
        if (config == null) {
            return null;
        }
        Object raw = config.get("aiProfile");
        if (raw == null) {
            return null;
        }
        try {
            if (raw instanceof Map) {
                // 将嵌套 Map 转换为 SpiderAiProfile
                String json = JSON.toJSONString(raw);
                return JSON.parseObject(json, SpiderAiProfile.class);
            }
            if (raw instanceof String s && !s.isBlank()) {
                return JSON.parseObject(s, SpiderAiProfile.class);
            }
        } catch (Exception e) {
            log.warn("[SpiderFlowNode] nodeId={} 解析 config[\"aiProfile\"] 失败: {}", nodeId, e.getMessage());
        }
        return null;
    }
}

package com.chua.starter.ai.support.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * AI 对话请求模型。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class AiChatRequest {

    /**
     * 模型名称
     */
    private String model;

    /**
     * 用户输入
     */
    private String prompt;

    /**
     * 系统提示词
     */
    private String systemPrompt;

    /**
     * AI 角色编码（人设，如 java-dev/ui-designer），由后端解析为系统提示词
     */
    private String roleCode;

    /**
     * 采样温度
     */
    private Double temperature;

    /**
     * 最大生成 tokens
     */
    private Integer maxTokens;

    /**
     * 历史消息列表
     */
    private List<Map<String, Object>> messages;

    /**
     * 图片地址列表
     */
    private List<String> imageUrls;

    /**
     * 扩展参数
     */
    private Map<String, Object> parameters;

    /**
     * 是否流式输出
     */
    private Boolean stream;

    /**
     * 厂商工厂类型
     */
    private String factory;

    /**
     * 厂商提供者
     */
    private String provider;

    /**
     * 思考模式开关（透传 ChatClient，支持深度思考类模型）
     */
    private Boolean think;

    /**
     * 搜索开关（透传 ChatClient，支持联网搜索类模型）
     */
    private Boolean search;
}

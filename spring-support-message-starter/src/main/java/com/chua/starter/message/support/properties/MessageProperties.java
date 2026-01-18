package com.chua.starter.message.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 消息推送配置属性
 *
 * @author CH
 * @since 2024/12/26
 */
@Data
@ConfigurationProperties(prefix = MessageProperties.PREFIX)
public class MessageProperties {

    public static final String PREFIX = "plugin.message";

    /**
     * 是否启用消息推送
     */
    private boolean enable = false;

    /**
     * 默认渠道
     */
    private String defaultChannel;

    /**
     * 渠道配置
     * key: 渠道名称 (dingding, feishu, aliyun, email, tencent, etc.)
     * value: 渠道配置
     */
    private Map<String, ChannelProperties> channels = new LinkedHashMap<>();

    /**
     * 渠道配置
     */
    @Data
    public static class ChannelProperties {
        /**
         * 是否启用该渠道
         */
        private boolean enabled = true;

        /**
         * 渠道类型 (可选，默认使用key作为类型)
         */
        private String type;

        /**
         * 访问密钥ID
         */
        private String accessKeyId;

        /**
         * 访问密钥
         */
        private String accessKeySecret;

        /**
         * 端点/地址
         */
        private String endpoint;

        /**
         * Webhook URL (用于钉钉、飞书等)
         */
        private String webhookUrl;

        /**
         * 签名密钥 (用于钉钉加签)
         */
        private String signSecret;

        /**
         * 默认签名 (用于短信)
         */
        private String defaultSign;

        /**
         * 默认模板ID
         */
        private String defaultTemplateId;

        /**
         * 额外配置
         */
        private Map<String, String> extra = new LinkedHashMap<>();
    }
}

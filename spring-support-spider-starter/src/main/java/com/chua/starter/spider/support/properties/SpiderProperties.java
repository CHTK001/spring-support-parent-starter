package com.chua.starter.spider.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 爬虫模块配置。
 *
 * @author CH
 * @since 2026/04/02
 */
@Data
@ConfigurationProperties(prefix = SpiderProperties.PREFIX, ignoreInvalidFields = true)
public class SpiderProperties {

    public static final String PREFIX = "plugin.spider";

    /**
     * 是否启用爬虫 Spring 模块。
     */
    private boolean enable = true;

    /**
     * 默认大脑配置。
     */
    private BrainProperties brain = new BrainProperties();

    @Data
    public static class BrainProperties {

        /**
         * 是否启用默认大脑。
         */
        private boolean enable = false;

        /**
         * 是否接管爬虫流程。
         */
        private Boolean takeover = true;

        /**
         * 厂商标识，例如 siliconflow。
         */
        private String provider;

        /**
         * API Key。
         */
        private String apiKey;

        /**
         * 兼容 appKey 输入，底层会回落映射为 apiKey。
         */
        private String appKey;

        /**
         * 模型服务地址。
         */
        private String baseUrl;

        /**
         * 模型名称。
         */
        private String model;

        /**
         * 系统提示词。
         */
        private String systemPrompt;

        /**
         * 会话标识。
         */
        private String sessionId;

        /**
         * 凭据键。
         */
        private String credentialKey;

        /**
         * MCP 配置文件列表。
         */
        private List<String> mcpConfigPaths = new ArrayList<>();
    }
}

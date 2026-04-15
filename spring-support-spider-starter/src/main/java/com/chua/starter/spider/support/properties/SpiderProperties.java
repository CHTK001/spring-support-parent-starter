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
     * 平台层配置。
     */
    private PlatformProperties platform = new PlatformProperties();

    /**
     * 凭证加密配置。
     */
    private CredentialProperties credential = new CredentialProperties();

    /**
     * 是否初始化样例任务（仅开发/测试环境使用）。
     */
    private boolean initSample = false;

    /**
     * 默认大脑配置。
     */
    private BrainProperties brain = new BrainProperties();

    @Data
    public static class PlatformProperties {
        /**
         * 是否启用平台层（REST 接口、持久化、执行引擎等）。
         */
        private boolean enabled = true;
    }

    @Data
    public static class CredentialProperties {
        /**
         * AES 加密密钥（32 位字符串），用于凭证池密码加密存储。
         */
        private String aesKey = "spider-default-aes-key-32chars!!";
    }

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

package com.chua.com.chua.starter.unified.server.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static com.chua.com.chua.starter.unified.server.support.properties.UnifiedServerProperties.PRE;

/**
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = PRE, ignoreInvalidFields = true)
public class UnifiedServerProperties {

    public static final String PRE = "plugin.unified.server";

    /**
     * 加密模式
     */
    private String encryptionSchema = "aes";

    /**
     * 加密密钥
     */
    private String encryptionKey = "123456";
}

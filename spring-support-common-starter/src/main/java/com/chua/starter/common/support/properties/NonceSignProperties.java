package com.chua.starter.common.support.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;

/**
 * Nonce 签名配置（与前端 getConfig().secretKey 一致）
 * <p>
 * 支持多路径解析：plugin.nonce.sign.secret-key、nonce.sign.secret-key、环境变量 PLUGIN_NONCE_SIGN_SECRET_KEY
 * </p>
 *
 * @author CH
 * @since 2025/01/15
 */
@ConfigurationProperties(prefix = NonceSignProperties.PRE, ignoreInvalidFields = true)
@Getter
@Setter
public class NonceSignProperties {

    public static final String PRE = "plugin.nonce.sign";

    /** 备用前缀（解析失败时尝试） */
    public static final String PRE_ALT = "nonce.sign";

    /** 默认密钥占位符 */
    public static final String DEFAULT_SECRET = "your-secret-key-here";

    /** 签名密钥，需与前端 getConfig().secretKey 一致 */
    private String secretKey = DEFAULT_SECRET;

    /**
     * 从 Environment 重新解析密钥（多路径回退）
     *
     * @param env Environment
     * @return 解析到的密钥，无则返回当前 secretKey
     */
    public String resolveSecretKey(Environment env) {
        if (env == null) {
            return secretKey;
        }
        var key = env.getProperty(PRE + ".secret-key");
        if (key != null && !key.isBlank()) {
            return key;
        }
        key = env.getProperty(PRE + ".secretKey");
        if (key != null && !key.isBlank()) {
            return key;
        }
        key = env.getProperty(PRE_ALT + ".secret-key");
        if (key != null && !key.isBlank()) {
            return key;
        }
        key = env.getProperty("PLUGIN_NONCE_SIGN_SECRET_KEY");
        if (key != null && !key.isBlank()) {
            return key;
        }
        return secretKey;
    }
}


package com.chua.starter.common.support.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;

/**
 * Nonce 签名配置。
 *
 * <p>前缀 {@code plugin.nonce.sign}（备用前缀 {@code nonce.sign}）。签名密钥需与前端
 * 获取配置接口返回的 {@code secretKey} 保持一致，否则 nonce 校验将失败。</p>
 *
 * <p>密钥解析顺序（{@link #resolveSecretKey(Environment)}）：</p>
 * <ol>
 *     <li>{@code plugin.nonce.sign.secret-key}</li>
 *     <li>{@code plugin.nonce.sign.secretKey}</li>
 *     <li>{@code nonce.sign.secret-key}</li>
 *     <li>环境变量 {@code PLUGIN_NONCE_SIGN_SECRET_KEY}</li>
 *     <li>字段默认值（开发兜底 {@link #DEFAULT_SECRET}）</li>
 * </ol>
 *
 * <h3>配置示例</h3>
 * <pre>
 * plugin:
 *   nonce:
 *     sign:
 *       secret-key: ${NONCE_SIGN_SECRET:change-me}  # 生产从环境变量注入，禁止使用内置兜底值
 * </pre>
 *
 * @since 2025/01/15
 * @author CH
 */
@ConfigurationProperties(prefix = NonceSignProperties.PRE, ignoreInvalidFields = true)
@Getter
@Setter
public class NonceSignProperties {

    /**
     * PRE
    */
    public static final String PRE = "plugin.nonce.sign";

    /**
     * 备用前缀（解析失败时尝试）
    */
    public static final String PRE_ALT = "nonce.sign";

    /**
     * 默认签名密钥（开发兜底值，与前端默认密钥保持一致）。
     * 仅用于开发或未显式配置时的兜底，生产环境请通过配置覆盖。
     */
    public static final String DEFAULT_SECRET = "aP9xL3sV7mQ2zT5kB8nR1cY4wH6eD0fJ";

    /**
     * 签名密钥，需与前端获取配置接口返回的 {@code secretKey} 一致。
     * <p>YAML：{@code plugin.nonce.sign.secret-key}。字段初始化时优先读取环境变量
     * {@code PLUGIN_NONCE_SIGN_SECRET_KEY}，未设置时使用开发兜底值 {@link #DEFAULT_SECRET}；
     * 生产环境必须显式覆盖，禁止依赖内置默认密钥。</p>
     */
    private String secretKey = System.getenv("PLUGIN_NONCE_SIGN_SECRET_KEY") != null
        ? System.getenv("PLUGIN_NONCE_SIGN_SECRET_KEY") : DEFAULT_SECRET;

    /**
     * 从 环境 重新解析密钥（多路径回退）
     *
     * @param env 环境
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


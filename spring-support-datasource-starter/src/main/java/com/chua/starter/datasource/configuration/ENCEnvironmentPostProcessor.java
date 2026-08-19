package com.chua.starter.datasource.configuration;

import com.chua.starter.datasource.support.AesPasswordDecryptor;
import com.chua.starter.datasource.support.PasswordDecryptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ENC 配置加密处理器
 * <p>
 * 在 Spring 环境初始化阶段，扫描所有配置属性中的 {@code ENC(...)} 值并进行解密。
 * 支持数据源密码、URL、用户名等敏感信息的加密存储。
 * </p>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * spring.datasource.password: ENC(uX5z1H+z5f3k8L9mW2qR4w==)
 * spring.datasource.url: ENC(jdbc:mysql://...)        # 可选：加密URL
 *
 * # 自定义加密密钥
 * plugin.datasource.enc.secret-key: mySecretKey12345
 * }</pre>
 *
 * <h3>ENC 格式</h3>
 * <ul>
 *   <li>{@code ENC(base64EncryptedValue)} - 使用 AES 解密</li>
 *   <li>{@code ENC(AES:base64EncryptedValue)} - 指定 AES 算法</li>
 * </ul>
 *
 * @since 2026/07/14
 * @author CH
 */
@Slf4j
public class ENCEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /** ENC 正则：ENC(algorithm:value) 或 ENC(value) */
    /** Enc_pattern */
    private static final Pattern ENC_PATTERN = Pattern.compile(
            "^ENC\\((?:([A-Za-z0-9]+):)?(.+)\\)$");

    /** 需要解密的配置键后缀 */
    /** Sensitive_keys */
    private static final String[] SENSITIVE_KEYS = {
            "password", "pwd", "secret", "token", "apikey", "api-key", "api_key",
            "private-key", "private_key"
    };

    /** ENC 功能启用配置键 */
    /** Enc_enabled_prop */
    private static final String ENC_ENABLED_PROP = "plugin.datasource.enc.enabled";

    /** 密钥配置前缀 */
    /** Secret_key_prop */
    private static final String SECRET_KEY_PROP = "plugin.datasource.enc.secret-key";

    /** Decryptor */
    private PasswordDecryptor decryptor;

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // 检查 ENC 功能是否启用（默认关闭，需显式配置 plugin.datasource.enc.enabled=true）
        if (!Boolean.parseBoolean(environment.getProperty(ENC_ENABLED_PROP, "false"))) {
            log.debug("[ENC] 功能未启用，跳过 (设置 {} = true 启用)", ENC_ENABLED_PROP);
            return;
        }
        // 1. 初始化解密器
        this.decryptor = createDecryptor(environment);
        log.debug("[ENC] 初始化密码解密器: {}", decryptor.getClass().getSimpleName());

        // 2. 收集所有需要解密的属性
        Map<String, Object> decryptedProperties = new HashMap<>();
        MutablePropertySources propertySources = environment.getPropertySources();
        Iterator<PropertySource<?>> iterator = propertySources.iterator();

        while (iterator.hasNext()) {
            PropertySource<?> source = iterator.next();
            if (source instanceof MapPropertySource mapSource) {
                scanAndDecrypt(mapSource.getSource(), decryptedProperties);
            } else {
                // 通过反射尝试 EnumerablePropertySource（Spring 内部类，避免编译依赖）
                scanByReflection(source, decryptedProperties);
            }
        }

        // 3. 注册解密后的属性（高优先级）
        if (!decryptedProperties.isEmpty()) {
            propertySources.addFirst(
                    new MapPropertySource("enc-decrypted", decryptedProperties));
            log.info("[ENC] 已解密 {} 个配置项", decryptedProperties.size());
        }
    }

    /**
     * 创建密码解密器
     */
    private PasswordDecryptor createDecryptor(ConfigurableEnvironment environment) {
        String secretKey = environment.getProperty(SECRET_KEY_PROP);
        if (secretKey != null && !secretKey.isEmpty()) {
            log.info("[ENC] 使用自定义密钥（配置项: {}）", SECRET_KEY_PROP);
            return new AesPasswordDecryptor(secretKey);
        }
        log.warn("[ENC] 未配置自定义密钥 ({}), 使用默认密钥。请在生产环境中配置自定义密钥以提高安全性！",
                SECRET_KEY_PROP);
        return new AesPasswordDecryptor();
    }

    /**
     * 扫描 MapPropertySource 并解密 ENC 值
     */
    private void scanAndDecrypt(Map<String, Object> source, Map<String, Object> result) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof String strValue && isEncValue(strValue)) {
                String decrypted = resolveEncValue(strValue);
                result.put(key, decrypted);
            }
        }
    }

    /**
     * 通过反射扫描 EnumerablePropertySource（如 .properties 文件）
     * <p>
     * Spring 的 {@code EnumerablePropertySource} 提供了 {@code getPropertyNames()} 方法，
     * 但为了避免编译期依赖，通过反射调用。
     * </p>
     */
    private void scanByReflection(PropertySource<?> source, Map<String, Object> result) {
        try {
            Class<?> enumerableClass = Class.forName("org.springframework.core.env.EnumerablePropertySource");
            if (!enumerableClass.isInstance(source)) {
                return;
            }
            java.lang.reflect.Method getNamesMethod = source.getClass().getMethod("getPropertyNames");
            java.lang.reflect.Method getPropMethod = source.getClass().getMethod("getProperty", String.class);
            String[] names = (String[]) getNamesMethod.invoke(source);
            if (names == null) {
                return;
            }
            for (String name : names) {
                Object value = getPropMethod.invoke(source, name);
                if (value instanceof String strValue && isEncValue(strValue)) {
                    String decrypted = resolveEncValue(strValue);
                    result.put(name, decrypted);
                }
            }
        } catch (Exception e) {
            log.debug("[ENC] 跳过 PropertySource {}: {}", source.getName(), e.getMessage());
        }
    }

    /**
     * 判断值是否为 ENC 加密格式
     */
    static boolean isEncValue(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        return value.startsWith("ENC(") && value.endsWith(")");
    }

    /**
     * 解析并解密 ENC 值
     *
     * @param encValue ENC 格式的值
     * @return 解密后的明文
     */
    String resolveEncValue(String encValue) {
        if (!isEncValue(encValue)) {
            return encValue;
        }

        Matcher matcher = ENC_PATTERN.matcher(encValue.trim());
        if (!matcher.matches()) {
            log.warn("[ENC] 无法解析格式: {}", encValue);
            return encValue;
        }

        String algorithm = matcher.group(1);
        String encryptedContent = matcher.group(2);

        if (algorithm != null && !algorithm.isEmpty() && !decryptor.supports(algorithm)) {
            log.warn("[ENC] 不支持的加密算法: {}, 返回原始值", algorithm);
            return encValue;
        }

        try {
            String decrypted = decryptor.decrypt(encryptedContent);
            log.debug("[ENC] 解密成功 (算法: {})", algorithm != null ? algorithm : "AES");
            return decrypted;
        } catch (Exception e) {
            log.error("[ENC] 解密失败, 返回原始值: {}", e.getMessage());
            return encValue;
        }
    }

    /**
     * 检查配置键是否敏感（需要优先解密）
     */
    @SuppressWarnings("unused")
    private boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String lowerKey = key.toLowerCase();
        for (String suffix : SENSITIVE_KEYS) {
            if (lowerKey.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getOrder() {
        // 在所有 ConfigData 处理完成后执行（高优先级确保在绑定前解密）
        return ConfigDataEnvironmentPostProcessor.ORDER + 50;
    }

    // EnumerablePropertySource 通过反射访问，避免编译期依赖
}

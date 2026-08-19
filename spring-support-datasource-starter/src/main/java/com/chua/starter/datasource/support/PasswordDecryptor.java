package com.chua.starter.datasource.support;

/**
 * 密码解密器接口
 * <p>
 * 用于解密 {@code ENC(encryptedValue)} 格式的加密配置。
 * 可通过 SPI 机制扩展自定义实现。
 * </p>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * # 配置文件中的加密密码
 * spring.datasource.password: ENC(uX5z1H+z5f3k8L9mW2qR4w==)
 * }</pre>
 *
 * <h3>扩展</h3>
 * <pre>{@code
 * public class MyCustomDecryptor implements PasswordDecryptor {
 *     \@Override
 *     public boolean supports(String algorithm) {
 *         return "custom".equals(algorithm);
 *     }
 *     \@Override
 *     public String decrypt(String encryptedValue) {
 *         // 自定义解密逻辑
 *     }
 * }
 * }</pre>
 *
 * @since 2026/07/14
 * @author CH
 */
@FunctionalInterface
public interface PasswordDecryptor {

    /**
     * 解密加密的值
     *
     * @param encryptedValue 加密后的字符串（不包含 ENC() 包裹）
     * @return 解密后的明文
     * @throws IllegalArgumentException 解密失败时抛出
     */
    String decrypt(String encryptedValue);

    /**
     * 是否支持指定的加密算法
     *
     * @param algorithm 算法标识，默认从 {@code ENC(algorithm:value)} 中提取
     * @return 是否支持
     */
    default boolean supports(String algorithm) {
        return true;
    }

    /**
     * 获取解密器的优先级（数值越小优先级越高）
     *
     * @return 优先级
     */
    default int getOrder() {
        return 0;
    }
}

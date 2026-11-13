package com.chua.starter.common.support.algorithm.crypto;

import com.chua.common.support.spi.ServiceProvider;

import java.util.Locale;

/**
 * 文本加解密 SPI。
 *
 * <p>所有算法实现都通过 {@code META-INF/extensions/} 注册，按类型名解析：
 * {@code aes}、{@code sm4}、{@code des} 是真实加解密，{@code hex} 只是十六进制编码
 * （仅用于未配置密钥的兼容路径）。<b>不认识的类型直接抛异常，不再静默退化成 hex</b>。</p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * Codec codec = Codec.build("sm4", "1234567890abcdef");
 * String cipher = codec.encodeHex("明文");
 * String plain = codec.decodeHex(cipher);
 * }</pre>
 *
 * @author CH
 * @since 4.0.0
 */
public interface Codec {

    /**
     * 绑定密钥，返回可用的编解码器。
     *
     * @param key 密钥原文（对称算法）或公钥十六进制（SM2 不走本接口）
     * @return 绑定密钥后的编解码器
     */
    Codec key(String key);

    /**
     * 编码为十六进制字符串。
     *
     * @param data 明文
     * @return 十六进制密文
     */
    String encodeHex(String data);

    /**
     * 解码十六进制字符串。
     *
     * @param hex 十六进制密文
     * @return 明文
     */
    String decodeHex(String hex);

    /**
     * 按类型解析实现。
     *
     * @param type 类型名，如 aes / sm4 / des / hex
     * @param key  密钥
     * @return 编解码器
     * @throws IllegalArgumentException 类型不支持或密钥为空
     */
    static Codec build(String type, String key) {
        String name = type == null || type.isBlank() ? "aes" : type.trim().toLowerCase(Locale.ROOT);
        ServiceProvider<Codec> provider = ServiceProvider.of(Codec.class);
        if (!provider.isSupport(name)) {
            throw new IllegalArgumentException("不支持的加解密类型: " + type);
        }
        Codec codec = provider.getNewExtension(name);
        if (codec == null) {
            throw new IllegalArgumentException("加解密实现加载失败: " + type);
        }
        return codec.key(key);
    }
}

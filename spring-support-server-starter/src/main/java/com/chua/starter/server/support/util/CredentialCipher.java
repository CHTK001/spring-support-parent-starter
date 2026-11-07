package com.chua.starter.server.support.util;

import com.chua.starter.common.support.crypto.AesCodec;
import com.chua.starter.server.support.config.ServerProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 凭据加解密器
 * <p>
 * 基于 AES-CBC 对密钥私钥、登录密码进行加密落库与解密还原，
 * 密钥来源于 {@code plugin.server.support.credential-key} 配置。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Component
public class CredentialCipher {

    /**
     * 编解码器
    */
    private final AesCodec codec;

    /**
     * 构造注入（密钥取自服务属性）
     *
     * @param properties 服务属性
     */
    public CredentialCipher(ServerProperties properties) {
        String key = properties == null ? null : properties.getCredentialKey();
        this.codec = new AesCodec((key == null ? "" : key).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 加密明文
     *
     * @param plain 明文
     * @return 密文（hex），入参为空时原样返回
     */
    public String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) {
            return plain;
        }
        return codec.encodeHex(plain);
    }

    /**
     * 解密密文
     *
     * @param hex 密文（hex）
     * @return 明文，入参为空时原样返回
     */
    public String decrypt(String hex) {
        if (hex == null || hex.isEmpty()) {
            return hex;
        }
        try {
            return codec.decodeHex(hex);
        } catch (Exception e) {
            /**
             * illegal状态异常。
             * @param 配置是否与加密时一致" 配置是否与加密时一致"
             * @param e e
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("凭据解密失败：当前环境的加密密钥与写入时不一致，请重新填写该账号口令", e);
        }
    }
}

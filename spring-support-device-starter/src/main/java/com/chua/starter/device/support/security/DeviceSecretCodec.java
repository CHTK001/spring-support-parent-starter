package com.chua.starter.device.support.security;

import com.chua.starter.common.support.crypto.AesCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

/**
 * 设备密钥编解码器
 *
 * <p>设备密码、厂商 AppSecret 等敏感信息落库前加密，读取后按需解密。
 * 使用 {@code device.security.secret-key} 配置密钥；未配置时保持明文（兼容既有部署），
 * 但会在启动日志中提示风险。</p>
 *
 * <p>加密值带 {@code enc:} 前缀，用于区分历史明文与密文，支持平滑迁移：
 * 旧数据继续可读，新写入的数据自动加密。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Component
public class DeviceSecretCodec {

    /**
     * 密文前缀
     */
    private static final String PREFIX = "enc:";

    /**
     * AES 编解码器；密钥未配置时为 {@code null}
     */
    private final AesCodec codec;

    /**
     * 创建编解码器
     *
     * @param secretKey 密钥，来自 {@code device.security.secret-key}
     */
    public DeviceSecretCodec(@Value("${device.security.secret-key:}") String secretKey) {
        if (!StringUtils.hasText(secretKey)) {
            this.codec = null;
            log.warn("[Device] 未配置 device.security.secret-key，设备密码/厂商 SK 将以明文存储；生产环境请务必配置");
        } else {
            this.codec = new AesCodec(secretKey.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * 加密
     *
     * @param plain 明文
     * @return 带前缀的密文；空值或未启用加密时原样返回
     */
    public String encrypt(String plain) {
        if (!StringUtils.hasText(plain) || isEncrypted(plain) || codec == null) {
            return plain;
        }
        return PREFIX + codec.encodeHex(plain);
    }

    /**
     * 解密
     *
     * @param value 明文或带前缀的密文
     * @return 明文；空值返回 {@code null}
     * @throws IllegalStateException 数据已加密但未配置密钥
     */
    public String decrypt(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        if (!isEncrypted(value)) {
            // 历史明文数据：保持可读，下次写入时自然转为密文
            return value;
        }
        if (codec == null) {
            throw new IllegalStateException(
                    "检测到加密的设备密钥，但未配置 device.security.secret-key，无法解密");
        }
        return codec.decodeHex(value.substring(PREFIX.length()));
    }

    /**
     * 是否为密文
     *
     * @param value 值
     * @return true 表示带加密前缀
     */
    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    /**
     * 是否为前端回传的掩码（例如 {@code ab****cd}）
     * <p>编辑场景里掩码表示“不修改”，服务层应保留原值。</p>
     *
     * @param value 值
     * @return true 表示掩码
     */
    public boolean isMasked(String value) {
        return value != null && value.contains("****");
    }
}

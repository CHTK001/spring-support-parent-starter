package com.chua.starter.pay.support.callback;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * AES工具类
 *
 * @author CH
 * @since 2023-09-05
 */
@Slf4j
public class AesUtil {

    /**
     * 密钥长度（字节）
     */
    static final int KEY_LENGTH_BYTE = 32;

    /**
     * 标签长度（位）
     */
    static final int TAG_LENGTH_BIT = 128;

    /**
     * AES密钥
     */
    private final byte[] aesKey;

    /**
     * 构造函数
     *
     * @param key APIv3密钥，长度必须为32字节
     */
    public AesUtil(byte[] key) {
        if (key.length != KEY_LENGTH_BYTE) {
            throw new IllegalArgumentException("无效的ApiV3Key，长度必须为32个字节");
        }
        this.aesKey = key;
    }

    /**
     * 解密数据
     *
     * @param associatedData 关联数据，用于验证数据完整性
     * @param nonce          随机数，确保每次加密结果不同
     * @param ciphertext     待解密的密文，Base64编码格式
     * @return 解密后的明文字符串
     * @throws GeneralSecurityException 安全相关异常
     * @throws IOException              IO异常
     */
    public String decryptToString(byte[] associatedData, byte[] nonce, String ciphertext)
            throws GeneralSecurityException, IOException {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec key = new SecretKeySpec(aesKey, "AES");
            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH_BIT, nonce);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);
            cipher.updateAAD(associatedData);
            return new String(cipher.doFinal(Base64.getDecoder().decode(ciphertext)), StandardCharsets.UTF_8);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new IllegalStateException("不支持的加密算法", e);
        } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
            throw new IllegalArgumentException("无效的密钥或参数", e);
        }
    }
}
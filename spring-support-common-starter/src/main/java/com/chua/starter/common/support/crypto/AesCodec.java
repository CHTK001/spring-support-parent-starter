package com.chua.starter.common.support.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * AES 编解码器
 *
 * @author CH
 */
public class AesCodec {

    /** 密钥bytes */
    private final byte[] keyBytes;

    public AesCodec(byte[] keyBytes) {
        this.keyBytes = keyBytes;
    }

    public String encodeHex(String data) {
        if (data == null) return null;
        try {
            byte[] bytes = data.getBytes("UTF-8");
            return bytesToHex(encrypt(bytes));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String decodeHex(String hex) {
        if (hex == null) return null;
        try {
            return new String(decrypt(hexToBytes(hex)), "UTF-8");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] encrypt(byte[] plain) throws Exception {
        SecretKeySpec keySpec = generateKey();
        IvParameterSpec iv = generateIv();
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, iv);
        return cipher.doFinal(plain);
    }

    private byte[] decrypt(byte[] encrypted) throws Exception {
        SecretKeySpec keySpec = generateKey();
        IvParameterSpec iv = generateIv();
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, iv);
        return cipher.doFinal(encrypted);
    }

    private SecretKeySpec generateKey() throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(keyBytes);
        return new SecretKeySpec(md.digest(), "AES");
    }

    private IvParameterSpec generateIv() throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(keyBytes);
        // AES/CBC 要求 IV 必须为 16 字节，取 SHA-256 摘要前 16 字节
        return new IvParameterSpec(Arrays.copyOf(md.digest(), 16));
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}

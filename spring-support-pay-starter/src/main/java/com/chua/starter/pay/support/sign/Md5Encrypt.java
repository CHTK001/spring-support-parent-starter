package com.chua.starter.pay.support.sign;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * @Description:
 * @Author: yun-jiao
 * @Date: 2020/4/30 11:06
 **/
public class Md5Encrypt {
    private static final char[] DIGITS = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    public Md5Encrypt() {
    }

    public static String md5(String text) {
        MessageDigest msgDigest = null;

        try {
            msgDigest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException var5) {
            throw new IllegalStateException("System doesn't support MD5 algorithm.");
        }

        try {
            msgDigest.update(text.getBytes("utf-8"));
        } catch (UnsupportedEncodingException var4) {
            throw new IllegalStateException("System doesn't support your  EncodingException.");
        }

        byte[] bytes = msgDigest.digest();
        String md5Str = new String(encodeHex(bytes));
        return md5Str;
    }

    public static char[] encodeHex(byte[] data) {
        int l = data.length;
        char[] out = new char[l << 1];
        int i = 0;

        for (int var4 = 0; i < l; ++i) {
            out[var4++] = DIGITS[(240 & data[i]) >>> 4];
            out[var4++] = DIGITS[15 & data[i]];
        }

        return out;
    }

    /**
     * MD5 32位小写加密
     *
     * @param encryptStr
     * @return
     */
    public static String encrypt32(String encryptStr) {
        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
            byte[] md5Bytes = md5.digest(encryptStr.getBytes());
            StringBuffer hexValue = new StringBuffer();
            for (int i = 0; i < md5Bytes.length; i++) {
                int val = ((int) md5Bytes[i]) & 0xff;
                if (val < 16) {
                    hexValue.append("0");
                }
                hexValue.append(Integer.toHexString(val));
            }
            encryptStr = hexValue.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return encryptStr;
    }

    public static String md5Auth(String text) {
        MessageDigest msgDigest = null;

        try {
            msgDigest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "System doesn't support MD5 algorithm.");
        }

        try {
            msgDigest.update(text.getBytes("utf-8"));

        } catch (UnsupportedEncodingException e) {

            throw new IllegalStateException(
                    "System doesn't support your  EncodingException.");

        }

        byte[] bytes = msgDigest.digest();

        String md5Str = new String(encodeHex(bytes));

        return md5Str.toUpperCase();
    }

    public static String Aes256(String keyString, String encryptedText) {
        // 将密钥字符串转换为字节数组
        try {
            byte[] keyBytes = keyString.getBytes("UTF-8");

            // 使用密钥字节数组构建密钥对象
            SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, "AES");

            // 创建 AES 加密算法实例
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");

            // 使用解密模式初始化加密算法实例
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);

            // 将 Base64 编码的加密文本解码为字节数组
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedText);

            // 使用 AES 解密算法解密数据
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);

            // 将解密后的字节数组转换为字符串
            String decryptedText = new String(decryptedBytes, "UTF-8");
            return decryptedText;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String encodeAes256(String keyString, String plaintext) {
        // 将密钥字符串转换为字节数组
        try {
            // 将密钥字符串转换为字节数组
            byte[] keyBytes = keyString.getBytes("UTF-8");

            // 使用密钥字节数组构建密钥对象
            SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, "AES");

            // 创建 AES 加密算法实例
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");

            // 使用加密模式初始化加密算法实例
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);

            // 将明文字符串转换为字节数组
            byte[] plaintextBytes = plaintext.getBytes("UTF-8");

            // 使用 AES 加密算法加密数据
            byte[] encryptedBytes = cipher.doFinal(plaintextBytes);

            // 将加密后的字节数组进行 Base64 编码
            String encryptedText = Base64.getEncoder().encodeToString(encryptedBytes);

            return encryptedText;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }


    }

    public static String encryptMD5(String input) {
        try {
            // 创建 MessageDigest 实例并指定使用 MD5 算法
            MessageDigest md = MessageDigest.getInstance("MD5");

            // 将输入字符串转换为字节数组
            byte[] inputBytes = input.getBytes();

            // 计算MD5摘要
            byte[] hashBytes = md.digest(inputBytes);

            // 将摘要转换为十六进制字符串
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();

        } catch (NoSuchAlgorithmException e) {
            // 处理异常
            e.printStackTrace();
            return null;
        }
    }

}

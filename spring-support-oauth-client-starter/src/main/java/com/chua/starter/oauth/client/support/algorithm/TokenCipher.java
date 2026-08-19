package com.chua.starter.oauth.client.support.algorithm;

import com.chua.common.support.lang.algorithm.cipher.Sm4Cipher;
import com.chua.common.support.lang.algorithm.crypto.Hex;
import com.chua.common.support.lang.json.Json;
import com.chua.starter.oauth.client.support.execute.AuthClientExecute;
import com.chua.starter.oauth.client.support.user.UserResult;

import java.nio.charset.StandardCharsets;

/**
 * 令牌编解码工具
 * <p>统一使用 SM4/ECB + Hex 对用户信息进行加密与解密，
 * 保证登录侧（SysLoginServiceImpl）、静态鉴权侧（StaticProtocol）与登录执行器（StaticProtocolExecutor）
 * 生成和解析 token 的算法完全一致，避免各实现各自维护导致的不兼容问题。</p>
 *
 * @since 4.0.0.42
 */
public final class TokenCipher {

    /**
     * SM4 加密密钥，与登录侧保持一致
     */
    private static final byte[] KEY = AuthClientExecute.DEFAULT_KEY.getBytes(StandardCharsets.UTF_8);

    /**
     * SM4 加密解密器（线程安全、单例初始化）
     */
    private static volatile Sm4Cipher SM4;

    /**
     * 私有构造函数，防止实例化
     */
    private TokenCipher() {
    }

    /**
     * 获取 SM4 加密解密器
     *
     * @return SM4 加密解密器
     */
    private static Sm4Cipher cipher() {
        if (null == SM4) {
            synchronized (TokenCipher.class) {
                if (null == SM4) {
                    SM4 = Sm4Cipher.create("bc");
                }
            }
        }
        return SM4;
    }

    /**
     * 加密用户信息为 token
     *
     * @param userResult 用户信息
     * @return 加密后的 token，失败返回 null
     */
    public static String encrypt(UserResult userResult) {
        if (null == userResult) {
            return null;
        }
        return encrypt(Json.toJson(userResult));
    }

    /**
     * 加密明文为 token
     *
     * @param plainText 明文
     * @return 加密后的 token，失败返回 null
     */
    public static String encrypt(String plainText) {
        if (null == plainText) {
            return null;
        }
        try {
            return Hex.encodeHexString(cipher().encrypt(KEY, plainText.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解密 token 为明文
     *
     * @param hexToken 加密后的 token
     * @return 解密后的明文，失败返回 null
     */
    public static String decrypt(String hexToken) {
        if (null == hexToken || hexToken.isEmpty()) {
            return null;
        }
        try {
            return new String(cipher().decrypt(KEY, Hex.decodeHex(hexToken)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解密 token 为用户信息
     *
     * @param hexToken 加密后的 token
     * @return 用户信息，解密或解析失败返回 null
     */
    public static UserResult decryptToUser(String hexToken) {
        String source = decrypt(hexToken);
        if (null == source) {
            return null;
        }
        try {
            return Json.fromJson(source, UserResult.class);
        } catch (Exception e) {
            return null;
        }
    }
}

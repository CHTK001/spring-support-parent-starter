package com.chua.starter.oauth.client.support.entity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 应用请求 HMAC 契约。字段以 UTF-8 字节长度分隔，保留查询参数顺序与重复值。
 * @author CH
 */
public final class AppRequestSignature {
    private AppRequestSignature() {
    }

    /**
     * 生成 v1 签名；调用端与认证端共享同一规范化实现。
     * @param key 应用标识
     * @param secret 应用密钥
     * @param method 原业务请求方法
     * @param path 原业务请求 URI 路径
     * @param query 未解码的原始查询串，无问号
     * @param body 原始业务请求体
     * @param time 毫秒时间戳
     * @param nonce 一次性随机值
     * @return 小写十六进制 HMAC-SHA256
     */
    public static String sign(String key, String secret, String method, String path,
                              String query, String body, String time, String nonce) {
        if (key == null || key.isBlank() || secret == null || secret.isBlank()
                || method == null || !method.matches("[A-Za-z]+")
                || path == null || !path.startsWith("/") || path.contains("?") || path.contains("#")
                || time == null || nonce == null || !nonce.matches("[A-Za-z0-9_-]{16,128}")) {
            throw new IllegalArgumentException("应用签名参数不完整或格式错误");
        }
        try {
            String bodyHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((body == null ? "" : body).getBytes(StandardCharsets.UTF_8)));
            String canonical = "oauth-app-v1" + field(key) + field(method.toUpperCase(Locale.ROOT))
                    + field(path) + field(query == null ? "" : query) + field(bodyHash)
                    + field(time) + field(nonce);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("应用签名算法不可用", exception);
        }
    }

    /** @param value 字段值 @return 具有无歧义字节长度前缀的字段 */
    private static String field(String value) {
        return ":" + value.getBytes(StandardCharsets.UTF_8).length + ":" + value;
    }
}

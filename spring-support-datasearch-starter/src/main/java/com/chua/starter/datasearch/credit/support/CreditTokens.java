package com.chua.starter.datasearch.credit.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 余额令牌解析工具：从 JWT 形态的令牌里解出过期时间。
 *
 * <p>余额令牌的「有效期」判定只认 JWT 的 {@code exp} 声明（秒级时间戳，按 UTC）：
 * payload 是 base64url 编码的 JSON，无需验签（我们只读声明，不校验真伪——令牌
 * 真伪由上游接口的查询结果说话）。非 JWT 令牌（如 Moss 的不透明 Cookie 值）解不出
 * {@code exp}，返回 null，调用方按「无到期时间」口径走周期保活回访。</p>
 *
 * <p>解析全程容错：格式不对、字段缺失、类型异常都返回 null，绝不抛异常——
 * 续期调度里它是每轮都要跑的旁路判定，不能因为单个怪令牌打断整个循环。</p>
 *
 * @author CH
 * @since 4.0.0.61
 */
public final class CreditTokens {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CreditTokens() {
    }

    /**
     * 解 JWT 形态令牌的过期时间。
     *
     * <p>结构必须是 {@code header.payload.signature} 三段式，payload 为 base64url
     * （无填充也兼容）编码的 JSON，且含数值型 {@code exp}（秒）。exp 按秒 ×1000 转
     * 毫秒时间戳。任何一步不满足都返回 null。</p>
     *
     * @param token 余额令牌（可为 null / 空串）
     * @return 过期时间（epoch 毫秒）；非 JWT 或无 exp 声明时为 null
     */
    public static Long expireAtMs(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String trimmed = token.trim();
        String[] parts = trimmed.split("\\.");
        if (parts.length != 3) {
            return null;
        }
        JsonNode payload = decodePayload(parts[1]);
        if (payload == null) {
            return null;
        }
        JsonNode exp = payload.get("exp");
        if (exp == null || !exp.isNumber()) {
            return null;
        }
        long seconds = exp.asLong();
        if (seconds <= 0) {
            return null;
        }
        // 防御性上限：exp 合理范围在 2001-09-09（1e9）之后，避免把毫秒误当秒解析出天文数字
        if (seconds < 1_000_000_000L) {
            return null;
        }
        return seconds * 1000L;
    }

    /**
     * 解码 JWT payload 段并解析成 JSON 节点。
     *
     * @param segment base64url 编码的 payload 段
     * @return JSON 节点；解码或解析失败为 null
     */
    private static JsonNode decodePayload(String segment) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(padBase64Url(segment));
            String json = new String(bytes, StandardCharsets.UTF_8);
            return MAPPER.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 补齐 base64url 省略的 {@code =} 填充（JDK 解码器要求 padding）。
     *
     * @param segment 原始段
     * @return 长度为 4 的倍数的段
     */
    private static String padBase64Url(String segment) {
        int remainder = segment.length() % 4;
        if (remainder == 0) {
            return segment;
        }
        return segment + "=".repeat(4 - remainder);
    }
}

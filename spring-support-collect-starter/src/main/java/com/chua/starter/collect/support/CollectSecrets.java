package com.chua.starter.collect.support;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 敏感字段编解码。
 *
 * <p>数据源密码与加密变量落库时写成 {@code b64:} 前缀的 Base64，读取时还原明文；
 * 没有前缀的值按明文处理，避免"恰好是合法 Base64 的明文密码"被误解码。
 * 接口读回时下发 {@link #MASK} 占位，前端原样提交占位值表示不修改该项。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class CollectSecrets {

    /**
     * 下发给前端的占位值。
     */
    public static final String MASK = "******";

    /**
     * 存储值前缀，标识其后是 Base64。
     */
    private static final String PREFIX = "b64:";

    /**
     * 工具类禁止实例化。
     */
    private CollectSecrets() {
    }

    /**
     * 明文转存储值。
     *
     * @param plain 明文
     * @return 带前缀的存储值，空明文返回空串
     */
    public static String encode(String plain) {
        if (plain == null || plain.isEmpty()) {
            return "";
        }
        if (plain.startsWith(PREFIX)) {
            return plain;
        }
        return PREFIX + Base64.getEncoder().encodeToString(plain.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 存储值转明文，无前缀时原样返回。
     *
     * @param stored 存储值
     * @return 明文
     */
    public static String decode(String stored) {
        if (stored == null || stored.isBlank()) {
            return "";
        }
        String text = stored.trim();
        if (!text.startsWith(PREFIX)) {
            return text;
        }
        try {
            return new String(Base64.getDecoder().decode(text.substring(PREFIX.length())), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return text;
        }
    }

    /**
     * 是否为占位值。
     *
     * @param text 前端提交值
     * @return 是占位返回 true
     */
    public static boolean isMasked(String text) {
        return text != null && MASK.equals(text.trim());
    }
}

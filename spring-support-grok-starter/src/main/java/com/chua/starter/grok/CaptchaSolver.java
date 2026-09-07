package com.chua.starter.grok;

/**
 * Turnstile 验证码求解器接口。
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface CaptchaSolver {

    /**
     * 求解 Turnstile 验证码。
     *
     * @param siteUrl  网站 URL
     * @param siteKey  Turnstile site key
     * @param proxyUrl 代理地址（可选）
     * @return Turnstile token；失败返回 null
     */
    String solve(String siteUrl, String siteKey, String proxyUrl);
}

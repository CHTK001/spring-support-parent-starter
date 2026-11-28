package com.chua.starter.sso.support;

import com.xkcoding.http.config.HttpConfig;

/**
 * 三方授权 HTTP 参数
 *
 * <p>JustAuth 的 {@code AuthConfig} 未携带 {@code httpConfig} 时，simple-http 在
 * {@code HttpUtil.setConfig(null)} 里按 {@code HttpConfig.builder().timeout(3000)} 兜底，
 * 连接超时只有 3 秒。跨网、经代理访问 GitHub 的 TLS 建连实测在 1.4~2.8 秒且抖动，
 * 3 秒档位会随机抛 {@code HTTP connect timed out}，整段授权码换票失败，页面表现为
 * 「Github授权失败」。</p>
 *
 * <p>代理不在此显式配置：simple-http 未设置显式 {@code proxy} 时，
 * JDK {@code java.net.http.HttpClient} 使用 {@code ProxySelector.getDefault()}，
 * 即 JVM 的 {@code -Dhttps.proxyHost/-Dhttps.proxyPort}（并遵守 {@code http.nonProxyHosts}）；
 * 反过来显式塞 {@code proxy} 会绕过 {@code http.nonProxyHosts}，把内网地址也推给代理。</p>
 *
 * @since 2026/11/27
 * @author CH
 */
public final class ThirdPartyHttpSupport {

    /**
     * 连接超时覆盖参数名（毫秒）
     */
    private static final String TIMEOUT_PROPERTY = "plugin.oauth.sso.http-timeout";

    /**
     * 缺省连接超时（毫秒）
     */
    private static final int DEFAULT_TIMEOUT_MILLIS = 15000;

    /**
     * 连接超时下限（毫秒）
     */
    private static final int MIN_TIMEOUT_MILLIS = 3000;

    private ThirdPartyHttpSupport() {
    }

    /**
     * 构造三方授权（授权码换票、拉取用户信息）使用的 HTTP 参数
     *
     * @return 带连接超时的 HTTP 参数
     */
    public static HttpConfig httpConfig() {
        return HttpConfig.builder().timeout(timeoutMillis()).build();
    }

    /**
     * 解析连接超时
     *
     * <p>取 JVM 参数 {@code -Dplugin.oauth.sso.http-timeout=<毫秒>}，未配置或配置过小时回落缺省值。</p>
     *
     * @return 连接超时（毫秒）
     */
    private static int timeoutMillis() {
        return Math.max(MIN_TIMEOUT_MILLIS, Integer.getInteger(TIMEOUT_PROPERTY, DEFAULT_TIMEOUT_MILLIS));
    }
}

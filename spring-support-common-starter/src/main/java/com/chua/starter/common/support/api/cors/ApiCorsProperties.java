package com.chua.starter.common.support.api.cors;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashSet;
import java.util.Set;

/**
 * API跨域配置属性
 * <p>
 * 用于配置跨域请求的相关参数。
 * </p>
 *
 * <h3>配置示例</h3>
 * <pre>
 * plugin:
 *   api:
 *     cors:
 *       enable: true           # 是否开启跨域
 *       pattern:               # 跨域路径匹配规则
 *         - /api/**
 *         - /v2/**
 * </pre>
 *
 * @since 2024/12/07
 * @author CH
 */
@ConfigurationProperties(prefix = "plugin.api.cors", ignoreInvalidFields = true)
public class ApiCorsProperties {

    /**
     * 开启跨域
     */
    private boolean enable = false;

    /**
     * 跨域路径白名单
     * <p>
     * 如果为空，默认匹配所有路径 /**
     * </p>
     */
    private Set<String> pattern = new HashSet<>();

    /**
     * 允许的来源域名白名单
     * <p>
     * 为空时<b>不放行任何跨域来源</b>（同源访问不受影响）。
     * 这一项必须与 allowCredentials 成对考虑：一旦 allowCredentials=true，
     * 浏览器就会把带凭证的跨域响应交给脚本，此时来源白名单是唯一防线。
     * </p>
     * <p>
     * 不要填 "*"：Spring 的 allowedOriginPatterns("*") 会把请求里的 Origin
     * 原样反射回 Access-Control-Allow-Origin，配合 allowCredentials=true
     * 等于任意网站都能带着用户凭证读接口响应（2026-10-01 实测确认该行为）。
     * </p>
     * <p>支持端口通配，形如 {@code http://192.168.50.*:8096}（Spring 原生支持）。</p>
     */
    private Set<String> allowedOrigins = new HashSet<>();

    /**
     * 是否允许跨域请求携带凭证（Cookie / Authorization）
     * <p>
     * 默认 false。打开前必须确认 {@link #allowedOrigins} 已配好白名单。
     * </p>
     */
    private boolean allowCredentials = false;

    /**
     * 获取 enable
     *
     * @return enable
     */
    public boolean getEnable() {
        return enable;
    }

    /**
     * 设置 enable
     *
     * @param enable enable
     */
    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    /**
     * 获取 模式
     *
     * @return pattern
     */
    public Set<String> getPattern() {
        return pattern;
    }

    /**
     * 设置 模式
     *
     * @param pattern 模式
     */
    public void setPattern(Set<String> pattern) {
        this.pattern = pattern;
    }

    /**
     * 获取允许的来源域名白名单
     *
     * @return 来源白名单
     */
    public Set<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    /**
     * 设置允许的来源域名白名单
     *
     * @param allowedOrigins 来源白名单
     */
    public void setAllowedOrigins(Set<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    /**
     * 是否允许跨域携带凭证
     *
     * @return 是否允许携带凭证
     */
    public boolean isAllowCredentials() {
        return allowCredentials;
    }

    /**
     * 设置是否允许跨域携带凭证
     *
     * @param allowCredentials 是否允许携带凭证
     */
    public void setAllowCredentials(boolean allowCredentials) {
        this.allowCredentials = allowCredentials;
    }
}

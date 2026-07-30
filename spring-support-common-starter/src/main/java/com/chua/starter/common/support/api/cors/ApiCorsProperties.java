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
 * @author CH
 * @version 1.0.0
 * @since 2024/12/07
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
     * 获取 pattern
     *
     * @return pattern
     */
    public Set<String> getPattern() {
        return pattern;
    }

    /**
     * 设置 pattern
     *
     * @param pattern pattern
     */
    public void setPattern(Set<String> pattern) {
        this.pattern = pattern;
    }
}

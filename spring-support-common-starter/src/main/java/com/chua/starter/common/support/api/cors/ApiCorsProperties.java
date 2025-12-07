package com.chua.starter.common.support.api.cors;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashSet;
import java.util.Set;

/**
 * API跨域配置属�?
 * <p>
 * 用于配置跨域请求的相关参数�?
 * </p>
 *
 * <h3>配置示例�?/h3>
 * <pre>
 * plugin:
 *   api:
 *     cors:
 *       enable: true           # 是否开启跨�?
 *       pattern:               # 跨域路径匹配规则
 *         - /api/**
 *         - /v2/**
 * </pre>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/07
 */
@Data
@ConfigurationProperties(prefix = "plugin.api.cors", ignoreInvalidFields = true)
public class ApiCorsProperties {

    /**
     * 开启跨�?
     */
    private boolean enable = true;

    /**
     * 跨域路径白名�?
     * <p>
     * 如果为空，默认匹配所有路�?/**
     * </p>
     */
    private Set<String> pattern = new HashSet<>();
}


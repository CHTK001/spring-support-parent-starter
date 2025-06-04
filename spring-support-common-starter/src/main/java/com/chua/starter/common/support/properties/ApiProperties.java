package com.chua.starter.common.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * api属性
 *
 * @author CH
 * @since 2025/6/3 15:56
 */
@Data
@ConfigurationProperties(prefix = ApiProperties.PRE, ignoreInvalidFields = true)
public class ApiProperties {

    public static final String PRE = "plugin.api";

    /**
     * 忽略返回格式（针对返回格式不进行统计处理）
     */
    private String[] ignoreFormatPackages;
}

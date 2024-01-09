package com.chua.starter.common.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 统一参数
 */
@Data
@ConfigurationProperties(prefix = "plugin.parameter", ignoreInvalidFields = true)
public class ParameterProperties {

    /**
     * 开启限流
     */
    private boolean enable;
}
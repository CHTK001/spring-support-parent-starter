package com.chua.starter.common.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * 基础配置
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = "plugin.options", ignoreInvalidFields = true)
public class OptionProperties {
    /**
     * 选项
     */
    private Map<String, Object> options;
}

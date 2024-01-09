package com.chua.starter.common.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 日志
 *
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = "plugin.log", ignoreInvalidFields = true)
public class LogProperties {

    /**
     * 开启限流
     */
    private boolean enable = true;
}
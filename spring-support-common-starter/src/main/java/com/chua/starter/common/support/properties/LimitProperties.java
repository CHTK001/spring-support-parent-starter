package com.chua.starter.common.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 限流
 *
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = "plugin.limit", ignoreInvalidFields = true)
public class LimitProperties {

    /**
     * 开启限流
     */
    private boolean openLimit = true;
}
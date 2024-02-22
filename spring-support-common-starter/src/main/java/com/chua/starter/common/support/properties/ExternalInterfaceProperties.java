package com.chua.starter.common.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 限流
 *
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = "plugin.external", ignoreInvalidFields = true)
public class ExternalInterfaceProperties {

    /**
     * 开启外部接口
     */
    private boolean enable;
}
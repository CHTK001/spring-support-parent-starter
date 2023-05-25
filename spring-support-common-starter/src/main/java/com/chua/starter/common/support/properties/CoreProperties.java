package com.chua.starter.common.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 跨域
 *
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = "plugin.core", ignoreInvalidFields = true)
public class CoreProperties {

    /**
     * 是否开启统一响应(返回值自动包裹对象)
     */
    private boolean uniformParameter = true;
    /**
     * 是否开启版本控制
     */
    public boolean openVersion;
}
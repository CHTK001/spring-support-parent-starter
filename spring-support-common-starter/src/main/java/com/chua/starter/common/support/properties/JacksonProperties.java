package com.chua.starter.common.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * jackson配置
 * @author CH
 * @since 2024/12/3
 */
@Data
@ConfigurationProperties(prefix = JacksonProperties.PRE, ignoreInvalidFields = true)
public class JacksonProperties {

    public static final String PRE = "plugin.jackson";

    /**
     * 是否包含null
     */
    private boolean includeNull = false;
}


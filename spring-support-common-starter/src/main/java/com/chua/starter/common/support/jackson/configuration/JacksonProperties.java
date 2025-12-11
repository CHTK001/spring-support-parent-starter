package com.chua.starter.common.support.jackson.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Jackson配置属性
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/3
 */
@Data
@ConfigurationProperties(prefix = JacksonProperties.PRE, ignoreInvalidFields = true)
public class JacksonProperties {
    /**
     * 是否启用
     */
    private boolean enable = false;


    public static final String PRE = "plugin.jackson";

    /**
     * 是否包含null值
     */
    private boolean includeNull = false;
}

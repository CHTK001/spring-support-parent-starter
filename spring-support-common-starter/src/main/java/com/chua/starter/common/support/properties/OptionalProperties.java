package com.chua.starter.common.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 校验码
 *
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = OptionalProperties.PRE, ignoreInvalidFields = true)
public class OptionalProperties {

    /**
     * 配置前缀
     */
    public static final String PRE = "plugin.optional";

    /**
     *  是否打开
     */
    private boolean enable = true;


}

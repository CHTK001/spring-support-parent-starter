package com.chua.starter.common.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 可选功能配置属性
 * <p>
 * 用于配置系统中的可选功能开关。
 * </p>
 *
 * @author CH
 * @version 1.0.0
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
    private boolean enable = false;


}


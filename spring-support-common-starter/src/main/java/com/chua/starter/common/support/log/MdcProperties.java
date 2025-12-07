package com.chua.starter.common.support.log;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MDC配置属性
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/01
 */
@Data
@ConfigurationProperties(prefix = MdcProperties.PRE, ignoreInvalidFields = true)
public class MdcProperties {

    public static final String PRE = "plugin.mdc";

    /**
     * 开启MDC
     * 默认值为true，表示功能启用
     */
    private boolean enable = true;
}

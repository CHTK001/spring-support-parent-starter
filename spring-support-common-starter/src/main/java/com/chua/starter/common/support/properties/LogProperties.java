package com.chua.starter.common.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 日志配置
 *
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = LogProperties.PRE, ignoreInvalidFields = true)
public class LogProperties {

    public static final String PRE = "plugin.log";

    /**
     * 实现
     */
    private String impl = "slf4j";
}

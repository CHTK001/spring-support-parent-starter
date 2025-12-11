package com.chua.starter.common.support.log;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 日志配置属性
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/01
 */
@Data
@ConfigurationProperties(prefix = "plugin.log", ignoreInvalidFields = true)
public class LogProperties {

    /**
     * 日志开启
     */
    private boolean enable = false;

    /**
     * 开启接口日志
     */
    private boolean openInterfaceLog = false;
}

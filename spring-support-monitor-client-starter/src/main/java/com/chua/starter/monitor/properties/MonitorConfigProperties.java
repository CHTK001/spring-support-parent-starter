package com.chua.starter.monitor.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static com.chua.starter.monitor.properties.MonitorConfigProperties.PRE;

/**
 * 监视器属性
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/31
 */
@Data
@ConfigurationProperties(prefix = PRE, ignoreInvalidFields = true)
public class MonitorConfigProperties {

    public static final String PRE = "plugin.monitor.config";

    /**
     * 订阅的配置名称, 默认全部
     */
    private String configAppName;

}

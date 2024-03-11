package com.chua.starter.monitor.server.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = "plugin.monitor.job")
public class JobProperties {
    /**
     * 触发池快速最大值
     */
    private int triggerPoolFastMax = 200;

    /**
     * 触发池慢速最大值
     */
    private int triggerPoolSlowMax = 100;
}

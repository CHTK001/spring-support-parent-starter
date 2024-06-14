package com.chua.starter.monitor.server.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static com.chua.starter.monitor.server.properties.JobProperties.PRE;

/**
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = PRE)
public class JobProperties {

    public static final String PRE = "plugin.monitor.job";
    /**
     * 启用
     */
    private boolean enable = true;
    /**
     * 触发池快速最大值
     */
    private int triggerPoolFastMax = 200;

    /**
     * 触发池慢速最大值
     */
    private int triggerPoolSlowMax = 100;
}

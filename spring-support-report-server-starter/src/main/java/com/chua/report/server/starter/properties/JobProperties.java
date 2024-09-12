package com.chua.report.server.starter.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = JobProperties.PRE)
public class JobProperties {

    public static final String PRE = "plugin.report.server.job";
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

package com.xxl.job.admin.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 客户端配置
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = SchedulerProperties.PRE, ignoreInvalidFields = true)
public class SchedulerProperties {

    public static final String PRE = "plugin.scheduler.server";
    /**
     * 开启127.0.0.1过滤器
     */
    private boolean openLocalNetFilter;
}

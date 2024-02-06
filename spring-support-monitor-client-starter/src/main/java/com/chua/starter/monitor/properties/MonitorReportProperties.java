package com.chua.starter.monitor.properties;

import com.google.common.collect.Lists;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

import static com.chua.starter.monitor.properties.MonitorReportProperties.PRE;

/**
 * 监视器属性
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/31
 */
@Data
@ConfigurationProperties(prefix = PRE, ignoreInvalidFields = true)
public class MonitorReportProperties {

    public static final String PRE = "plugin.monitor.report";

    private List<String> plugins = Lists.newArrayList("jvm");

    /**
     * 报告时间(s)
     */
    private long reportTime = 10;
}

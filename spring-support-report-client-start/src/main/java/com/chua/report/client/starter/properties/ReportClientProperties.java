package com.chua.report.client.starter.properties;

import com.chua.report.client.starter.report.event.ReportEvent;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashSet;
import java.util.Set;

/**
 * 上报配置
 * @author CH
 * @since 2024/9/11
 */
@Data
@ConfigurationProperties(prefix = ReportClientProperties.PRE, ignoreInvalidFields = true)
public class ReportClientProperties {

    public static final String PRE = "plugin.report.client";

    /**
     * 是否开启
     */
    private boolean enable = false;


    /**
     * 是否开启自上报
     */
    private boolean openSelf = false;

    /**
     * 上报服务器地址
     */
    private String address;

    /**
     * 上报服务
     */
    private Set<ReportEvent.ReportType> report = new HashSet<>();

    /**
     * 上报时间间隔(s)
     */
    private Integer reportTime = 10;

    /**
     * trace切面(使用agent则会忽略切面实现)
     */
    private String traceAop;
}

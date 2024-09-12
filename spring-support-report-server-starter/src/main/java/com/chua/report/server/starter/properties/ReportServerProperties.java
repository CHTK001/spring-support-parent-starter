package com.chua.report.server.starter.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 上报配置
 * @author CH
 * @since 2024/9/11
 */
@Data
@ConfigurationProperties(prefix = ReportServerProperties.PRE, ignoreInvalidFields = true)
public class ReportServerProperties {

    public static final String PRE = "plugin.report.server";


    /**
     * 上报地址(不填默认当前系统, 用于处理zbus服务共用一个/占用情况)
     */
    private String reportEndpointHost;
    /**
     * 上报地址(不填默认当前系统, 用于处理zbus服务共用一个/占用情况)
     */
    private Integer reportEndpointPort;
}

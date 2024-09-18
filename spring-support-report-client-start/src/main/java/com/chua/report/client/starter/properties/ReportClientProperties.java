package com.chua.report.client.starter.properties;

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
     * 上报服务器地址
     */
    private String address;

    /**
     * 上报服务
     */
    private Set<String> report = new HashSet<>();

    /**
     * 上报时间间隔(s)
     */
    private Integer reportTime = 10;
}

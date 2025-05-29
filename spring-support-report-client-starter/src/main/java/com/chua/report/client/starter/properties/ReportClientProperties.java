package com.chua.report.client.starter.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
     * 接收协议
     */
    private String receivableProtocol = "http";
    /**
     * 接收端口
     */
    private Integer receivablePort = -1;
}

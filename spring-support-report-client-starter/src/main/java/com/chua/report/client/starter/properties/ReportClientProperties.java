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
     * 上报接口
     */
    private String addressReportPath = "/monitor/api";
    /**
     * 接收协议
     */
    private String receivableProtocol = "http";
    /**
     * 接收端口
     */
    private Integer receivablePort = -1;

    /**
     * 推送间隔时间（秒）
     */
    private long pushInterval = 30;

    /**
     * 初始延迟时间（秒）
     */
    private long initialDelay = 10;

    /**
     * 连接超时时间（毫秒）
     */
    private int connectTimeout = 5000;

    /**
     * 读取超时时间（毫秒）
     */
    private int readTimeout = 10000;

    /**
     * 重试次数
     */
    private int retryCount = 3;

    /**
     * 设备标识
     */
    private String deviceId;

    /**
     * 设备名称
     */
    private String deviceName;
}

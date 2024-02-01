package com.chua.starter.monitor.request;

import lombok.Data;

/**
 * 监视器请求
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/31
 */
@Data
public class MonitorRequest {

    /**
     * 类型
     */
    private MonitorRequestType type;

    /**
     * 报告类型
     */
    private String reportType;

    /**
     * 应用程序名称
     */
    private String appName;

    /**
     * 轮廓
     */
    private String profile;

    /**
     * 订阅应用程序名称
     */
    private String subscribeAppName;

    /**
     * 数据
     */
    private Object data;

    /**
     * 服务器主机
     */
    private String serverHost;

    /**
     * 服务器端口
     */
    private String serverPort;

    /**
     * 端点URL
     */
    private String endpointsUrl;

    /**
     * 上下文路径
     */
    private String contextPath;

}

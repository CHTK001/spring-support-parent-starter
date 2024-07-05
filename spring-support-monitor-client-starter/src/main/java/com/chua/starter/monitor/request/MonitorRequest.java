package com.chua.starter.monitor.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;

/**
 * 监视器请求
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/31
 */
@Data
public class MonitorRequest implements Serializable {

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
     * 值
     */
    private double value;
    /**
     * 消息
     */
    private String msg;

    /**
     * 密码
     */
    private String code;

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

    /**
     * 时间戳
     */
    private long timestamp = System.currentTimeMillis();

    private transient volatile String uid;

    /**
     * 唯一标识
     *
     * @return 唯一标识
     */
    @JsonIgnore
    public String getUid() {
        return "monitor:report:" + getKey();
    }

    /**
     * 唯一标识
     *
     * @return 唯一标识
     */
    @JsonIgnore
    public  String getKey() {
        return getAppName()+ ":" + getServerHost() + "_" + getServerPort() + ":" +  getReportType();
    }

}

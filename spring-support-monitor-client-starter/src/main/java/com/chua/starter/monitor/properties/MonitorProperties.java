package com.chua.starter.monitor.properties;

import com.chua.common.support.net.NetUtils;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static com.chua.starter.monitor.properties.MonitorProperties.PRE;

/**
 * 监视器属性
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/31
 */
@Data
@ConfigurationProperties(prefix = PRE, ignoreInvalidFields = true)
public class MonitorProperties {

    public static final String PRE = "plugin.monitor";
    /**
     * 打开
     */
    private boolean enable = true;


    private String address = NetUtils.getLocalHost();

    /**
     * 保活时间(s)
     */
    private long keepAliveTime = 30;
    /**
     * 监听地址
     */
    private String monitor = "http://${plugin.monitor.mq.host:127.0.0.1}:31111/monitor/api/report";

}

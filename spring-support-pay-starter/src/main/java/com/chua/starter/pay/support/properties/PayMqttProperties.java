package com.chua.starter.pay.support.properties;

import com.chua.common.support.utils.IdUtils;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 支付通知
 *
 * @author CH
 * @since 2025/1/3
 */
@Data
@ConfigurationProperties(prefix = PayMqttProperties.PRE, ignoreInvalidFields = true)
public class PayMqttProperties {

    public static final String PRE = "plugin.pay.mqtt";
    /**
     * 主机
     */
    private String host = "0.0.0.0";

    /**
     * 端口
     */
    private int port = 0;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;


}

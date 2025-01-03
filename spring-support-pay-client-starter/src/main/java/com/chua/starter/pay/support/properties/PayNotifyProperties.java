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
@ConfigurationProperties(prefix = PayNotifyProperties.PRE, ignoreInvalidFields = true)
public class PayNotifyProperties {

    public static final String PRE = "plugin.pay.notify";


    /**
     * 是否启用
     */
    private boolean enable;

    /**
     * 通知类型
     */
    private Type type;

    /**
     * mqtt配置
     */
    private MqttConfig mqttConfig = new MqttConfig();


    @Data
    public static class MqttConfig {

        /**
         * 客户端id
         */
        private String clientId = IdUtils.createUlid();
        /**
         * 主机
         */
        private String host = "127.0.0.1";

        /**
         * 端口
         */
        private int port = 31070;

        /**
         * 用户名
         */
        private String username;

        /**
         * 密码
         */
        private String password;
    }


    public static enum Type {
        /**
         * mqtt
         */
        MQTT
    }


}

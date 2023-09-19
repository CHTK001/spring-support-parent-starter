package com.chua.subscribe.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.chua.subscribe.support.properties.EventbusProperties.PRE;

/**
 * @author CH
 */

@Data
@ConfigurationProperties(prefix = PRE, ignoreInvalidFields = true)
public class EventbusProperties {

    public static final String PRE = "plugin.eventbus";


    @NestedConfigurationProperty
    public Map<String, SubscribeEventbusConfig> subscribe = new LinkedHashMap<>();


    @Data
    public static class SubscribeEventbusConfig {

        /**
         * 地址
         */
        private String host;

        /**
         * 端口
         */
        private int port;

        /**
         * 用户名
         */
        private String username;
        /**
         * passwd
         */
        private String passwd;
        /**
         * kafka分组
         */
        private String groupId;

    }
}

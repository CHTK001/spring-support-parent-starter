package com.chua.starter.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;


/**
 * @author CH
 * @since 2022/7/30 8:37
 */
@Data
@ConfigurationProperties(prefix = ConfigUniformProperties.PRE, ignoreInvalidFields = true)
public class ConfigUniformProperties {

    public static final String PRE = "plugin.configuration.uniform";

    /**
     * 统一中心地址
     */
    private String address = "127.0.0.1:23579";
    /**
     * 协议
     */
    private String protocol = "MQ";
    /**
     * 是否开启
     */
    private boolean open = true;

}

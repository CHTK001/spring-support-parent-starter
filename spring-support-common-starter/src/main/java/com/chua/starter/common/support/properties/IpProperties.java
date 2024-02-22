package com.chua.starter.common.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 跨域/版本控制/统一响应
 *
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = IpProperties.PRE, ignoreInvalidFields = true)
public class IpProperties {

    public static final String PRE = "plugin.ip";
    /**
     * 是否开启版本控制
     */
    private boolean enable;

    /**
     * ip翻译实现方式
     */
    private String ipType = "qqwry";
}
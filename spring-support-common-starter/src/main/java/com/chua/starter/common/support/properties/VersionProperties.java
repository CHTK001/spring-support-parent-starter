package com.chua.starter.common.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static com.chua.starter.common.support.properties.VersionProperties.PRE;

/**
 * 跨域/版本控制/统一响应
 *
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = PRE, ignoreInvalidFields = true)
public class VersionProperties {

    public static final String PRE = "plugin.version";
    /**
     * 是否开启版本控制
     */
    private boolean enable = true;

    /**
     * 平台名称
     */
    private String platform;

    /**
     * 版本号
     */
    private String version;
}
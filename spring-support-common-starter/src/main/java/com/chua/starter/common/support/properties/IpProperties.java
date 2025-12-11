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
    /**
     * 是否启用
     */
    private boolean enable = false;


    public static final String PRE = "plugin.ip";
    /**
     * 是否开启IP控制
     */
    private boolean enable;

    /**
     * 数据库文件路径
     */
    private String databaseFile = "classpath:qqwry.dat";

    /**
     * ip翻译实现方式
     */
    private String ipType = "qqwry";
}


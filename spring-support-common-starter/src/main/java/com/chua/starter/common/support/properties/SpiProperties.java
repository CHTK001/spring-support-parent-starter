package com.chua.starter.common.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * spi配置
 * @author CH
 * @since 2024/7/22
 */
@Data
@ConfigurationProperties(prefix = SpiProperties.PRE, ignoreInvalidFields = true)
public class SpiProperties {

    public static final String PRE = "plugin.spi";

    /**
     * 虚拟映射
     */
    private Map<String, String> mapping;


    /**
     * 是否开启虚拟映射
     */
    private boolean enable = true;

}


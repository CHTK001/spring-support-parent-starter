package com.chua.starter.discovery.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedList;
import java.util.List;

/**
 * 发现配置
 * @author CH
 * @since 2024/9/9
 */
@Data
@ConfigurationProperties(prefix = DiscoveryListProperties.PRE, ignoreInvalidFields = true)
public class DiscoveryListProperties {
    /**
     * 是否启用
     */
    private boolean enable = false;


    public static final String PRE = "plugin.discovery";

    /**
     * 发现配置
     */
    private List<DiscoveryProperties> properties = new LinkedList<>();



}

package com.chua.starter.common.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 缓存配置
 * @author CH
 * @since 2024/7/22
 */
@Data
@ConfigurationProperties(prefix = CacheProperties.PRE, ignoreInvalidFields = true)
public class CacheProperties {

    public static final String PRE = "plugin.cache";

    /**
     * 缓存类型
     */
    private List<String> type = List.of("default", "redis");
}

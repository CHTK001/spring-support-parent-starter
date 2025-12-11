package com.chua.starter.common.support.cache;

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
    /**
     * 是否启用
     */
    private boolean enable = false;


    public static final String PRE = "plugin.cache";
    /**
     * 缓存类型
     */
    private List<String> type = List.of("default", "redis");


    /**
     * redis
     */
    private RedisProperties redis = new RedisProperties();

    /**
     * redis
     */
    @Data
    public static class RedisProperties {

        /**
         * 缓存时间
         */
        private long ttl = 600;

    }
}



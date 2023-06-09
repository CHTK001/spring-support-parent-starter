package com.chua.starter.cacheable.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedList;
import java.util.List;

/**
 * @author CH
 * @since 2022/8/10 11:45
 */
@Data
@ConfigurationProperties(prefix = CacheProperties.PRE, ignoreInvalidFields = true)
public class CacheProperties {


    public static final String PRE = "plugin.cache";
    /**
     * 是否存储空值，默认true，防止缓存穿透
     */
    private boolean cacheNullValues = true;
    /**
     * 前缀
     */
    private String cachePrefix;

    private
    /**
     * 缓存链路
     */
    private String link = "mem -> guava";

    /**
     * 缓存工厂
     */
    @Data
    public static class CachePoolProperties {
        /**
         * 缓存类型
         */
        private String type;
        /**
         * 过期时间
         */
        private long expire;
        /**
         * 配置文件路径
         */
        private String ehcache;

    }
}

package com.chua.starter.monitor.server.constant;

/**
 * REDIS常量
 * @author CH
 * @since 2024/7/16
 */
public interface RedisConstant {

    /**
     * 时间序列前缀
     */
    String REDIS_TIME_SERIES_PREFIX = com.chua.redis.support.constant.RedisConstant.REDIS_TIME_SERIES_PREFIX;
    /**
     * 监控前缀
     */
    String REDIS_TIME_SERIES_REPORT_PREFIX = REDIS_TIME_SERIES_PREFIX +  "REPORT:";
    /**
     * 指标前缀
     */
    String REDIS_TIME_SERIES_INDICATOR_PREFIX = REDIS_TIME_SERIES_PREFIX +  "INDICATOR:";

    /**
     * 搜索前缀
     */

    String REDIS_SEARCH_PREFIX = com.chua.redis.support.constant.RedisConstant.REDIS_SEARCH_PREFIX;
    /**
     * 监控前缀
     */
    String REDIS_SEARCH_MONITOR_REPORT_PREFIX = REDIS_SEARCH_PREFIX + "monitor:report:" ;

    /**
     * 监控前缀
     */
    String REDIS_SEARCH_MONITOR_GEN_PREFIX = REDIS_SEARCH_PREFIX + "monitor_gen_" ;
}

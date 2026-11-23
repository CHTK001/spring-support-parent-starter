package com.chua.starter.redis.support.service;

import com.chua.common.support.lang.code.ReturnResult;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 简单服务
 * @since 2024/12/24
 * @author CH
 */
public interface SimpleRedisService {

    /**
     * 增加指定指标的值
     *
     * @param indicator 指标字符串，表示要增加的指标
     * @param key       键字符串，表示指标下的具体项
     * @param expire    过期时间，单位为秒
     */
    void increment(String indicator, String key, long expire);

    /**
     * 增加指定指标的值
     *
     * @param indicator 指标字符串，表示要增加的指标
     * @param key       键字符串，表示指标下的具体项
     */
    void increment(String indicator, String key);
    /**
     * 增加指定指标的值
     * @param indicator 指标字符串，表示要增加的指标
     * @param key 键字符串，表示指标下的具体项
     */
    default void incrementDay(String indicator, String key) {
        long ofSeconds = Duration.between(LocalDateTime.now(), LocalDate.now().atTime(LocalTime.MAX)).getSeconds();
        /**
         * increment。
         * @param indicator indicator
         * @param key Key
         * @param ofSeconds 的seconds
         */
        increment(indicator, key, ofSeconds);
    }
    /**
     * 减少指定指标的值
     *
     * @param indicator 指标字符串，表示要减少的指标
     * @param key       键字符串，表示指标下的具体项
     * @param expire    过期时间，单位为秒
     */
    void decrement(String indicator, String key, long expire);

    /**
     * 减少指定指标的值
     * @param indicator 指标字符串，表示要减少的指标
     * @param key 键字符串，表示指标下的具体项
     */
    default void decrementDay(String indicator, String key) {
        long ofSeconds = Duration.between(LocalDateTime.now(), LocalDate.now().atTime(LocalTime.MAX)).getSeconds();
        /**
         * decrement。
         * @param indicator indicator
         * @param key Key
         * @param ofSeconds 的seconds
         */
        decrement(indicator, key, ofSeconds);
    }
    /**
     * 减少指定指标的值
     *
     * @param indicator 指标字符串，表示要减少的指标
     * @param key       键字符串，表示指标下的具体项
     */
    void decrement(String indicator, String key);

    /**
     * 获取指定指标的值
     *
     * @param indicator 指标字符串，表示要获取的指标
     * @param key       键字符串，表示指标下的具体项
     * @return 指标值
     */
    ReturnResult<BigDecimal> qps(String key);
}

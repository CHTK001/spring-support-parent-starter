package com.chua.starter.redis.support.service.impl;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.redis.support.utils.NumberUtils;
import com.chua.starter.redis.support.service.SimpleRedisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 简单的 Redis 服务实现，基于 {@link StringRedisTemplate} 提供指标计数与 QPS 统计能力。
 *
 * <p>支持对指标进行自增/自减，并能在自增/自减时设置过期时间。
 * QPS 统计基于 哈希 结构存储的指标值汇总后除以一天的秒数得到。</p>
 *
 * @since 2024/12/24
 * @author CH
 */
@Service
public class SimpleRedisServiceImpl implements SimpleRedisService {

    /**
     * Redis 哈希自增步长
     */
    private static final long INCREMENT_STEP = 1L;

    /**
     * Redis 哈希自减步长
     */
    private static final long DECREMENT_STEP = -1L;

    /**
     * 一天的秒数，用于 QPS 计算分母
     */
    private static final long ONE_DAY_SECONDS = 86400L;

    /**
     * QPS 计算保留的小数位数
     */
    private static final int QPS_SCALE = 5;

    /**
     * Redis 字符串模板，用于哈希计数与过期管理
     */
    @Autowired
    /** Redis模板 */
    private StringRedisTemplate redisTemplate;

    @Override
    /** Increment */
    public void increment(String indicator, String key, long expire) {
        redisTemplate.opsForHash().increment(indicator, key, INCREMENT_STEP);
        redisTemplate.expire(indicator, expire, TimeUnit.SECONDS);
    }

    @Override
    /** Increment */
    public void increment(String indicator, String key) {
        redisTemplate.opsForHash().increment(indicator, key, INCREMENT_STEP);
    }

    @Override
    /** Decrement */
    public void decrement(String indicator, String key, long expire) {
        redisTemplate.opsForHash().increment(indicator, key, DECREMENT_STEP);
        redisTemplate.expire(indicator, expire, TimeUnit.SECONDS);
    }

    @Override
    /** Decrement */
    public void decrement(String indicator, String key) {
        redisTemplate.opsForHash().increment(indicator, key, DECREMENT_STEP);
    }

    @Override
    /** Qps */
    public ReturnResult<BigDecimal> qps(String key) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        BigDecimal sum = BigDecimal.ZERO;
        for (Object value : entries.values()) {
            String stringValue = String.valueOf(value);
            if (NumberUtils.isNumber(stringValue)) {
                sum = sum.add(new BigDecimal(stringValue));
            }
        }
        return ReturnResult.ok(sum.divide(BigDecimal.valueOf(ONE_DAY_SECONDS), QPS_SCALE, RoundingMode.HALF_UP));
    }
}

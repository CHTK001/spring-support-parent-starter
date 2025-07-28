package com.chua.starter.redis.support.service.impl;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.NumberUtils;
import com.chua.redis.support.client.RedisClient;
import com.chua.starter.redis.support.service.SimpleRedisService;
import org.springframework.beans.factory.annotation.Autowired;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * 简单的redis服务
 * @author CH
 * @since 2024/12/24
 */
public class SimpleRedisServiceImpl implements SimpleRedisService {

    @Autowired
    private RedisClient redisClient;
    @Override
    public void increment(String indicator, String key, long expire) {
        JedisPool jedis = redisClient.getJedisPool();
        try (Jedis resource = jedis.getResource()) {
            resource.hincrBy(indicator ,  key, 1);
            resource.expire(indicator, expire);
        }
    }

    @Override
    public void increment(String indicator, String key) {
        JedisPool jedis = redisClient.getJedisPool();
        try (Jedis resource = jedis.getResource()) {
            resource.hincrBy(indicator ,  key, 1);
        }
    }

    @Override
    public void decrement(String indicator, String key, long expire) {
        JedisPool jedis = redisClient.getJedisPool();
        try (Jedis resource = jedis.getResource()) {
            resource.hincrBy(indicator ,  key, -1);
            resource.expire(indicator, expire);
        }
    }

    @Override
    public void decrement(String indicator, String key) {
        JedisPool jedis = redisClient.getJedisPool();
        try (Jedis resource = jedis.getResource()) {
            resource.hincrBy(indicator ,  key, -1);
        }
    }

    @Override
    public ReturnResult<BigDecimal> qps(String key) {
        JedisPool jedis = redisClient.getJedisPool();
        try (Jedis resource = jedis.getResource()) {
            Map<String, String> stringStringMap = resource.hgetAll(key);
            BigDecimal sum = new BigDecimal(0);
            for (String string : stringStringMap.values()) {
                if(NumberUtils.isNumber(string)) {
                    sum = sum.add(new BigDecimal(string));
                }
            }
            return ReturnResult.success(sum.divide(BigDecimal.valueOf(86400), 5, RoundingMode.HALF_UP));
        }
    }
}

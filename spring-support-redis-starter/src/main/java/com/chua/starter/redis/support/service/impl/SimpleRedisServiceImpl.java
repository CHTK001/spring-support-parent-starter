package com.chua.starter.redis.support.service.impl;

import com.chua.redis.support.client.RedisChanelSession;
import com.chua.redis.support.client.RedisClient;
import com.chua.starter.redis.support.service.SimpleRedisService;
import org.springframework.beans.factory.annotation.Autowired;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

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
        RedisChanelSession redisSession  = (RedisChanelSession) redisClient.getSession();
        JedisPool jedis = redisSession.getJedis();
        try (Jedis resource = jedis.getResource()) {
            resource.hincrBy(indicator ,  key, 1);
            resource.expire(indicator, expire);
        }
    }

    @Override
    public void increment(String indicator, String key) {
        RedisChanelSession redisSession  = (RedisChanelSession) redisClient.getSession();
        JedisPool jedis = redisSession.getJedis();
        try (Jedis resource = jedis.getResource()) {
            resource.hincrBy(indicator ,  key, 1);
        }
    }

    @Override
    public void decrement(String indicator, String key, long expire) {
        RedisChanelSession redisSession  = (RedisChanelSession) redisClient.getSession();
        JedisPool jedis = redisSession.getJedis();
        try (Jedis resource = jedis.getResource()) {
            resource.hincrBy(indicator ,  key, -1);
            resource.expire(indicator, expire);
        }
    }

    @Override
    public void decrement(String indicator, String key) {
        RedisChanelSession redisSession  = (RedisChanelSession) redisClient.getSession();
        JedisPool jedis = redisSession.getJedis();
        try (Jedis resource = jedis.getResource()) {
            resource.hincrBy(indicator ,  key, -1);
        }
    }
}

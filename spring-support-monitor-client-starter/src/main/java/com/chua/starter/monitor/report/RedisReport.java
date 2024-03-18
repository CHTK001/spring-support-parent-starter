package com.chua.starter.monitor.report;


import com.chua.common.support.annotations.Spi;
import com.chua.common.support.annotations.SpiCondition;
import com.chua.common.support.utils.IoUtils;
import com.chua.redis.support.oshi.RedisOshi;
import org.springframework.beans.BeansException;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.RedisTemplate;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisClientConfig;

import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

@Spi("redis")
@SpiCondition("org.springframework.data.redis.core.RedisTemplate")
public class RedisReport implements Report, AutoCloseable{

    @Resource
    private Environment environment;
    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private RedisTemplate redisTemplate;

    private Jedis jedis;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private RedisProperties redisProperties;

    @Override
    public Object report() {
        if(null == environment) {
            return Collections.emptyList();
        }

        initialJedis();
        com.chua.redis.support.oshi.RedisReport redisReport = RedisOshi.newRedis(jedis);
        if(null != redisProperties) {
            redisReport.setPort(redisProperties.getPort());
        }
        return redisReport;
    }

    private void initialJedis() {
        if(running.get()) {
            return;
        }

        if(null != jedis) {
            return;
        }

        running.set(true);
        try {
            redisProperties = applicationContext.getBean(RedisProperties.class);
        } catch (BeansException ignored) {
        }
        if(null != redisProperties) {
            HostAndPort hostAndPort = new HostAndPort(redisProperties.getHost(), redisProperties.getPort());
            JedisClientConfig jedisClientConfig = DefaultJedisClientConfig.builder()
                    .password(redisProperties.getPassword())
                    .database(redisProperties.getDatabase())
                    .build();
            jedis = new Jedis(hostAndPort, jedisClientConfig);
        }
    }

    @Override
    public void close() throws Exception {
        IoUtils.closeQuietly(jedis);
    }
}

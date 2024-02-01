package com.chua.starter.monitor.report;


import com.chua.common.support.annotations.Spi;
import com.chua.common.support.annotations.SpiCondition;
import com.chua.redis.support.oshi.RedisOshi;
import org.springframework.data.redis.connection.RedisConnection;
import redis.clients.jedis.Jedis;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

@Spi("redis")
@SpiCondition("org.springframework.data.redis.core.RedisTemplate")
public class RedisReport implements Report{

    @Resource
    private RedisConnection redisConnection;

    private Jedis jedis;

    private final AtomicBoolean running = new AtomicBoolean(false);
    @Override
    public Object report() {
        if(null == redisConnection) {
            return Collections.emptyList();
        }

        initialJedis();
        return RedisOshi.newRedis(jedis);
    }

    private void initialJedis() {
        if(running.get()) {
            return;
        }

        if(null != jedis) {
            return;
        }

        running.set(true);
        jedis = new Jedis();
    }
}

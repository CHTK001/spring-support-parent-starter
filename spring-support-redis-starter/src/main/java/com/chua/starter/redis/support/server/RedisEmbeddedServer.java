package com.chua.starter.redis.support.server;

import com.chua.common.support.net.NetUtils;
import com.chua.starter.redis.support.properties.RedisServerProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import redis.embedded.RedisServer;

/**
 * redis
 *
 * @author CH
 */
@Slf4j
public class RedisEmbeddedServer implements DisposableBean {

    private final RedisServerProperties redisServerProperties;
    private RedisServer redisServer;

    public RedisEmbeddedServer(RedisServerProperties redisServerProperties) {
        this.redisServerProperties = redisServerProperties;
    }

    @Override
    public void destroy() throws Exception {
        redisServer.stop();
        log.info("关闭嵌入式redis服务[{}:{}]", redisServerProperties.getHost(), redisServerProperties.getPort());
    }

    @PostConstruct
    public void afterPropertiesSet() {
        if (!redisServerProperties.isOpenEmbedded()) {
            return;
        }

        if (NetUtils.isPortInUsed(redisServerProperties.getPort())) {
            return;
        }

        redisServer = RedisServer.builder()
                .bind(redisServerProperties.getHost())
                .port(redisServerProperties.getPort())
                .setting("maxmemory " + redisServerProperties.getMaxMemory())
                .setting("maxheap " + redisServerProperties.getMaxHeap())
                .build();

        redisServer.start();
        log.info("开启嵌入式redis服务[{}:{}]", redisServerProperties.getHost(), redisServerProperties.getPort());
    }
}

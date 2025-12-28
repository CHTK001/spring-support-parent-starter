package com.chua.starter.redis.support.server;

import com.chua.common.support.net.NetUtils;
import com.chua.starter.redis.support.properties.RedisServerProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import redis.embedded.RedisServer;

import static com.chua.starter.common.support.logger.ModuleLog.*;

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
        if (redisServer != null) {
            redisServer.stop();
            log.info("[Redis] 嵌入式服务已停止 {}", address(redisServerProperties.getHost(), redisServerProperties.getPort()));
        }
    }

    @PostConstruct
    public void afterPropertiesSet() {
        if (!redisServerProperties.isOpenEmbedded()) {
            log.info("[Redis] 嵌入式服务 [{}]", disabled());
            return;
        }

        if (NetUtils.isPortInUsed(redisServerProperties.getPort())) {
            log.warn("[Redis] 端口 {} 已被占用, 跳过启动", redisServerProperties.getPort());
            return;
        }

        redisServer = RedisServer.builder()
                .bind(redisServerProperties.getHost())
                .port(redisServerProperties.getPort())
                .setting("maxmemory " + redisServerProperties.getMaxMemory())
                .setting("maxheap " + redisServerProperties.getMaxHeap())
                .build();

        redisServer.start();
        log.info("[Redis] 嵌入式服务已启动 {} [{}]", address(redisServerProperties.getHost(), redisServerProperties.getPort()), enabled());
    }
}

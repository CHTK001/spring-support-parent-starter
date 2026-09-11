package com.chua.socket.support.session;

import com.chua.socket.support.properties.SocketProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Socket 服务器动态注册服务
 * 服务器启动时向 Redis 注册本实例的 socket 配置
 */
@Slf4j
@Service
public class SocketServerRegistry {

    private static final String KEY_PREFIX = "socket:servers:";
    private static final long HEARTBEAT_INTERVAL_MS = 30_000;
    private static final long OFFLINE_THRESHOLD_MS = 120_000;

    private final RedisTemplate<String, Object> redisTemplate;
    private final SocketProperties properties;
    private final boolean enable;
    private String serverId;
    private long lastHeartbeat = System.currentTimeMillis();

    public SocketServerRegistry(RedisTemplate<String, Object> redisTemplate, SocketProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.enable = properties.isEnable();
        this.serverId = generateServerId();
        if (enable) {
            log.info("[SocketServerRegistry] 服务器推送注册已启用，serverId={}", serverId);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!enable) {
            return;
        }
        try {
            registerSelf();
            log.info("[SocketServerRegistry] 服务器注册到 Redis: serverId={}", serverId);
        } catch (Exception e) {
            log.error("[SocketServerRegistry] 服务器注册失败", e);
        }
    }

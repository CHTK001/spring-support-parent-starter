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
 * <p>
 * 服务器启动时向 Redis 注册本实例的 socket 配置（协议/地址/端口/path/context 等），
 * 并启动心跳定时任务保持在线。支持多个服务器实例同时运行，并按协议/地址汇聚供前端获取。
 * </p>
 * <p>
 * 前端从接口获取所有在线 socket 服务器地址时，自动合并数据库配置（默认为空），
 * 前端根据 URL scheme 决定协议，不再固定死 protocol。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
public class SocketServerRegistry {

    /** 注册本服务器到 Redis */
    public void registerSelf() {
        Map<String, Object> info = new HashMap<>();
        info.put("id", serverId);
        info.put("protocol", properties.getProtocol().getValue());
        info.put("host", getLocalHost());
        info.put("port", getActualPort());
        info.put("path", getPath());
        info.put("context", getContext());
        info.put("enable", enable ? 1 : 0);
        info.put("heartbeat", System.currentTimeMillis());
        info.put("ttl", 3600);
        info.put("name", getServerName());

        String key = KEY_PREFIX + serverId;
        redisTemplate.opsForHash().putAll(key, info);
        redisTemplate.expire(key, 3600, TimeUnit.SECONDS);

        log.info("[SocketServerRegistry] 服务器注册完成: serverId={}, protocol={}", serverId, properties.getProtocol().getValue());
    }



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

}

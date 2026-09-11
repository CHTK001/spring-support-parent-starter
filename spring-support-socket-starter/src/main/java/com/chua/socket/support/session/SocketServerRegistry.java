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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;

/**
 * Socket 服务器动态注册服务
 * 服务器启动时向 Redis 注册本实例的 socket 配置
 * @author CH
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
    private final String serverId;
    private volatile long lastHeartbeat = System.currentTimeMillis();
    private Map<String, Object> registeredInfo;
    private ScheduledFuture<?> heartbeatTask;

    public SocketServerRegistry(RedisTemplate<String, Object> redisTemplate, SocketProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.enable = properties.getEnable() != null && properties.getEnable();
        this.serverId = generateServerId();

        if (enable) {
            log.info("[SocketServerRegistry] 服务器推送注册已启用，serverId={}", serverId);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!enable) {
            log.info("[SocketServerRegistry] 服务器推送注册未启用");
            return;
        }
        try {
            registerSelf();
            log.info("[SocketServerRegistry] 服务器注册到 Redis: serverId={}", serverId);
        } catch (Exception e) {
            log.error("[SocketServerRegistry] 服务器注册失败", e);
        }
    }

    /** 注册本服务器到 Redis */
    public void registerSelf() {
        Map<String, Object> info = new HashMap<>();
        info.put("id", serverId);
        info.put("protocol", properties.getProtocol().getValue());
        info.put("host", getLocalHost());
        info.put("port", getActualPort(properties));
        info.put("path", getPath());
        info.put("context", getContextPath());
        info.put("enable", properties.getEnable() != null && properties.getEnable() ? 1 : 0);
        info.put("heartbeat", System.currentTimeMillis());
        info.put("ttl", 3600);
        info.put("name", getServerName());

        registeredInfo = info;
        String key = KEY_PREFIX + serverId;
        redisTemplate.opsForHash().putAll(key, info);
        redisTemplate.expire(key, 3600, TimeUnit.SECONDS);

        scheduledHeartbeat();
        log.info("[SocketServerRegistry] 服务器注册完成: serverId={}, protocol={}",
                serverId, properties.getProtocol().getValue());
    }

    /** 心跳定时任务（每30秒更新一次心跳） */
    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MS)
    public void heartbeat() {
        if (!enable || registeredInfo == null) {
            return;
        }
        lastHeartbeat = System.currentTimeMillis();
        String key = KEY_PREFIX + serverId;
        try {
            redisTemplate.opsForHash().put(key, "heartbeat", lastHeartbeat);
            redisTemplate.expire(key, 3600, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[SocketServerRegistry] 心跳更新失败: serverId={}", serverId, e);
        }
    }

    /** 获取所有在线 socket 服务器地址（合并数据库配置） */
    public List<SocketServerInfo> getAllOnlineServers() {
        List<SocketServerInfo> result = new ArrayList<>();

        Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
        if (keys != null) {
            for (String key : keys) {
                Map<Object, Object> info = redisTemplate.opsForHash().entries(key);
                if (info != null && !info.isEmpty()) {
                    SocketServerInfo server = parseServerInfo(info);
                    if (server != null && isOnline(server)) {
                        result.add(server);
                    }
                }
            }
        }

        List<SocketServerInfo> dbServers = getDatabaseServers();
        if (dbServers != null && !dbServers.isEmpty()) {
            for (SocketServerInfo dbServer : dbServers) {
                boolean exists = result.stream()
                        .anyMatch(s -> Objects.equals(s.getId(), dbServer.getId()));
                if (!exists) {
                    result.add(dbServer);
                }
            }
        }

        log.info("[SocketServerRegistry] 获取在线服务器列表: {} 个服务器", result.size());
        return result;
    }

    // ======================== 辅助方法 ========================

    private String generateServerId() {
        String host = getLocalHost();
        int port = getActualPort(properties);
        return host + ":" + port;
    }

    private String getLocalHost() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            log.warn("[SocketServerRegistry] 获取本地地址失败，使用默认地址");
            return "127.0.0.1";
        }
    }

    private int getActualPort(SocketProperties properties) {
        if (properties.getPort() != null && properties.getPort() > 0) {
            return properties.getPort();
        }
        return 8080;
    }

    private String getPath() {
        return properties.getPath();
    }

    private String getContextPath() {
        return properties.getContext();
    }

    private String getServerName() {
        return properties.getName();
    }

    private SocketServerInfo parseServerInfo(Map<Object, Object> info) {
        try {
            SocketServerInfo server = new SocketServerInfo();
            server.setId(String.valueOf(info.get("id")));
            server.setProtocol(String.valueOf(info.get("protocol")));
            server.setHost(String.valueOf(info.get("host")));
            server.setPort(parseInt(info.get("port")));
            server.setPath(String.valueOf(info.get("path")));
            server.setContext(String.valueOf(info.get("context")));
            server.setEnable(parseInt(info.get("enable")) == 1);
            server.setHeartbeat(parseLong(info.get("heartbeat")));
            server.setTtl(parseInt(info.get("ttl")));
            server.setName(String.valueOf(info.get("name")));
            return server;
        } catch (Exception e) {
            log.warn("[SocketServerRegistry] 解析服务器信息失败: {}", e.getMessage());
            return null;
        }
    }

    private int parseInt(Object value) {
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return 0;
        }
    }

    private long parseLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (Exception e) {
            return 0L;
        }
    }

    private boolean isOnline(SocketServerInfo server) {
        if (server.getHeartbeat() == 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        return (now - server.getHeartbeat()) <= OFFLINE_THRESHOLD_MS;
    }

    protected List<SocketServerInfo> getDatabaseServers() {
        return Collections.emptyList();
    }

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    public String getServerId() {
        return serverId;
    }

    public Map<String, Object> getRegisteredInfo() {
        return registeredInfo;
    }

    // ======================== SocketServerInfo ========================

    public static class SocketServerInfo {
        private String id;
        private String protocol;
        private String host;
        private int port;
        private String path;
        private String context;
        private boolean enable;
        private long heartbeat;
        private int ttl;
        private String name;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getProtocol() {
            return protocol;
        }

        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getContext() {
            return context;
        }

        public void setContext(String context) {
            this.context = context;
        }

        public boolean isEnable() {
            return enable;
        }

        public void setEnable(boolean enable) {
            this.enable = enable;
        }

        public long getHeartbeat() {
            return heartbeat;
        }

        public void setHeartbeat(long heartbeat) {
            this.heartbeat = heartbeat;
        }

        public int getTtl() {
            return ttl;
        }

        public void setTtl(int ttl) {
            this.ttl = ttl;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}



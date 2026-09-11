package com.chua.socket.support.session;

import com.chua.socket.support.properties.SocketProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
                boolean exists = result.stream().anyMatch(s -> Objects.equals(s.getId(), dbServer.getId()));
                if (!exists) {
                    result.add(dbServer);
                }
            }
        }

        log.info("[SocketServerRegistry] 获取在线服务器列表: {} 个服务器", result.size());
        return result;
    }

    /** 服务器关闭时不删除 Redis 注册信息 */
    public void onShutdown() {
        if (!enable) {
            return;
        }
        log.warn("[SocketServerRegistry] 服务器 {} 下线，不删除 Redis 注册信息", serverId);
    }

    /** 心跳定时任务：刷新心跳时间并续期 */
    @Scheduled(fixedDelay = HEARTBEAT_INTERVAL_MS)
    public void heartbeat() {
        if (!enable) {
            return;
        }
        try {
            lastHeartbeat = System.currentTimeMillis();
            String key = KEY_PREFIX + serverId;
            redisTemplate.opsForHash().put(key, "heartbeat", lastHeartbeat);
            redisTemplate.expire(key, 3600, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[SocketServerRegistry] 心跳刷新失败 serverId={}: {}", serverId, e.getMessage());
        }
    }

    // ======================== 辅助方法 ========================

    private String generateServerId() {
        String host = getLocalHost();
        int port = getActualPort();
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

    private int getActualPort() {
        if (properties.getRoom() != null && !properties.getRoom().isEmpty()) {
            SocketProperties.Room room = properties.getRoom().get(0);
            int port = room.getPort();
            return port > 0 ? port : 9000;
        }
        return 9000;
    }

    private String getPath() {
        if (properties.getRoom() != null && !properties.getRoom().isEmpty()) {
            SocketProperties.Room room = properties.getRoom().get(0);
            return room.getContextPath();
        }
        return "/socket.io";
    }

    private String getContext() {
        return getPath();
    }

    private String getServerName() {
        if (properties.getRoom() != null && !properties.getRoom().isEmpty()) {
            SocketProperties.Room room = properties.getRoom().get(0);
            return room.getClientId();
        }
        return "default";
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
        return (System.currentTimeMillis() - server.getHeartbeat()) <= OFFLINE_THRESHOLD_MS;
    }

    /** 获取数据库中的 socket 服务器配置（默认为空） */
    protected List<SocketServerInfo> getDatabaseServers() {
        return Collections.emptyList();
    }

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    public String getServerId() {
        return serverId;
    }

    // ======================== SocketServerInfo ========================

    /** Socket 服务器在线信息 */
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

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
* 服务器启动时向 Redis 注册本实例的 Socket 配置（协议/地址/端口/路径/上下文 等），
* 并启动心跳定时任务保持在线。支持多个服务器实例同时运行，并按协议/地址汇聚供前端获取。
* </p>
* <p>
* 前端从接口获取所有在线 Socket 服务器地址时，自动合并数据库配置（默认为空），
* 前端根据 URL scheme 决定协议，不再固定死 协议。
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

    private static final String KEY_PREFIX = "socket:servers:"; // 键前缀
    private static final long HEARTBEAT_INTERVAL_MS = 30_000; // 心跳间隔ms
    private static final long OFFLINE_THRESHOLD_MS = 120_000; // offline阈值ms

    private final RedisTemplate<String, Object> redisTemplate; // redistemplate
    private final SocketProperties properties; // 属性
    private final boolean enable; // enable
    private String serverId; // 服务端标识
    private long lastHeartbeat = System.currentTimeMillis(); // 最后一个心跳

    /**
    * Socket服务端registry。
    * @param redisTemplate redistemplate
    * @param properties 属性
    */
    public SocketServerRegistry(RedisTemplate<String, Object> redisTemplate, SocketProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.enable = properties.isEnable();
        this.serverId = generateServerId();
        if (enable) {
            log.info("[SocketServerRegistry] 服务器推送注册已启用，serverId={}", serverId);
        }
    }

    /**
     * 响应ApplicationReady。
     */
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

    /**
    * 获取所有在线 Socket 服务器地址（合并数据库配置）
    *
    * @return 获取全部online服务端的结果
    */
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

    /**
    * generate服务端标识。
    * @return generate服务端id的结果
    */
    private String generateServerId() {
        String host = getLocalHost();
        int port = getActualPort();
        return host + ":" + port;
    }

    /**
    * 获取本地主机。
    * @return 获取本地主机的结果
    */
    private String getLocalHost() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            log.warn("[SocketServerRegistry] 获取本地地址失败，使用默认地址");
            return "127.0.0.1";
        }
    }

    /**
    * 获取actual端口。
    * @return 获取actual端口的结果
    */
    private int getActualPort() {
        if (properties.getRoom() != null && !properties.getRoom().isEmpty()) {
            SocketProperties.Room room = properties.getRoom().getFirst();
            int port = room.getPort();
            return port > 0 ? port : 9000;
        }
        return 9000;
    }

    /**
    * 获取路径。
    * @return 获取路径的结果
    */
    private String getPath() {
        if (properties.getRoom() != null && !properties.getRoom().isEmpty()) {
            SocketProperties.Room room = properties.getRoom().getFirst();
            return room.getContextPath();
        }
        return "/socket.io";
    }

    /**
    * 获取上下文。
    * @return 获取上下文的结果
    */
    private String getContext() {
        return getPath();
    }

    /**
    * 获取服务端名称。
    * @return 获取服务端名称的结果
    */
    private String getServerName() {
        if (properties.getRoom() != null && !properties.getRoom().isEmpty()) {
            SocketProperties.Room room = properties.getRoom().getFirst();
            return room.getClientId();
        }
        return "default";
    }

    /**
    * 解析服务端信息。
    * @param info 信息
    * @return 解析服务端信息的结果
    */
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

    /**
    * 解析int。
    * @param value 值
    * @return 解析int的结果
    */
    private int parseInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return 0;
        }
    }

    /**
    * 解析long。
    * @param value 值
    * @return 解析long的结果
    */
    private long parseLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (Exception e) {
            return 0L;
        }
    }

    /**
    * 是否online。
    * @param server 服务端
    * @return 是否online的结果
    */
    private boolean isOnline(SocketServerInfo server) {
        if (server.getHeartbeat() == 0) {
            return false;
        }
        return (System.currentTimeMillis() - server.getHeartbeat()) <= OFFLINE_THRESHOLD_MS;
    }

     /**
     * 获取数据库中的服务端
     *
     * @return 服务端标识
     */
    protected List<SocketServerInfo> getDatabaseServers() {
        return Collections.emptyList();
    }

    /**
     * 获取最后一个Heartbeat。
     *
     * @return 结果数值
     */
    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    /**
     * 获取服务端ID。
     *
     * @return 结果字符串
     */
    public String getServerId() {
        return serverId;
    }

    // ======================== SocketServerInfo ========================

    /**
    * Socket 服务器在线信息
    *
    * @param name 名称
    * @return 设置ttl的结果
    * @param ttl ttl
    * @author CH
    * @since 4.0.0
    */
    public static class SocketServerInfo {
        private String id; // 标识
        private String protocol; // 协议
        private String host; // 主机
        private int port; // 端口
        private String path; // 路径
        /**
        * 获取标识。
        * @return 获取id的结果
        */
        private String context;
        private boolean enable; // enable
        private long heartbeat; // 心跳
        private int ttl; // ttl
        /**
        * 设置标识。
        * @param id 标识
        */
        private String name;

        /**
        * 获取id。
        * @return 获取id的结果
        */
        public String getId() {
            return id;
        /**
        * 获取协议。
        * @return 获取协议的结果
        * @param id id
        */
        }

        public void setId(String id) {
            this.id = id;
        /**
        * 设置协议。
        * @param protocol 协议
        * @return 获取协议的结果
        */
        }

        public String getProtocol() {
            return protocol;
        /**
        * 获取主机。
        * @return 获取主机的结果
        * @param protocol 协议
        */
        }

        public void setProtocol(String protocol) {
            this.protocol = protocol;
        /**
        * 设置主机。
        * @param host 主机
        * @return 获取主机的结果
        */
        }

        public String getHost() {
            return host;
        /**
        * 获取端口。
        * @return 获取端口的结果
        * @param host 主机
        */
        }

        public void setHost(String host) {
            this.host = host;
        /**
        * 设置端口。
        * @param port 端口
        * @return 获取端口的结果
        */
        }

        public int getPort() {
            return port;
        /**
        * 获取路径。
        * @return 获取路径的结果
        * @param port 端口
        */
        }

        public void setPort(int port) {
            this.port = port;
        /**
        * 设置路径。
        * @param path 路径
        * @return 获取路径的结果
        */
        }

        public String getPath() {
            return path;
        /**
        * 获取上下文。
        * @return 获取上下文的结果
        * @param path 路径
        */
        }

        public void setPath(String path) {
            this.path = path;
        /**
        * 设置上下文。
        * @param context 上下文
        * @return 获取上下文的结果
        */
        }

        public String getContext() {
            return context;
        /**
        * 是否enable。
        * @return 是否enable的结果
        * @param context 上下文
        */
        }

        public void setContext(String context) {
            this.context = context;
        /**
        * 设置enable。
        * @param enable enable
        * @return 是否enable的结果
        */
        }

        public boolean isEnable() {
            return enable;
        /**
        * 获取心跳。
        * @return 获取心跳的结果
        * @param enable enable
        */
        }

        public void setEnable(boolean enable) {
            this.enable = enable;
        /**
        * 设置心跳。
        * @param heartbeat 心跳
        * @return 获取心跳的结果
        */
        }

        public long getHeartbeat() {
            return heartbeat;
        /**
        * 获取ttl。
        * @return 获取ttl的结果
        * @param name 名称
        * @param ttl ttl
        * @param heartbeat 心跳
        */
        }

        public void setHeartbeat(long heartbeat) {
            this.heartbeat = heartbeat;
        /**
        * 获取ttl。
        * @return 获取ttl的结果
        */
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

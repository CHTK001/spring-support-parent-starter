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
 * Socket 鏈嶅姟鍣ㄥ姩鎬佹敞鍐屾湇鍔? * <p>
 * 鏈嶅姟鍣ㄥ惎鍔ㄦ椂鍚?Redis 娉ㄥ唽鏈疄渚嬬殑 socket 閰嶇疆锛堝崗璁?鍦板潃/绔彛/path/context 绛夛級锛? * 骞跺惎鍔ㄥ績璺冲畾鏃朵换鍔′繚鎸佸湪绾裤€傛敮鎸佸涓湇鍔″櫒瀹炰緥鍚屾椂杩愯锛屽苟鎸夊崗璁?鍦板潃姹囪仛渚涘墠绔幏鍙栥€? * </p>
 * <p>
 * 鍓嶇浠庢帴鍙ｈ幏鍙栨墍鏈夊湪绾?socket 鏈嶅姟鍣ㄥ湴鍧€鏃讹紝鑷姩鍚堝苟鏁版嵁搴撻厤缃紙榛樿涓虹┖锛夛紝
 * 鍓嶇鏍规嵁 URL scheme 鍐冲畾鍗忚锛屼笉鍐嶅浐瀹氭 protocol銆? * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
public class SocketServerRegistry {

    /** 娉ㄥ唽鏈湇鍔″櫒鍒?Redis */
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

        log.info("[SocketServerRegistry] 鏈嶅姟鍣ㄦ敞鍐屽畬鎴? serverId={}, protocol={}", serverId, properties.getProtocol().getValue());
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
            log.info("[SocketServerRegistry] 鏈嶅姟鍣ㄦ帹閫佹敞鍐屽凡鍚敤锛宻erverId={}", serverId);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!enable) {
            return;
        }
        try {
            registerSelf();
            log.info("[SocketServerRegistry] 鏈嶅姟鍣ㄦ敞鍐屽埌 Redis: serverId={}", serverId);
        } catch (Exception e) {
            log.error("[SocketServerRegistry] 鏈嶅姟鍣ㄦ敞鍐屽け璐?, e);
        }
    }


    /** 鑾峰彇鎵€鏈夊湪绾?socket 鏈嶅姟鍣ㄥ湴鍧€锛堝悎骞舵暟鎹簱閰嶇疆锛?*/
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

        log.info("[SocketServerRegistry] 鑾峰彇鍦ㄧ嚎鏈嶅姟鍣ㄥ垪琛? {} 涓湇鍔″櫒", result.size());
        return result;
    }

    /** 鏈嶅姟鍣ㄥ叧闂椂涓嶅垹闄?Redis 娉ㄥ唽淇℃伅 */
    public void onShutdown() {
        if (!enable) {
            return;
        }
        log.warn("[SocketServerRegistry] 鏈嶅姟鍣?{} 涓嬬嚎锛屼笉鍒犻櫎 Redis 娉ㄥ唽淇℃伅", serverId);
    }

    // ======================== 杈呭姪鏂规硶 ========================

    private String generateServerId() {
        String host = getLocalHost();
        int port = getActualPort();
        return host + ":" + port;
    }

    private String getLocalHost() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            log.warn("[SocketServerRegistry] 鑾峰彇鏈湴鍦板潃澶辫触锛屼娇鐢ㄩ粯璁ゅ湴鍧€");
            return "127.0.0.1";
        }
    }

    private int getActualPort() {
        int port = properties.getPort();
        return port > 0 ? port : 8080;
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

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getProtocol() { return protocol; }
        public void setProtocol(String protocol) { this.protocol = protocol; }
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getContext() { return context; }
        public void setContext(String context) { this.context = context; }
        public boolean isEnable() { return enable; }
        public void setEnable(boolean enable) { this.enable = enable; }
        public long getHeartbeat() { return heartbeat; }
        public void setHeartbeat(long heartbeat) { this.heartbeat = heartbeat; }
        public int getTtl() { return ttl; }
        public void setTtl(int ttl) { this.ttl = ttl; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}

        } catch (Exception e) {
            log.warn("[SocketServerRegistry] 瑙ｆ瀽鏈嶅姟鍣ㄤ俊鎭け璐? {}", e.getMessage());
            return null;
        }
    }

    private int parseInt(Object value) {
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).intValue();
        try { return Integer.parseInt(value.toString()); } catch (Exception e) { return 0; }
    }

    private long parseLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number) return ((Number) value).longValue();
        try { return Long.parseLong(value.toString()); } catch (Exception e) { return 0L; }
    }

    private boolean isOnline(SocketServerInfo server) {
        if (server.getHeartbeat() == 0) return false;
        return (System.currentTimeMillis() - server.getHeartbeat()) <= OFFLINE_THRESHOLD_MS;
    }

    /** 鑾峰彇鏁版嵁搴撲腑鐨?socket 鏈嶅姟鍣ㄩ厤缃紙榛樿涓虹┖锛?*/
    protected List<SocketServerInfo> getDatabaseServers() {
        return Collections.emptyList();
    }

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    public String getServerId() {
        return serverId;
    }


}

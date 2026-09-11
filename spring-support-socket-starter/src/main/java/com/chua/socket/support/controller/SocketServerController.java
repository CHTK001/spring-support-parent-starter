package com.chua.socket.support.controller;

import com.chua.socket.support.session.SocketServerRegistry;
import com.chua.socket.support.session.SocketServerRegistry.SocketServerInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Socket 服务器信息获取接口
 * 前端通过此接口获取所有在线 socket 服务器地址列表
 */
@Slf4j
@RestController
@RequestMapping("/socket")
public class SocketServerController {

    private final SocketServerRegistry registry;

    public SocketServerController(SocketServerRegistry registry) {
        this.registry = registry;
    }

    /**
     * 获取所有在线 socket 服务器地址列表
     * 返回合并了 Redis 动态注册和数据库配置的服务器列表
     */
    @GetMapping("/servers")
    public List<SocketServerInfo> getOnlineServers() {
        List<SocketServerInfo> servers = registry.getAllOnlineServers();
        log.info("[SocketServerController] 返回在线服务器列表: {} 个服务器", servers.size());
        return servers;
    }

    /**
     * 获取统一格式的 socket 地址列表（便于前端使用）
     */
    @GetMapping("/addresses")
    public List<SocketAddressInfo> getSocketAddresses() {
        List<SocketServerInfo> servers = registry.getAllOnlineServers();
        List<SocketAddressInfo> result = new ArrayList<>();

        for (SocketServerInfo server : servers) {
            SocketAddressInfo info = new SocketAddressInfo();
            info.setId(server.getId());
            info.setProtocol(server.getProtocol());
            info.setHost(server.getHost());
            info.setPort(server.getPort());
            info.setPath(server.getPath());
            info.setContext(server.getContext());
            info.setEnable(server.isEnable());
            info.setName(server.getName());

            // 构建完整 URL
            info.setUrl(buildSocketUrl(server));

            result.add(info);
        }

        return result;
    }

    /** 构建 socket URL（带 scheme 前缀） */
    private String buildSocketUrl(SocketServerInfo server) {
        String protocol = server.getProtocol();
        String host = server.getHost();
        int port = server.getPort();
        String path = server.getPath();
        String context = server.getContext();

        if ("sse".equalsIgnoreCase(protocol)) {
            // sse://host:port/path
            if (path != null && !path.isEmpty()) {
                return "sse://" + host + ":" + port + path;
            }
            return "sse://" + host + ":" + port + "/sse/connect";
        } else if ("socketio".equalsIgnoreCase(protocol)) {
            // socket://host:port/context
            if (context != null && !context.isEmpty()) {
                return "socket://" + host + ":" + port + context;
            }
            return "socket://" + host + ":" + port + "/socket.io";
        } else if ("websocket".equalsIgnoreCase(protocol)) {
            // ws://host:port/path
            if (path != null && !path.isEmpty()) {
                return "ws://" + host + ":" + port + path;
            }
            return "ws://" + host + ":" + port + "/ws";
        }

        // 默认返回 sse
        return "sse://" + host + ":" + port + "/sse/connect";
    }

    public static class SocketAddressInfo {
        private String id;
        private String protocol;
        private String host;
        private int port;
        private String path;
        private String context;
        private boolean enable;
        private String name;
        private String url;

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

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }
}

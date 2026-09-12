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
   * 套接字 服务器信息获取接口
   * 前端通过此接口获取所有在线 套接字 服务器地址列表
 * @author CH
 * @since 4.0.0
 */
@Slf4j
@RestController
@RequestMapping("/socket")
public class SocketServerController {

    private final SocketServerRegistry registry; // registry

    /**
     * 套接字服务端控制器。
     * @param registry registry
     */
    public SocketServerController(SocketServerRegistry registry) {
        this.registry = registry;
    }

    /**
      * 获取所有在线 套接字 服务器地址列表
     * 返回合并了 Redis 动态注册和数据库配置的服务器列表
     * @return 获取online服务端的结果
     */
    @GetMapping("/servers")
    public List<SocketServerInfo> getOnlineServers() {
        List<SocketServerInfo> servers = registry.getAllOnlineServers();
        log.info("[SocketServerController] 返回在线服务器列表: {} 个服务器", servers.size());
        return servers;
    }

    /**
      * 获取统一格式的 套接字 地址列表（便于前端使用）
     * @return 获取套接字地址的结果
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

    /**
     * 构建 套接字 URL（带 scheme 前缀）
     *
     * @param server 服务端
     * @return 构建套接字url的结果
     */
    private String buildSocketUrl(SocketServerInfo server) {
        String protocol = server.getProtocol();
        String host = server.getHost();
        int port = server.getPort();
        String path = server.getPath();
        String context = server.getContext();

        if ("sse".equalsIgnoreCase(protocol)) {
 // sse://主机:端口/路径
            if (path != null && !path.isEmpty()) {
                return "sse://" + host + ":" + port + path;
            }
            return "sse://" + host + ":" + port + "/sse/connect";
        } else if ("socketio".equalsIgnoreCase(protocol)) {
 // 套接字://主机:端口/上下文
            if (context != null && !context.isEmpty()) {
                return "socket://" + host + ":" + port + context;
            }
            return "socket://" + host + ":" + port + "/socket.io";
        } else if ("websocket".equalsIgnoreCase(protocol)) {
 // ws://主机:端口/路径
            if (path != null && !path.isEmpty()) {
                return "ws://" + host + ":" + port + path;
            }
            return "ws://" + host + ":" + port + "/ws";
        }

        // 默认返回 sse
        return "sse://" + host + ":" + port + "/sse/connect";
    }

    /**
     * 套接字地址信息类。
     *
     * @author CH
     * @since 4.0.0
     */
    public static class SocketAddressInfo {
        private String id; // 标识
        private String protocol; // 协议
        private String host; // 主机
        private int port; // 端口
        private String path; // 路径
        private String context; // 上下文
        private boolean enable; // enable
        private String name; // 名称
        private String url; // url

        /**
          * 获取标识。
         * @return 获取id的结果
         */
        public String getId() {
            return id;
        }

        /**
          * 设置标识。
         * @param id 标识
         */
        public void setId(String id) {
            this.id = id;
        }

        /**
         * 获取协议。
         * @return 获取协议的结果
         */
        public String getProtocol() {
            return protocol;
        }

        /**
         * 设置协议。
         * @param protocol 协议
         */
        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }

        /**
         * 获取主机。
         * @return 获取主机的结果
         */
        public String getHost() {
            return host;
        }

        /**
         * 设置主机。
         * @param host 主机
         */
        public void setHost(String host) {
            this.host = host;
        }

        /**
         * 获取端口。
         * @return 获取端口的结果
         */
        public int getPort() {
            return port;
        }

        /**
         * 设置端口。
         * @param port 端口
         */
        public void setPort(int port) {
            this.port = port;
        }

        /**
         * 获取路径。
         * @return 获取路径的结果
         */
        public String getPath() {
            return path;
        }

        /**
         * 设置路径。
         * @param path 路径
         */
        public void setPath(String path) {
            this.path = path;
        }

        /**
         * 获取上下文。
         * @return 获取上下文的结果
         */
        public String getContext() {
            return context;
        }

        /**
         * 设置上下文。
         * @param context 上下文
         */
        public void setContext(String context) {
            this.context = context;
        }

        /**
         * 是否enable。
         * @return 是否enable的结果
         */
        public boolean isEnable() {
            return enable;
        }

        /**
         * 设置enable。
         * @param enable enable
         */
        public void setEnable(boolean enable) {
            this.enable = enable;
        }

        /**
         * 获取名称。
         * @return 获取名称的结果
         */
        public String getName() {
            return name;
        }

        /**
         * 设置名称。
         * @param name 名称
         */
        public void setName(String name) {
            this.name = name;
        }

        /**
         * 获取url。
         * @return 获取url的结果
         */
        public String getUrl() {
            return url;
        }

        /**
         * 设置url。
         * @param url url
         */
        public void setUrl(String url) {
            this.url = url;
        }
    }
}

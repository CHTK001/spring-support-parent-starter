package com.chua.starter.gateway.support.server;

import com.chua.common.support.protocol.Protocol;
import com.chua.common.support.protocol.ProtocolSetting;
import com.chua.common.support.protocol.server.ProtocolServer;
import com.chua.starter.gateway.support.properties.GatewayProperties;
import com.chua.starter.gateway.support.route.DiscoveryRouteLocator;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网关服务端管理器
 * <p>
 * 管理多个代理服务端实例，支持HTTP和TCP代理。
 *
 * @author CH
 * @since 2024/12/26
 */
@Slf4j
public class GatewayServerManager implements AutoCloseable {

    private final GatewayProperties properties;
    private final DiscoveryRouteLocator routeLocator;
    private final Map<Integer, ProtocolServer> servers = new ConcurrentHashMap<>();

    public GatewayServerManager(GatewayProperties properties, DiscoveryRouteLocator routeLocator) {
        this.properties = properties;
        this.routeLocator = routeLocator;
    }

    /**
     * 启动所有配置的代理服务器
     */
    public void start() {
        List<GatewayProperties.PortConfig> ports = properties.getServer().getPorts();
        if (ports == null || ports.isEmpty()) {
            log.warn("未配置任何网关代理端口");
            return;
        }

        for (GatewayProperties.PortConfig portConfig : ports) {
            if (!portConfig.isEnabled()) {
                log.debug("端口 {} 未启用", portConfig.getPort());
                continue;
            }

            try {
                startServer(portConfig);
            } catch (Exception e) {
                log.error("启动网关代理端口 {} 失败: {}", portConfig.getPort(), e.getMessage(), e);
            }
        }
    }

    /**
     * 启动单个代理服务器
     */
    private void startServer(GatewayProperties.PortConfig portConfig) throws Exception {
        String protocol = portConfig.getProtocol().toLowerCase();
        String host = properties.getServer().getHost();
        int port = portConfig.getPort();

        log.info("启动网关代理服务器 - 协议: {}, 端口: {}", protocol, port);

        // 根据协议类型创建对应的代理服务器
        String protocolType = "http".equals(protocol) ? "http-proxy" : "tcp-proxy";

        // 创建协议设置，包含服务器配置
        ProtocolSetting protocolSetting = ProtocolSetting.builder()
                .protocol(protocolType)
                .host(host)
                .port(port)
                .build();

        Protocol protocolInstance = Protocol.create(protocolType, protocolSetting);

        if (protocolInstance == null) {
            log.error("不支持的协议类型: {}", protocolType);
            return;
        }

        ProtocolServer server = protocolInstance.createServer(protocolSetting);
        if (server == null) {
            log.error("创建代理服务器失败: {}", protocolType);
            return;
        }

        // 启动服务器
        server.start();
        servers.put(port, server);

        log.info("网关代理服务器启动成功 - {}:{}", host, port);
    }

    /**
     * 停止所有代理服务器
     */
    public void stop() {
        for (Map.Entry<Integer, ProtocolServer> entry : servers.entrySet()) {
            try {
                entry.getValue().stop();
                log.info("停止网关代理端口: {}", entry.getKey());
            } catch (Exception e) {
                log.warn("停止网关代理端口 {} 失败: {}", entry.getKey(), e.getMessage());
            }
        }
        servers.clear();
    }

    /**
     * 获取运行中的服务器端口列表
     */
    public List<Integer> getRunningPorts() {
        return new ArrayList<>(servers.keySet());
    }

    /**
     * 检查指定端口是否运行中
     */
    public boolean isPortRunning(int port) {
        ProtocolServer server = servers.get(port);
        return server != null && server.isRunning();
    }

    @Override
    public void close() throws Exception {
        stop();
    }
}

package com.chua.starter.gateway.support.server;

import com.chua.common.support.protocol.Protocol;
import com.chua.common.support.protocol.ProtocolSetting;
import com.chua.common.support.protocol.server.ProtocolServer;
import com.chua.starter.gateway.support.properties.GatewayProperties;
import com.chua.starter.gateway.support.route.DiscoveryRouteLocator;
import lombok.extern.slf4j.Slf4j;

import static com.chua.starter.common.support.logger.ModuleLog.*;
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
            log.warn("[Gateway] 未配置代理端口, 跳过启动");
            return;
        }

        log.info("[Gateway] 启动网关代理服务, 配置 {} 个端口", highlight(ports.size()));
        for (GatewayProperties.PortConfig portConfig : ports) {
            if (!portConfig.isEnabled()) {
                log.debug("[Gateway] 端口 {} [{}]", portConfig.getPort(), disabled());
                continue;
            }

            try {
                startServer(portConfig);
            } catch (Exception e) {
                log.error("[Gateway] 启动端口 {} {}: {}", portConfig.getPort(), failed(), e.getMessage(), e);
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

        log.info("[Gateway] 启动代理 - 协议: {}, 地址: {}", highlight(protocol), address(host, port));

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
            log.error("[Gateway] 不支持的协议类型: {}", protocolType);
            return;
        }

        ProtocolServer server = protocolInstance.createServer(protocolSetting);
        if (server == null) {
            log.error("[Gateway] 创建代理服务器失败: {}", protocolType);
            return;
        }

        // 启动服务器
        server.start();
        servers.put(port, server);

        log.info("[Gateway] 代理服务启动 {} - {}", success(), address(host, port));
    }

    /**
     * 停止所有代理服务器
     */
    public void stop() {
        for (Map.Entry<Integer, ProtocolServer> entry : servers.entrySet()) {
            try {
                entry.getValue().stop();
                log.info("[Gateway] 停止代理端口: {}", entry.getKey());
            } catch (Exception e) {
                log.warn("[Gateway] 停止端口 {} {}: {}", entry.getKey(), failed(), e.getMessage());
            }
        }
        servers.clear();
        log.info("[Gateway] 网关服务已停止");
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

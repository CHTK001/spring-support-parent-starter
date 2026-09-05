package com.chua.starter.rpc.support.monitor;

import com.chua.common.support.network.rpc.RpcConnectionInfo;
import com.chua.common.support.network.rpc.RpcMetrics;
import com.chua.common.support.network.rpc.RpcServer;
import com.chua.starter.rpc.support.interceptor.MetricsRpcInterceptor;
import com.chua.starter.rpc.support.service.RpcServiceBean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * RPC 监控端点，暴露当前 RPC 服务端的连接信息与运行时指标。
 *
 * <ul>
 *   <li>{@code GET /v2/rpc/monitor/info} — 服务端基本信息（协议、服务列表）</li>
 *   <li>{@code GET /v2/rpc/monitor/connections} — 当前连接信息</li>
 *   <li>{@code GET /v2/rpc/monitor/metrics} — 运行时指标（含方法级调用统计）</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequestMapping("/v2/rpc/monitor")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.rpc", name = "enable", havingValue = "true", matchIfMissing = true)
public class RpcMonitorController {

    /**
     * 应用上下文（用于枚举已暴露的 RpcServiceBean）
     */
    private final ApplicationContext applicationContext;

    /**
     * 方法级指标拦截器（可选，未注册时跳过方法统计）
     */
    private final ObjectProvider<MetricsRpcInterceptor> metricsRpcInterceptor;

    /**
     * 获取 RPC 服务端基本信息。
     *
     * @return 服务端基本信息
     */
    @GetMapping("/info")
    public MonitorInfo info() {
        RpcServer server = currentServer();
        if (server == null) {
            return new MonitorInfo(false, null, 0, List.of());
        }
        return new MonitorInfo(true, server.getProtocol(), server.getServiceCount(), collectServices());
    }

    /**
     * 获取当前连接信息。
     *
     * @return 连接信息
     */
    @GetMapping("/connections")
    public ConnectionsInfo connections() {
        RpcServer server = currentServer();
        if (server == null) {
            return new ConnectionsInfo(false, null, 0, List.of());
        }
        List<RpcConnectionInfo> connections = server.getConnections();
        return new ConnectionsInfo(true, server.getProtocol(), connections.size(), connections);
    }

    /**
     * 获取运行时指标。
     *
     * @return 指标快照（含方法级调用统计）
     */
    @GetMapping("/metrics")
    public MetricsInfo metrics() {
        RpcServer server = currentServer();
        if (server == null) {
            return new MetricsInfo(false, new RpcMetrics("unknown"));
        }
        RpcMetrics metrics = server.getMetrics();
        List<RpcMetrics.MethodStat> methodStats = new ArrayList<>(metrics.getMethodStats());
        MetricsRpcInterceptor interceptor = metricsRpcInterceptor.getIfAvailable();
        if (interceptor != null) {
            methodStats.addAll(interceptor.getMethodStats());
        }
        metrics.setMethodStats(methodStats);
        return new MetricsInfo(true, metrics);
    }

    /**
     * 获取当前激活的 RPC 服务端实例。
     *
     * @return RPC 服务端实例，未启用时返回 {@code null}
     */
    private RpcServer currentServer() {
        RpcServer server = RpcServerHolder.getRpcServer();
        if (server == null) {
            log.warn("[RPC] 监控端点被调用但 RPC 服务端未初始化");
        }
        return server;
    }

    /**
     * 收集已暴露的服务接口列表。
     *
     * @return 服务接口全限定名列表
     */
    private List<String> collectServices() {
        List<String> services = new ArrayList<>();
        for (RpcServiceBean<?> serviceBean : applicationContext.getBeansOfType(RpcServiceBean.class).values()) {
            String interfaceName = serviceBean.getInterfaceName();
            if (interfaceName != null && !interfaceName.isEmpty()) {
                services.add(interfaceName);
            }
        }
        return services;
    }

    /**
     * 服务端基本信息。
     *
     * @param enabled      RPC 是否启用
     * @param protocol     协议名称
     * @param serviceCount 服务数量
     * @param services     服务接口全限定名列表
     */
    public record MonitorInfo(boolean enabled, String protocol, int serviceCount, List<String> services) {
    }

    /**
     * 连接信息响应。
     *
     * @param enabled     RPC 是否启用
     * @param protocol    协议名称
     * @param count       连接数量
     * @param connections 连接详情列表
     */
    public record ConnectionsInfo(boolean enabled, String protocol, int count,
                                  List<RpcConnectionInfo> connections) {
    }

    /**
     * 指标响应。
     *
     * @param enabled RPC 是否启用
     * @param metrics 指标快照
     */
    public record MetricsInfo(boolean enabled, RpcMetrics metrics) {
    }
}
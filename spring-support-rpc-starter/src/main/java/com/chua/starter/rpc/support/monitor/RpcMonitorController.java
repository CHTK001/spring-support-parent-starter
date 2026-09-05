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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
     * @return 基本信息映射
     */
    @GetMapping("/info")
    public Map<String, Object> info() {
        RpcServer server = currentServer();
        Map<String, Object> result = new LinkedHashMap<>();
        if (server == null) {
            result.put("enabled", false);
            return result;
        }
        result.put("enabled", true);
        result.put("protocol", server.getProtocol());
        result.put("serviceCount", server.getServiceCount());
        result.put("services", collectServices());
        return result;
    }

    /**
     * 获取当前连接信息。
     *
     * @return 连接信息映射
     */
    @GetMapping("/connections")
    public Map<String, Object> connections() {
        RpcServer server = currentServer();
        Map<String, Object> result = new LinkedHashMap<>();
        if (server == null) {
            result.put("enabled", false);
            return result;
        }
        List<RpcConnectionInfo> connections = server.getConnections();
        result.put("enabled", true);
        result.put("protocol", server.getProtocol());
        result.put("count", connections.size());
        result.put("connections", connections);
        return result;
    }

    /**
     * 获取运行时指标。
     *
     * @return 指标映射
     */
    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        RpcServer server = currentServer();
        Map<String, Object> result = new LinkedHashMap<>();
        if (server == null) {
            result.put("enabled", false);
            return result;
        }
        RpcMetrics metrics = server.getMetrics();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("protocol", metrics.getProtocol());
        payload.put("startTime", metrics.getStartTime());
        payload.put("totalCalls", metrics.getTotalCalls());
        payload.put("successCalls", metrics.getSuccessCalls());
        payload.put("failureCalls", metrics.getFailureCalls());
        payload.put("activeCalls", metrics.getActiveCalls());
        payload.put("totalConnections", metrics.getTotalConnections());
        payload.put("serviceCount", metrics.getServiceCount());
        List<RpcMetrics.MethodStat> methodStats = new ArrayList<>(metrics.getMethodStats());
        MetricsRpcInterceptor interceptor = metricsRpcInterceptor.getIfAvailable();
        if (interceptor != null) {
            methodStats.addAll(interceptor.getMethodStats());
        }
        payload.put("methodStats", methodStats);
        result.put("enabled", true);
        result.put("metrics", payload);
        return result;
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
}
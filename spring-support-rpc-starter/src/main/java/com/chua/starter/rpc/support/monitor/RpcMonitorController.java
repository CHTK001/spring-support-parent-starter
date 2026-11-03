// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
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
import java.util.Objects;

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
     * 应用上下文（用于枚举已暴露的 rpc服务Bean）
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
            /**
             * 监控信息。
             * @param false false
             * @param null 空
             * @param 0 0
             * @param List.of( 列表.的(
             * @return 监控信息的结果
             */
            return new MonitorInfo(false, null, 0, List.of());
        }
        /**
         * 监控信息。
         * @param true true
         * @param server.getProtocol( 服务端.获取协议(
         * @return 监控信息的结果
         */
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
            /**
             * connections信息。
             * @param false false
             * @param null 空
             * @param 0 0
             * @param List.of( 列表.的(
             * @return connections信息的结果
             */
            return new ConnectionsInfo(false, null, 0, List.of());
        }
        List<RpcConnectionInfo> connections = server.getConnections();
        /**
         * connections信息。
         * @param true true
         * @param server.getProtocol( 服务端.获取协议(
         * @return connections信息的结果
         */
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
            /**
             * 指标信息。
             * @param false false
             * @param RpcMetrics("unknown" rpc指标("unknown"
             * @return 指标信息的结果
             */
            return new MetricsInfo(false, new RpcMetrics("unknown"));
        }
        RpcMetrics metrics = server.getMetrics();
        Map<String, RpcMetrics.MethodStat> merged = new LinkedHashMap<>();
        for (RpcMetrics.MethodStat stat : metrics.getMethodStats()) {
            merged.putIfAbsent(stat.method(), stat);
        }
        MetricsRpcInterceptor interceptor = metricsRpcInterceptor.getIfAvailable();
        if (interceptor != null) {
            for (RpcMetrics.MethodStat stat : interceptor.getMethodStats()) {
 // 协议层（过滤器）统计优先；客户端拦截器仅补充协议层未覆盖的方法
                merged.putIfAbsent(stat.method(), stat);
            }
        }
        metrics.setMethodStats(new ArrayList<>(merged.values()));
        /**
         * 指标信息。
         * @param true true
         * @param metrics 指标
         * @return 指标信息的结果
         */
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
     * @return 监控信息的结果
     */
    public record MonitorInfo(boolean enabled, String protocol, int serviceCount, List<String> services) {

        /**
         * 规范构造器：服务接口列表做防御性拷贝。
         *
         * <p>value class 前置条件——集合组件必须深不可变。协议名称保留可空语义：
         * RPC 未启用时为 {@code null}。</p>
         *
         * @param services 服务接口全限定名列表
         */
        public MonitorInfo {
            services = List.copyOf(Objects.requireNonNull(services, "services 不能为 null"));
        }
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

        /**
         * 规范构造器：连接详情列表做防御性拷贝。
         *
         * <p>value class 前置条件——集合组件必须深不可变。协议名称保留可空语义：
         * RPC 未启用时为 {@code null}。</p>
         *
         * @param connections 连接详情列表
         */
        public ConnectionsInfo {
            connections = List.copyOf(Objects.requireNonNull(connections, "connections 不能为 null"));
        }
    }

    /**
     * 指标响应。
     *
     * @param enabled RPC 是否启用
     * @param metrics 指标快照
     * @return 指标信息的结果
     */
    public record MetricsInfo(boolean enabled, RpcMetrics metrics) {
    }
}

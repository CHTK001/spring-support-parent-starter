package com.chua.starter.monitor.support.node;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.sync.support.server.SyncServer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 内部服务发现接口。
 *
 * <p>把 report 客户端注册的在线节点当作服务实例输出，供内部消费方做服务发现；
 * 不依赖外部注册中心（Consul/Eureka/Nacos 等）。</p>
 *
 * <p>契约与前端 {@code @pages/server} 的 DiscoveryStatus / DiscoveryInstance 对齐。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Tag(name = "监控-节点发现", description = "内部发现接口：report 注册节点即服务实例")
@RestController
@RequestMapping("/ms/monitor/node/discovery")
@RequiredArgsConstructor
public class MonitorDiscoveryController {

    /**
     * 发现协议名：report 客户端注册
     */
    private static final String PROTOCOL_REPORT = "report";

    private final MonitorNodeRegistry registry;
    private final ObjectProvider<SyncServer> syncServerProvider;

    /**
     * 发现服务状态。
     *
     * @return 状态
     */
    @Operation(summary = "发现服务状态")
    @GetMapping("/status")
    public ReturnResult<DiscoveryStatusVO> status() {
        boolean present = syncServerProvider.getIfAvailable() != null;
        return ReturnResult.ok(new DiscoveryStatusVO(present ? 1 : 0,
                present ? List.of(PROTOCOL_REPORT) : List.of(), present, present));
    }

    /**
     * 可用发现协议。
     *
     * @return 协议列表
     */
    @Operation(summary = "可用发现协议")
    @GetMapping("/protocols")
    public ReturnResult<List<String>> protocols() {
        return ReturnResult.list(syncServerProvider.getIfAvailable() == null
                ? java.util.Collections.emptyList()
                : List.of(PROTOCOL_REPORT));
    }

    /**
     * 查询服务实例（在线节点）。
     *
     * @param protocol 协议（可选，仅 report）
     * @param uriSpec  服务标识（可选，按应用名包含匹配）
     * @return 实例列表
     */
    @Operation(summary = "查询服务实例")
    @GetMapping("/services")
    public ReturnResult<DiscoveryQueryResultVO> services(
            @RequestParam(required = false) String protocol,
            @RequestParam(required = false) String uriSpec) {
        if (protocol != null && !protocol.isBlank() && !PROTOCOL_REPORT.equalsIgnoreCase(protocol)) {
            return ReturnResult.ok(new DiscoveryQueryResultVO(List.of(), "未知协议: " + protocol));
        }
        List<DiscoveryInstanceVO> instances = new ArrayList<>();
        for (MonitorNodeVO node : registry.online()) {
            if (uriSpec != null && !uriSpec.isBlank()
                    && (node.applicationName() == null || !node.applicationName().contains(uriSpec))) {
                continue;
            }
            instances.add(new DiscoveryInstanceVO(node.clientId(), node.ipAddress(),
                    node.port() == null ? 0 : node.port(), PROTOCOL_REPORT, 1,
                    node.activeProfiles(), node.applicationName(), node.metadata()));
        }
        return ReturnResult.ok(new DiscoveryQueryResultVO(instances, "ok"));
    }
}

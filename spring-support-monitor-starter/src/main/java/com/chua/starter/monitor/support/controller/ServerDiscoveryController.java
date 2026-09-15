package com.chua.starter.monitor.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.network.discovery.Discovery;
import com.chua.common.support.network.discovery.ServiceDiscovery;
import com.chua.common.support.network.server.filter.ServerFilter;
import com.chua.common.support.network.server.filter.discovery.ServiceDiscoveryServerFilter;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.discovery.support.service.DiscoveryService;
import com.chua.starter.monitor.support.server.ServerNodeRegistry;
import com.chua.starter.monitor.support.server.ServerNodeRegistry.RuntimeServer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
* 服务管理 —— 服务发现的独立查询接口。
*
* <p>「服务发现」不只是 ServerFilter 的配置项，还需要独立接口对外暴露运行态：
* 已启用的发现协议、按服务标识实时查询实例列表、以及整体状态摘要。前端
* {@code ScFilterConfigPanel} 的「服务实例（实时查询）」分区即消费本接口。</p>
*
* <p>实例来源按优先级解析：</p>
* <ol>
*   <li>运行中节点上挂载的 {@link ServiceDiscoveryServerFilter}（复用其已建立的发现连接，
*       能拿到真正注册过的实例）；</li>
*   <li>Spring 容器中的 {@link DiscoveryService}（{@code spring-support-discovery-starter} 提供）。</li>
* </ol>
*
* @author CH
* @since 4.0.0.42
 */
@Slf4j
@Tag(name = "监控-服务发现", description = "服务发现的独立查询接口：协议枚举 / 实例查询 / 状态摘要")
@RestController
@RequestMapping("/ms/monitor/discovery")
@RequiredArgsConstructor
public class ServerDiscoveryController {

    /** 运行时注册表（从中取运行中节点挂载的发现过滤器） */
    private final ServerNodeRegistry nodeRegistry;
    /** 发现服务（可选，缺失时降级） */
    private final ObjectProvider<DiscoveryService> discoveryServiceProvider;

    /**
    * 已启用的发现协议列表。
    *
    * <p>由两部分合并：SPI 注册的 {@link ServiceDiscovery} 实现名，以及运行中节点
    * 实际使用的发现源名称。</p>
    *
    * @return 协议名列表
     */
    @Operation(summary = "已启用的发现协议", description = "SPI 实现名 + 运行中节点实际使用的发现源")
    @GetMapping("/protocols")
    public ReturnResult<List<String>> protocols() {
        Set<String> names = new LinkedHashSet<>();
        try {
            ServiceProvider.of(ServiceDiscovery.class).forDefinitionEach(definition -> {
                String name = definition.getName();
                if (name != null && !name.isBlank()) {
                    names.add(name);
                }
            });
        } catch (Exception e) {
            log.warn("[ServerDiscovery] 枚举 ServiceDiscovery SPI 失败: {}", e.getMessage());
        }
        for (ServiceDiscoveryServerFilter filter : liveDiscoveryFilters(null)) {
            try {
                String name = filter.getDiscoveryName();
                if (name != null && !name.isBlank()) {
                    names.add(name);
                }
            } catch (Throwable t) {
                // 兼容旧副本类导致的方法缺失
                log.warn("[ServerDiscovery] 读取发现源名称失败: {}", t.getMessage());
            }
        }
        return ReturnResult.ok(new ArrayList<>(names));
    }

    /**
    * 按发现协议 / 服务标识实时查询实例。
    *
    * @param protocol 发现协议（SPI 名，可为空表示不限）
    * @param uriSpec  服务标识（注册时使用的服务路径）
    * @return 实例列表与说明
     */
    @Operation(summary = "查询服务实例", description = "按发现协议与/或服务标识查询实时实例；复用运行中节点的发现连接")
    @GetMapping("/services")
    public ReturnResult<Map<String, Object>> services(@RequestParam(required = false) String protocol,
                                                      @RequestParam(required = false) String uriSpec) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> instances = new ArrayList<>();
        String message;

        if (uriSpec == null || uriSpec.isBlank()) {
            message = "请提供服务标识(uriSpec)后再查询";
            result.put("instances", instances);
            result.put("message", message);
            return ReturnResult.ok(result);
        }

        Set<Discovery> found = new LinkedHashSet<>();
        List<ServiceDiscoveryServerFilter> filters = liveDiscoveryFilters(protocol);
        for (ServiceDiscoveryServerFilter filter : filters) {
            ServiceDiscovery discovery;
            try {
                discovery = filter.getServiceDiscovery();
            } catch (Throwable t) {
                // 兼容旧副本类（部分下游 fat jar 内嵌同名类）导致的方法缺失
                log.warn("[ServerDiscovery] 读取发现实例失败: {}", t.getMessage());
                continue;
            }
            if (discovery == null) {
                continue;
            }
            try {
                Set<Discovery> all = discovery.getServiceAll(uriSpec.trim());
                if (all != null) {
                    found.addAll(all);
                }
            } catch (Exception e) {
                log.warn("[ServerDiscovery] 查询实例失败: uriSpec={} - {}", uriSpec, e.getMessage());
            }
        }

        if (found.isEmpty()) {
            DiscoveryService discoveryService = discoveryServiceProvider.getIfAvailable();
            if (discoveryService != null) {
                try {
                    Set<Discovery> all = discoveryService.getDiscoveryAll(protocol, uriSpec.trim());
                    if (all != null) {
                        found.addAll(all);
                    }
                } catch (Exception e) {
                    log.warn("[ServerDiscovery] DiscoveryService 查询失败: uriSpec={} - {}", uriSpec, e.getMessage());
                }
            }
        }

        for (Discovery d : found) {
            instances.add(toInstance(d));
        }

        if (instances.isEmpty()) {
            message = filters.isEmpty()
                    ? "未发现运行中的服务发现插件，请先在服务管理中为某个服务器挂载 service-discovery 插件"
                    : "未查询到匹配实例，请确认服务标识或发现源配置";
        } else {
            message = "ok";
        }
        result.put("instances", instances);
        result.put("message", message);
        return ReturnResult.ok(result);
    }

    /**
    * 发现服务状态摘要。
    *
    * @return 状态
     */
    @Operation(summary = "发现服务状态", description = "运行中的发现插件数量、可用协议数、容器内是否装配 DiscoveryService")
    @GetMapping("/status")
    public ReturnResult<Map<String, Object>> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<ServiceDiscoveryServerFilter> filters = liveDiscoveryFilters(null);
        Set<String> protocols = new LinkedHashSet<>();
        try {
            ServiceProvider.of(ServiceDiscovery.class).forDefinitionEach(d -> {
                if (d.getName() != null && !d.getName().isBlank()) {
                    protocols.add(d.getName());
                }
            });
        } catch (Exception ignored) {
            // 枚举失败不影响状态摘要
        }
        result.put("runningDiscoveryFilters", filters.size());
        result.put("availableProtocols", new ArrayList<>(protocols));
        result.put("discoveryServicePresent", discoveryServiceProvider.getIfAvailable() != null);
        result.put("enabled", !filters.isEmpty() || discoveryServiceProvider.getIfAvailable() != null);
        return ReturnResult.ok(result);
    }

    /**
    * 收集运行中节点上挂载的发现过滤器。
    *
    * @param protocol 发现协议过滤（SPI 名，可为空表示不限）
    * @return 发现过滤器列表
     */
    private List<ServiceDiscoveryServerFilter> liveDiscoveryFilters(String protocol) {
        List<ServiceDiscoveryServerFilter> result = new ArrayList<>();
        Collection<RuntimeServer> runtimes;
        try {
            runtimes = nodeRegistry.snapshot();
        } catch (Exception e) {
            return result;
        }
        if (runtimes == null) {
            return result;
        }
        for (RuntimeServer runtime : runtimes) {
            if (runtime == null || !runtime.isRunning()) {
                continue;
            }
            List<ServerFilter> filters = runtime.getManagedFilters();
            if (filters == null) {
                continue;
            }
            for (ServerFilter filter : filters) {
                if (!(filter instanceof ServiceDiscoveryServerFilter discoveryFilter)) {
                    continue;
                }
                if (protocol != null && !protocol.isBlank()) {
                    String name;
                    try {
                        name = discoveryFilter.getDiscoveryName();
                    } catch (Throwable t) {
                        // 兼容旧副本类导致的方法缺失
                        continue;
                    }
                    if (!protocol.trim().equalsIgnoreCase(name)) {
                        continue;
                    }
                }
                result.add(discoveryFilter);
            }
        }
        return result;
    }

    /**
    * {@link Discovery} → 前端实例视图。
    *
    * @param d 发现信息
    * @return 实例视图
     */
    private Map<String, Object> toInstance(Discovery d) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", d.getId());
        item.put("host", d.getHost());
        item.put("port", d.getPort());
        item.put("protocol", d.getProtocol());
        item.put("weight", d.getWeight());
        item.put("env", d.getEnv());
        item.put("uriSpec", d.getUriSpec());
        item.put("metadata", d.getMetadata());
        return item;
    }
}

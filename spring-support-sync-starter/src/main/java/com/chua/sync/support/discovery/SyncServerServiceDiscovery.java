package com.chua.sync.support.discovery;

import com.chua.common.support.discovery.Discovery;
import com.chua.common.support.discovery.ServiceDiscovery;
import com.chua.common.support.lang.robin.LoadBalance;
import com.chua.common.support.lang.robin.Robin;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.MapUtils;
import com.chua.common.support.utils.ObjectUtils;
import com.chua.common.support.utils.RandomUtils;
import com.chua.sync.support.pojo.ClientInfo;
import com.chua.sync.support.server.SyncServer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 基于 SyncServer 的服务发现实现
 * <p>
 * 使用 SyncServer 维护的长连接客户端作为服务节点，
 * 通过应用名称(clientApplicationName)进行服务分组
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/08
 */
@Slf4j
public class SyncServerServiceDiscovery implements ServiceDiscovery {

    /**
     * SyncServer 实例
     */
    @Getter
    private final SyncServer syncServer;

    /**
     * 本地注册的服务（用于兼容 registerService 方法）
     */
    private final Map<String, Set<Discovery>> localRegistrations = new ConcurrentHashMap<>();

    /**
     * 是否已启动
     */
    private volatile boolean started = false;

    /**
     * 构造函数
     *
     * @param syncServer SyncServer 实例
     */
    public SyncServerServiceDiscovery(SyncServer syncServer) {
        this.syncServer = syncServer;
        log.info("[SyncServerServiceDiscovery] 初始化，SyncServer 连接数: {}", 
                syncServer != null ? syncServer.getConnectionCount() : 0);
    }

    @Override
    public void start() throws Exception {
        if (started) {
            return;
        }
        started = true;
        log.info("[SyncServerServiceDiscovery] 启动成功，当前连接数: {}", 
                syncServer != null ? syncServer.getConnectionCount() : 0);
    }

    @Override
    public ServiceDiscovery registerService(String path, Discovery discovery) {
        localRegistrations.computeIfAbsent(path, k -> ConcurrentHashMap.newKeySet()).add(discovery);
        log.debug("[SyncServerServiceDiscovery] 注册服务: path={}, discovery={}", path, discovery);
        return this;
    }

    @Override
    public Discovery getService(String path, String balance, String protocol) {
        Set<Discovery> allServices = getServiceAll(path);
        if (CollectionUtils.isEmpty(allServices)) {
            log.debug("[SyncServerServiceDiscovery] 未找到服务: path={}", path);
            return null;
        }

        // 根据负载均衡策略选择服务
        return selectByBalance(allServices, balance);
    }

    @Override
    public Set<Discovery> getServiceAll(String path) {
        Set<Discovery> result = new LinkedHashSet<>();

        // 1. 先从 SyncServer 的长连接客户端中获取
        if (syncServer != null) {
            Map<String, ClientInfo> allClients = syncServer.getAllClients();
            if (MapUtils.isNotEmpty(allClients)) {
                for (ClientInfo clientInfo : allClients.values()) {
                    // 通过应用名称匹配服务路径
                    if (matchService(path, clientInfo)) {
                        Discovery discovery = convertToDiscovery(clientInfo);
                        result.add(discovery);
                    }
                }
            }
        }

        // 2. 合并本地注册的服务
        Set<Discovery> localServices = localRegistrations.get(path);
        if (CollectionUtils.isNotEmpty(localServices)) {
            result.addAll(localServices);
        }

        log.debug("[SyncServerServiceDiscovery] 获取服务: path={}, count={}", path, result.size());
        return result;
    }

    /**
     * 判断客户端是否匹配指定的服务路径
     *
     * @param path       服务路径/应用名称
     * @param clientInfo 客户端信息
     * @return 是否匹配
     */
    private boolean matchService(String path, ClientInfo clientInfo) {
        if (clientInfo == null || !clientInfo.isClientOnline()) {
            return false;
        }

        String appName = clientInfo.getClientApplicationName();
        if (appName == null) {
            return false;
        }

        // 精确匹配或路径前缀匹配
        return appName.equalsIgnoreCase(path) 
                || appName.equalsIgnoreCase("/" + path)
                || path.equalsIgnoreCase("/" + appName);
    }

    /**
     * 将 ClientInfo 转换为 Discovery
     *
     * @param clientInfo 客户端信息
     * @return Discovery 实例
     */
    private Discovery convertToDiscovery(ClientInfo clientInfo) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("clientId", ObjectUtils.defaultIfNull(clientInfo.getClientId(), ""));
        metadata.put("instanceId", ObjectUtils.defaultIfNull(clientInfo.getClientInstanceId(), ""));
        metadata.put("hostname", ObjectUtils.defaultIfNull(clientInfo.getClientHostname(), ""));
        metadata.put("contextPath", ObjectUtils.defaultIfNull(clientInfo.getClientContextPath(), ""));
        metadata.put("actuatorPath", ObjectUtils.defaultIfNull(clientInfo.getClientActuatorPath(), ""));
        metadata.put("osName", ObjectUtils.defaultIfNull(clientInfo.getClientOsName(), ""));
        metadata.put("javaVersion", ObjectUtils.defaultIfNull(clientInfo.getClientJavaVersion(), ""));
        metadata.put("online", String.valueOf(clientInfo.isClientOnline()));
        metadata.put("registerTime", String.valueOf(clientInfo.getClientRegisterTime()));
        metadata.put("lastHeartbeatTime", String.valueOf(clientInfo.getClientLastHeartbeatTime()));

        // 添加扩展元数据
        if (MapUtils.isNotEmpty(clientInfo.getClientMetadata())) {
            clientInfo.getClientMetadata().forEach((k, v) -> {
                if (v != null) {
                    metadata.put(k, String.valueOf(v));
                }
            });
        }

        return Discovery.builder()
                .id(clientInfo.getClientId())
                .serverId(clientInfo.getClientInstanceId())
                .host(clientInfo.getClientIpAddress())
                .port(clientInfo.getClientPort())
                .protocol("http")
                .uriSpec(clientInfo.getClientApplicationName())
                .weight(1.0)
                .metadata(metadata)
                .build();
    }

    /**
     * 根据负载均衡策略选择服务
     *
     * @param services 服务集合
     * @param balance  负载均衡策略
     * @return 选中的服务
     */
    private Discovery selectByBalance(Set<Discovery> services, String balance) {
        if (CollectionUtils.isEmpty(services)) {
            return null;
        }

        List<Discovery> list = new ArrayList<>(services);

        // 使用 SPI 加载负载均衡策略
        if (balance != null && !balance.isEmpty()) {
            try {
                var robin =
                        ServiceProvider.of(LoadBalance.class)
                                .getNewExtension(balance);
                if (robin != null) {
                    return robin.select(list);
                }
            } catch (Exception e) {
                log.debug("[SyncServerServiceDiscovery] 加载负载均衡策略失败: {}, 使用默认随机策略", balance);
            }
        }

        // 默认随机选择
        return list.get(RandomUtils.randomInt(0, list.size()));
    }

    /**
     * 获取所有在线的客户端信息
     *
     * @return 在线客户端列表
     */
    public List<ClientInfo> getOnlineClients() {
        if (syncServer == null) {
            return Collections.emptyList();
        }

        return syncServer.getAllClients().values().stream()
                .filter(ClientInfo::isClientOnline)
                .collect(Collectors.toList());
    }

    /**
     * 获取指定应用的所有在线客户端
     *
     * @param appName 应用名称
     * @return 在线客户端列表
     */
    public List<ClientInfo> getOnlineClientsByAppName(String appName) {
        if (syncServer == null || appName == null) {
            return Collections.emptyList();
        }

        return syncServer.getAllClients().values().stream()
                .filter(c -> c.isClientOnline() && appName.equalsIgnoreCase(c.getClientApplicationName()))
                .collect(Collectors.toList());
    }

    /**
     * 获取所有应用名称
     *
     * @return 应用名称集合
     */
    public Set<String> getApplicationNames() {
        if (syncServer == null) {
            return Collections.emptySet();
        }

        return syncServer.getAllClients().values().stream()
                .filter(ClientInfo::isClientOnline)
                .map(ClientInfo::getClientApplicationName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    @Override
    public void close() throws Exception {
        localRegistrations.clear();
        started = false;
        log.info("[SyncServerServiceDiscovery] 已关闭");
    }

    @Override
    public boolean isSupportSubscribe() {
        return false;
    }
}

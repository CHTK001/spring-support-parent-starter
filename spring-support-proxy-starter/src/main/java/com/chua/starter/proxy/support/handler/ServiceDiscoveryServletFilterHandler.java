package com.chua.starter.proxy.support.handler;

import com.chua.common.support.core.annotation.Spi;
import com.chua.common.support.core.annotation.SpiDefault;
import com.chua.common.support.network.discovery.*;
import com.chua.common.support.network.net.NetAddress;
import com.chua.extension.support.network.discovery.HazelcastServiceDiscovery;
import com.chua.common.support.objects.ConfigureObjectContext;
import com.chua.common.support.network.protocol.filter.ServiceDiscoveryServletFilter;
import com.chua.common.support.network.protocol.filter.ServletFilter;
import com.chua.common.support.core.utils.StringUtils;
import com.chua.spring.support.configuration.SpringBeanUtils;
import com.chua.starter.proxy.support.entity.SystemServerSetting;
import com.chua.starter.proxy.support.entity.SystemServerSettingServiceDiscovery;
import com.chua.starter.proxy.support.entity.SystemServerSettingServiceDiscoveryMapping;
import com.chua.starter.proxy.support.service.server.SystemServerSettingServiceDiscoveryService;

import java.util.List;

/**
 * ServiceDiscovery 过滤器处理器
 *
 * 支持模式：SPRING/TABLE/HAZELCAST，并可配置默认负载均衡策略 balance
 *
 * @author CH
 * @since 2025/8/11 17:01
 */
@Spi("serviceDiscovery")
@SpiDefault
public class ServiceDiscoveryServletFilterHandler implements ServletFilterHandler {

    static HazelcastServiceDiscovery HAZELCAST_SERVICE_DISCOVERY = null;
    /**
     * 根据服务器配置创建 ServiceDiscoveryServletFilter
     *
     * @param setting       设置
     * @param objectContext
     */
    @Override
    public ServletFilter handle(SystemServerSetting setting, ConfigureObjectContext objectContext) {
        Integer serverId = setting.getSystemServerSettingServerId();

        SystemServerSettingServiceDiscoveryService discoveryService =
                SpringBeanUtils.getBean(SystemServerSettingServiceDiscoveryService.class);

        // 读取ServiceDiscovery主配置
        List<SystemServerSettingServiceDiscovery> configs = discoveryService.listByServerId(serverId);
        SystemServerSettingServiceDiscovery config = configs != null && !configs.isEmpty() ? configs.get(0) : null;

        String mode = config != null && StringUtils.isNotBlank(config.getServiceDiscoveryMode())
                ? config.getServiceDiscoveryMode()
                : "SPRING";
        String balance = config != null && StringUtils.isNotBlank(config.getServiceDiscoveryBalance())
                ? config.getServiceDiscoveryBalance()
                : "weight";
        boolean enabled = config == null || Boolean.TRUE.equals(config.getServiceDiscoveryEnabled());

        // 构建 ServiceDiscovery 实例
        ServiceDiscovery serviceDiscovery = createServiceDiscoveryByMode(mode, config, discoveryService, serverId);

        // 构建并返回过滤器
        ServiceDiscoveryServletFilter servletFilter = new ServiceDiscoveryServletFilter(serviceDiscovery)
                .setDefaultBalance(balance)
                .setEnabled(enabled)
                .setProtocolType("http");

        return servletFilter;
    }

    private ServiceDiscovery createServiceDiscoveryByMode(String mode,
                                                          SystemServerSettingServiceDiscovery config,
                                                          SystemServerSettingServiceDiscoveryService discoveryService,
                                                          Integer serverId) {
        if ("TABLE".equalsIgnoreCase(mode) || "DEFAULT".equalsIgnoreCase(mode)) {
            // 表模式：使用内置的 DefaultServiceDiscovery，并加载映射
            DefaultServiceDiscovery defaultServiceDiscovery = new DefaultServiceDiscovery(new DiscoveryOption());
            List<SystemServerSettingServiceDiscoveryMapping> mappings =
                    discoveryService.listMappingsByServerId(serverId);
            if (mappings != null) {
                for (SystemServerSettingServiceDiscoveryMapping m : mappings) {
                    if (m == null || Boolean.FALSE.equals(m.getServiceDiscoveryEnabled())) {
                        continue;
                    }
                    String path = StringUtils.startWithAppend(m.getServiceDiscoveryName(), "/");
                    Discovery discovery = toDiscovery(m.getServiceDiscoveryAddress(), m.getServiceDiscoveryWeight());
                    if (discovery != null) {
                        defaultServiceDiscovery.registerService(path, discovery);
                    }
                }
            }
            return defaultServiceDiscovery;
        }

        if ("HAZELCAST".equalsIgnoreCase(mode)) {
            if(null != HAZELCAST_SERVICE_DISCOVERY) {
                return HAZELCAST_SERVICE_DISCOVERY;
            }
            // Hazelcast模式
            HazelcastServiceDiscovery hazelcastServiceDiscovery = new HazelcastServiceDiscovery(new DiscoveryOption());
            try {
                hazelcastServiceDiscovery.start();
                HAZELCAST_SERVICE_DISCOVERY = hazelcastServiceDiscovery;
            } catch (Exception ignored) {
            }
            return hazelcastServiceDiscovery;
        }

        // SPRING模式（默认）：从Spring容器中获取 ServiceDiscovery
        if (config != null && StringUtils.isNotBlank(config.getServiceDiscoveryBeanName())) {
            return SpringBeanUtils.getBean(config.getServiceDiscoveryBeanName(), ServiceDiscovery.class);
        }
        return SpringBeanUtils.getBean(ServiceDiscovery.class);
    }

    private Discovery toDiscovery(String address, Integer weight) {
        if (StringUtils.isEmpty(address)) {
            return null;
        }
        try {
            NetAddress netAddress = NetAddress.of(address);
            String host = StringUtils.defaultString(netAddress.getHost(), "127.0.0.1");
            int port = netAddress.getPort() > 0 ? netAddress.getPort() : 80;
            String protocol = StringUtils.defaultString(netAddress.getProtocol(), "http");
            protocol = StringUtils.equalsIgnoreCase(protocol, "https") ? "http" : protocol;
            return Discovery.builder()
                    .host(host)
                    .port(port)
                    .protocol(protocol)
                    .weight(weight == null ? 1.0 : weight.doubleValue())
                    .build();
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    public void update(ServletFilter filter, SystemServerSetting setting) {
        if (!(filter instanceof ServiceDiscoveryServletFilter sdf)) {
            return;
        }

        Integer serverId = setting.getSystemServerSettingServerId();
        SystemServerSettingServiceDiscoveryService discoveryService =
                SpringBeanUtils.getBean(SystemServerSettingServiceDiscoveryService.class);
        List<SystemServerSettingServiceDiscovery> configs = discoveryService.listByServerId(serverId);
        SystemServerSettingServiceDiscovery config = configs != null && !configs.isEmpty() ? configs.get(0) : null;
        String mode = config != null && StringUtils.isNotBlank(config.getServiceDiscoveryMode())
                ? config.getServiceDiscoveryMode()
                : "SPRING";
        String balance = config != null && StringUtils.isNotBlank(config.getServiceDiscoveryBalance())
                ? config.getServiceDiscoveryBalance()
                : "weight";
        boolean enabled = config == null || Boolean.TRUE.equals(config.getServiceDiscoveryEnabled());

        ServiceDiscovery serviceDiscovery = sdf.getServiceDiscovery();
        if(serviceDiscovery != null && null != HAZELCAST_SERVICE_DISCOVERY && serviceDiscovery instanceof HazelcastServiceDiscovery hazelcastableServiceDiscovery) {
            HAZELCAST_SERVICE_DISCOVERY = hazelcastableServiceDiscovery;
        }
        ServiceDiscovery newServiceDiscovery = createServiceDiscoveryByMode(mode, config, discoveryService, serverId);
        sdf.setServiceDiscovery(newServiceDiscovery)
                .setDefaultBalance(balance)
                .setEnabled(enabled);
    }
}





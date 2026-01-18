package com.chua.starter.discovery.support.service;

import com.chua.common.support.network.discovery.Discovery;
import com.chua.common.support.network.discovery.ServiceDiscovery;
import com.chua.common.support.core.utils.StringUtils;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * 服务发现服务
 *
 * <p>封装 {@link ServiceDiscovery} 能力，提供默认协议查询与多协议查询两套接口：</p>
 * <ul>
 *     <li>不指定协议时，使用默认 {@link ServiceDiscovery} 实现；</li>
 *     <li>指定协议时，根据协议名称选择对应的 {@link ServiceDiscovery} 实现，
 *     协议与 Bean 名映射规则为 {@code serviceDiscovery#protocol}。</li>
 * </ul>
 *
 * <p>当未找到指定协议的实现时，将自动回退到默认实现。</p>
 *
 * @author CH
 * @since 2024/9/12
 */
public class DiscoveryService {

    /**
     * 默认负载均衡策略
     */
    private static final String DEFAULT_BALANCE = "weight";

    /**
     * 默认服务发现实现（primary）
     */
    private final ServiceDiscovery defaultServiceDiscovery;

    /**
     * 所有服务发现实现，key 为 BeanName
     */
    private final Map<String, ServiceDiscovery> serviceDiscoveryMap;

    /**
     * 仅注入单个 {@link ServiceDiscovery} 的构造方法，供禁用发现开关等简单场景使用。
     *
     * @param defaultServiceDiscovery 默认服务发现实现
     */
    public DiscoveryService(ServiceDiscovery defaultServiceDiscovery) {
        this(defaultServiceDiscovery,
                Collections.singletonMap("serviceDiscovery#default", defaultServiceDiscovery));
    }

    /**
     * 注入默认及全部 {@link ServiceDiscovery} 实现的构造方法。
     *
     * @param defaultServiceDiscovery 默认服务发现实现（primary）
     * @param serviceDiscoveryMap     所有服务发现实现，key 为 BeanName
     */
    public DiscoveryService(ServiceDiscovery defaultServiceDiscovery,
                            Map<String, ServiceDiscovery> serviceDiscoveryMap) {
        this.defaultServiceDiscovery = defaultServiceDiscovery;
        this.serviceDiscoveryMap = serviceDiscoveryMap == null ? Collections.emptyMap() : serviceDiscoveryMap;
    }

    /**
     * 按默认协议获取单个服务。
     *
     * @param uriSpec 服务标识
     * @return 匹配到的服务实例
     */
    public Discovery getDiscovery(String uriSpec) {
        return defaultServiceDiscovery.getService(uriSpec);
    }

    /**
     * 按默认协议获取全部服务实例。
     *
     * @param uriSpec 服务标识
     * @return 匹配到的服务实例集合
     */
    public Set<Discovery> getDiscoveryAll(String uriSpec) {
        return defaultServiceDiscovery.getServiceAll(uriSpec);
    }

    /**
     * 按指定协议获取单个服务。
     *
     * @param protocol 协议名称，对应配置中的 protocol
     * @param uriSpec  服务标识
     * @return 匹配到的服务实例
     */
    public Discovery getDiscovery(String protocol, String uriSpec) {
        ServiceDiscovery target = resolveServiceDiscovery(protocol);
        return target.getService(uriSpec, DEFAULT_BALANCE, protocol);
    }

    /**
     * 按指定协议获取全部服务实例。
     *
     * @param protocol 协议名称，对应配置中的 protocol
     * @param uriSpec  服务标识
     * @return 匹配到的服务实例集合
     */
    public Set<Discovery> getDiscoveryAll(String protocol, String uriSpec) {
        ServiceDiscovery target = resolveServiceDiscovery(protocol);
        return target.getServiceAll(uriSpec);
    }

    /**
     * 根据协议名称解析对应的 {@link ServiceDiscovery} 实现。
     * <p>优先规则如下：</p>
     * <ol>
     *     <li>protocol 为空时直接返回默认实现；</li>
     *     <li>优先匹配 BeanName 等于 {@code serviceDiscovery#protocol} 的实现；</li>
     *     <li>否则查找 BeanName 以 {@code #protocol} 结尾的实现；</li>
     *     <li>均未命中时回退到默认实现。</li>
     * </ol>
     *
     * @param protocol 协议名称
     * @return 对应的 {@link ServiceDiscovery} 实现
     */
    private ServiceDiscovery resolveServiceDiscovery(String protocol) {
        if (StringUtils.isEmpty(protocol) || serviceDiscoveryMap.isEmpty()) {
            return defaultServiceDiscovery;
        }

        String beanName = "serviceDiscovery#" + protocol;
        ServiceDiscovery target = serviceDiscoveryMap.get(beanName);
        if (target != null) {
            return target;
        }

        for (Map.Entry<String, ServiceDiscovery> entry : serviceDiscoveryMap.entrySet()) {
            String key = entry.getKey();
            if (key != null && key.endsWith("#" + protocol)) {
                return entry.getValue();
            }
        }

        return defaultServiceDiscovery;
    }
}

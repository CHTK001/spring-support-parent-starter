package com.chua.starter.server.support.spi;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.spi.definition.ServiceDefinition;
import com.chua.starter.server.support.enums.HostCollectType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 主机采集 提供者 注册中心
 * <p>
 * 实现按 {@code @Spi} 注册到 {@link HostCollectProvider} 扩展点，本类取其全部定义并按
 * {@link HostCollectProvider#type()} 建索引，调度器 / Agent 上报端点据此路由到具体采集实现。
 * </p>
 *
 * <p>
 * 索引在首次解析时构建：SPI 实例的依赖由 Spring 装配链注入，容器就绪前构建会得到半初始化实例。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class HostCollectProviderRegistry {

    /**
     * 主机采集扩展点
     */
    private final ServiceProvider<HostCollectProvider> extensions = ServiceProvider.of(HostCollectProvider.class);

    /**
     * 采集方式 -> 实现索引，首次使用时构建
     */
    private volatile Map<HostCollectType, HostCollectProvider> index;

    /**
     * 解析指定采集方式的 提供者
     *
     * @param type 采集方式
     * @return Optional，可能为空
     */
    public Optional<HostCollectProvider> resolve(HostCollectType type) {
        if (type == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(index().get(type));
    }

    /**
     * 所有已注册采集方式
     *
     * @return 不可变视图
     */
    public Map<HostCollectType, String> describe() {
        Map<HostCollectType, String> out = new EnumMap<>(HostCollectType.class);
        index().forEach((type, provider) -> out.put(type, provider.getClass().getSimpleName()));
        return out;
    }

    /**
     * 取索引，必要时构建。
     *
     * @return 采集方式 -> 实现
     */
    private Map<HostCollectType, HostCollectProvider> index() {
        Map<HostCollectType, HostCollectProvider> current = index;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (index == null) {
                index = build();
            }
            return index;
        }
    }

    /**
     * 遍历扩展点定义建索引，同一实现类的多个别名只登记一次。
     *
     * @return 不可变索引
     */
    private Map<HostCollectType, HostCollectProvider> build() {
        Map<HostCollectType, HostCollectProvider> out = new EnumMap<>(HostCollectType.class);
        Set<Class<?>> seen = new HashSet<>();
        for (ServiceDefinition definition : extensions.getPriorityServiceDefinitions()) {
            HostCollectProvider provider = definition.getObj(extensions.getServiceAutowire());
            if (provider == null || !seen.add(provider.getClass())) {
                continue;
            }
            HostCollectType type = provider.type();
            if (type == null) {
                continue;
            }
            out.put(type, provider);
            log.info("[collect] 注册采集 Provider: type={}, impl={}", type, provider.getClass().getSimpleName());
        }
        if (out.isEmpty()) {
            log.warn("[collect] HostCollectProvider 扩展点无任何实现，主机采集不可用");
        }
        return Collections.unmodifiableMap(out);
    }
}

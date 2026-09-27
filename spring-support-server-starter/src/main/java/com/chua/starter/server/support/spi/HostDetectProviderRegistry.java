package com.chua.starter.server.support.spi;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.spi.definition.ServiceDefinition;
import com.chua.starter.server.support.entity.MsServerHost;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 主机硬件探测提供者注册中心
 * <p>
 * 实现按 {@code @Spi} 注册到 {@link HostDetectProvider} 扩展点（{@code META-INF/extensions} 文件 +
 * 接口同包扫描两条发现路径），本类取其全部定义并按 {@link HostDetectProvider#osType()} 建索引，
 * {@code ServerHostServiceImpl.detectHardware} 据此路由到具体探测实现。
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
public class HostDetectProviderRegistry {

    /**
     * 硬件探测扩展点
     */
    private final ServiceProvider<HostDetectProvider> extensions = ServiceProvider.of(HostDetectProvider.class);

    /**
     * 系统类型 -> 探测实现索引，首次使用时构建
     */
    private volatile Map<String, HostDetectProvider> index;

    /**
     * 按主机解析探测提供者：先按采集方式（Agent 等自报通道按 {@code HostCollectType} 编码注册），
     * 未命中再按平台。
     *
     * @param host 目标主机
     * @return Optional，可能为空
     */
    public Optional<HostDetectProvider> resolve(MsServerHost host) {
        if (host == null) {
            return Optional.empty();
        }
        Optional<HostDetectProvider> channel = resolve(host.getServerHostCollectMode());
        return channel.isPresent() ? channel : resolve(host.getServerHostOsType());
    }

    /**
     * 按系统类型解析探测提供者
     *
     * @param osType 系统类型（LINUX/MACOS/WINDOWS）
     * @return Optional，可能为空
     */
    public Optional<HostDetectProvider> resolve(String osType) {
        if (osType == null || osType.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(index().get(osType.trim().toUpperCase(Locale.ROOT)));
    }

    /**
     * 是否支持指定系统类型
     *
     * @param osType 系统类型
     * @return 是否支持
     */
    public boolean supports(String osType) {
        return resolve(osType).isPresent();
    }

    /**
     * 取索引，必要时构建。
     *
     * @return 系统类型 -> 实现
     */
    private Map<String, HostDetectProvider> index() {
        Map<String, HostDetectProvider> current = index;
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
    private Map<String, HostDetectProvider> build() {
        Map<String, HostDetectProvider> out = new HashMap<>();
        Set<Class<?>> seen = new HashSet<>();
        for (ServiceDefinition definition : extensions.getPriorityServiceDefinitions()) {
            HostDetectProvider provider = definition.getObj(extensions.getServiceAutowire());
            if (provider == null || !seen.add(provider.getClass())) {
                continue;
            }
            String osType = provider.osType();
            if (osType == null || osType.isBlank()) {
                continue;
            }
            out.put(osType.trim().toUpperCase(Locale.ROOT), provider);
            log.info("[detect] 注册探测 Provider: osType={}, impl={}", osType, provider.getClass().getSimpleName());
        }
        if (out.isEmpty()) {
            log.warn("[detect] HostDetectProvider 扩展点无任何实现，硬件探测不可用");
        }
        return Collections.unmodifiableMap(out);
    }
}

package com.chua.starter.server.support.spi;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.spi.definition.ServiceDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 系统服务挂载提供者注册中心。
 *
 * <p>实现按 {@code @Spi} 注册到 {@link SystemServiceProvider} 扩展点
 * （{@code META-INF/extensions} 文件 + 接口同包扫描两条发现路径），本类取其全部定义并按
 * {@link SystemServiceProvider#osType()} 建索引；挂载/卸载入口据此路由，
 * 入口自身不再持有平台分支。</p>
 *
 * <p>索引在首次解析时构建：SPI 实例的依赖由 Spring 装配链注入，容器就绪前构建会得到半初始化实例。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class SystemServiceProviderRegistry {

    /**
     * 系统服务挂载扩展点
     */
    private final ServiceProvider<SystemServiceProvider> extensions = ServiceProvider.of(SystemServiceProvider.class);

    /**
     * 系统类型 -> 挂载实现索引，首次使用时构建
     */
    private volatile Map<String, SystemServiceProvider> index;

    /**
     * 按系统类型解析挂载提供者。
     *
     * @param osType 系统类型（LINUX/MACOS/WINDOWS/...，大小写不敏感）
     * @return Optional，未登记该平台时为空
     */
    public Optional<SystemServiceProvider> resolve(String osType) {
        if (osType == null || osType.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(index().get(osType.trim().toUpperCase(Locale.ROOT)));
    }

    /**
     * 是否支持指定系统类型的挂载。
     *
     * @param osType 系统类型
     * @return 是否支持
     */
    public boolean supports(String osType) {
        return resolve(osType).isPresent();
    }

    /**
     * 列出已登记的平台标识，供前端渲染「该主机平台是否可挂载服务」。
     *
     * @return 平台标识列表
     */
    public List<String> supportedOsTypes() {
        return List.copyOf(index().keySet());
    }

    /**
     * 取索引，必要时构建。
     *
     * @return 系统类型 -> 实现
     */
    private Map<String, SystemServiceProvider> index() {
        Map<String, SystemServiceProvider> current = index;
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
    private Map<String, SystemServiceProvider> build() {
        Map<String, SystemServiceProvider> out = new HashMap<>();
        Set<Class<?>> seen = new HashSet<>();
        for (ServiceDefinition definition : extensions.getPriorityServiceDefinitions()) {
            SystemServiceProvider provider = definition.getObj(extensions.getServiceAutowire());
            if (provider == null || !seen.add(provider.getClass())) {
                continue;
            }
            String osType = provider.osType();
            if (osType == null || osType.isBlank()) {
                continue;
            }
            out.put(osType.trim().toUpperCase(Locale.ROOT), provider);
            log.info("[system-service] 注册挂载 Provider: osType={}, impl={}",
                    osType, provider.getClass().getSimpleName());
        }
        if (out.isEmpty()) {
            log.warn("[system-service] SystemServiceProvider 扩展点无任何实现，挂载系统服务不可用");
        }
        return Collections.unmodifiableMap(out);
    }
}

package com.chua.starter.ai.support.manage.store.datasource;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 需求数据源提供方注册表。
 *
 * <p>通过 {@link ServiceLoader} 在应用启动时自动发现所有实现
 * {@link IRequirementDataSource} 的 SPI 类，并按 type() 注册。</p>
 *
 * <p>使用方式：
 * <pre>
 *   registry.getDataSource("lark")    → LarkRequirementDataSource
 *   registry.getDataSource("generic") → GenericApiRequirementDataSource
 *   registry.list()                   → [lark, generic]
 * </pre>
 * </p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Component
public class RequirementDataSourceRegistry implements ApplicationRunner {

    private final Map<String, IRequirementDataSource> registry = new ConcurrentHashMap<>();

    /**
     * 按类型获取数据源提供方。
     *
     * @param type 数据源类型（lark / generic / jira / notion 等）
     * @return 对应的 provider，未找到返回 null
     */
    public IRequirementDataSource getDataSource(String type) {
        return registry.get(type);
    }

    /**
     * 获取所有已注册的数据源类型列表。
     */
    public List<String> list() {
        return Collections.unmodifiableList(
                registry.keySet().stream().sorted().collect(Collectors.toList())
        );
    }

    /**
     * 获取所有已注册的数据源提供方。
     */
    public List<IRequirementDataSource> allProviders() {
        return Collections.unmodifiableList(
                registry.values().stream()
                        .sorted(Comparator.comparing(IRequirementDataSource::type))
                        .collect(Collectors.toList())
        );
    }

    /**
     * 应用启动时通过 SPI 自动注册所有数据源提供方。
     */
    @Override
    public void run(ApplicationArguments args) {
        ServiceLoader.load(IRequirementDataSource.class).forEach(provider -> {
            String type = provider.type();
            registry.put(type, provider);
            log.info("[DataSourceRegistry] 注册数据源提供方: type={}", type);
        });
        log.info("[DataSourceRegistry] 已注册 {} 个数据源提供方: {}", registry.size(), list());
    }
}

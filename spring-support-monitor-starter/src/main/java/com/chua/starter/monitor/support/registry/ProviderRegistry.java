package com.chua.starter.monitor.support.registry;

import com.chua.starter.monitor.support.enums.ProviderType;
import com.chua.starter.monitor.support.enums.ServiceType;
import com.chua.starter.monitor.support.spi.MetricProvider;
import com.chua.starter.monitor.support.spi.OperationProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 提供者 注册中心
 * <p>
 * 启动时扫描所有 {@link MetricProvider} / {@link OperationProvider} Bean,
 * 分别建立双键索引, 供调度器与控制器路由使用。
 * </p>
 *
 * <h3>MetricProvider 路由</h3>
 * <pre>
 *   Map&lt;ServiceType, Map&lt;ProviderType, MetricProvider&gt;&gt;
 * </pre>
 *
 * <h3>OperationProvider 路由</h3>
 * <pre>
 *   Map&lt;ServiceType, OperationProvider&gt;
 * </pre>
 *
 * @since 2026/8/4
 * @author CH
 */
@Slf4j
@Component
public class ProviderRegistry {

    /**
     * 指标 提供者 索引
     */
    private final Map<ServiceType, Map<ProviderType, MetricProvider>> metricIndex = new EnumMap<>(ServiceType.class);

    /**
     * 操作 提供者 索引
     */
    private final Map<ServiceType, OperationProvider> operationIndex = new ConcurrentHashMap<>();

    /**
     * 构造方法, 自动注入所有 提供者 实现
     *
     * @param metricProviders 指标 提供者 列表
     * @param operationProviders 操作 提供者 列表
     */
    @Autowired
    public ProviderRegistry(List<MetricProvider> metricProviders, List<OperationProvider> operationProviders) {
        /**
         * 注册指标。
         * @param metricProviders 指标提供者
         */
        registerMetric(metricProviders);
        /**
         * 注册operation。
         * @param operationProviders operation提供者
         */
        registerOperation(operationProviders);
    }

    /**
     * 注册指标 提供者
     *
     * @param providers 提供者 列表
     */
    private void registerMetric(List<MetricProvider> providers) {
        if (providers == null || providers.isEmpty()) {
            log.warn("[ProviderRegistry] 未发现 MetricProvider 实现, 采集调度将无实现可用");
            return;
        }
        for (MetricProvider provider : providers) {
            ProviderType pt = provider.providerType();
            for (ServiceType st : provider.supportServiceTypes()) {
                metricIndex.computeIfAbsent(st, k -> new EnumMap<>(ProviderType.class)).put(pt, provider);
                log.info("[ProviderRegistry] 注册 MetricProvider: service={}, provider={}, impl={}",
                        st, pt, provider.getClass().getSimpleName());
            }
        }
    }

    /**
     * 注册操作 提供者
     *
     * @param providers 提供者 列表
     */
    private void registerOperation(List<OperationProvider> providers) {
        if (providers == null || providers.isEmpty()) {
            log.warn("[ProviderRegistry] 未发现 OperationProvider 实现, Redis/DB 操作将无实现可用");
            return;
        }
        for (OperationProvider provider : providers) {
            ServiceType st = provider.serviceType();
            operationIndex.put(st, provider);
            log.info("[ProviderRegistry] 注册 OperationProvider: service={}, impl={}",
                    st, provider.getClass().getSimpleName());
        }
    }

    /**
     * 解析指标 提供者
     *
     * @param serviceType 服务类型
     * @param providerType 采集方式
     * @return Optional, 可能为空
     */
    public Optional<MetricProvider> resolveMetric(ServiceType serviceType, ProviderType providerType) {
        Map<ProviderType, MetricProvider> inner = metricIndex.get(serviceType);
        if (inner == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(inner.get(providerType));
    }

    /**
     * 解析操作 提供者
     *
     * @param serviceType 服务类型
     * @return Optional, 可能为空
     */
    public Optional<OperationProvider> resolveOperation(ServiceType serviceType) {
        return Optional.ofNullable(operationIndex.get(serviceType));
    }

    /**
     * 列出所有已注册的指标 提供者 组合
     *
     * @return 不可变视图
     */
    public Set<ServiceType> metricServices() {
        return Collections.unmodifiableSet(metricIndex.keySet());
    }

    /**
     * 用于测试与文档展示
     *
     * @return 已注册 提供者 描述 映射
     */
    public Map<String, String> describe() {
        Map<String, String> out = new HashMap<>();
        metricIndex.forEach((st, inner) -> inner.forEach((pt, p) ->
                out.put(st.name() + ":" + pt.name(), p.getClass().getSimpleName())));
        operationIndex.forEach((st, p) ->
                out.put(st.name() + ":OP", p.getClass().getSimpleName()));
        return out;
    }
}

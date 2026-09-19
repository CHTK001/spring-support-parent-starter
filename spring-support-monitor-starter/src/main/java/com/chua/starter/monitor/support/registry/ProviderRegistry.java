package com.chua.starter.monitor.support.registry;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.spi.definition.ServiceDefinition;
import com.chua.starter.monitor.support.enums.ProviderType;
import com.chua.starter.monitor.support.enums.ServiceType;
import com.chua.starter.monitor.support.spi.MetricProvider;
import com.chua.starter.monitor.support.spi.OperationProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 提供者 注册中心
 * <p>
 * 从 {@link MetricProvider} / {@link OperationProvider} 两个 SPI 扩展点取实现，
 * 分别建立双键索引, 供调度器与控制器路由使用。新增采集方式或服务类型只需提交实现类，
 * 不必改动本类。
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
 * <p>
 * 索引在首次解析时构建：SPI 实例的依赖由 Spring 装配链注入，容器就绪前构建会得到半初始化实例。
 * </p>
 *
 * @since 2026/8/4
 * @author CH
 */
@Slf4j
@Component
public class ProviderRegistry {

    /**
     * 指标采集扩展点
     */
    private final ServiceProvider<MetricProvider> metricExtensions = ServiceProvider.of(MetricProvider.class);

    /**
     * 操作扩展点
     */
    private final ServiceProvider<OperationProvider> operationExtensions = ServiceProvider.of(OperationProvider.class);

    /**
     * 指标 提供者 索引，首次使用时构建
     */
    private volatile Map<ServiceType, Map<ProviderType, MetricProvider>> metricIndex;

    /**
     * 操作 提供者 索引，首次使用时构建
     */
    private volatile Map<ServiceType, OperationProvider> operationIndex;

    /**
     * 解析指标 提供者
     *
     * @param serviceType 服务类型
     * @param providerType 采集方式
     * @return Optional, 可能为空
     */
    public Optional<MetricProvider> resolveMetric(ServiceType serviceType, ProviderType providerType) {
        Map<ProviderType, MetricProvider> inner = metricRegistry().get(serviceType);
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
        return Optional.ofNullable(operationRegistry().get(serviceType));
    }

    /**
     * 列出所有已注册的指标 提供者 组合
     *
     * @return 不可变视图
     */
    public Set<ServiceType> metricServices() {
        return Collections.unmodifiableSet(metricRegistry().keySet());
    }

    /**
     * 用于测试与文档展示
     *
     * @return 已注册 提供者 描述 映射
     */
    public Map<String, String> describe() {
        Map<String, String> out = new HashMap<>();
        metricRegistry().forEach((st, inner) -> inner.forEach((pt, p) ->
                out.put(st.name() + ":" + pt.name(), p.getClass().getSimpleName())));
        operationRegistry().forEach((st, p) ->
                out.put(st.name() + ":OP", p.getClass().getSimpleName()));
        return out;
    }

    /**
     * 取指标索引，必要时构建。
     *
     * @return 服务类型 -> (采集方式 -> 实现)
     */
    private Map<ServiceType, Map<ProviderType, MetricProvider>> metricRegistry() {
        Map<ServiceType, Map<ProviderType, MetricProvider>> current = metricIndex;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (metricIndex == null) {
                metricIndex = buildMetric();
            }
            return metricIndex;
        }
    }

    /**
     * 取操作索引，必要时构建。
     *
     * @return 服务类型 -> 实现
     */
    private Map<ServiceType, OperationProvider> operationRegistry() {
        Map<ServiceType, OperationProvider> current = operationIndex;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (operationIndex == null) {
                operationIndex = buildOperation();
            }
            return operationIndex;
        }
    }

    /**
     * 注册指标 提供者：同一实现类的多个别名只登记一次。
     *
     * @return 不可变索引
     */
    private Map<ServiceType, Map<ProviderType, MetricProvider>> buildMetric() {
        Map<ServiceType, Map<ProviderType, MetricProvider>> index = new EnumMap<>(ServiceType.class);
        Set<Class<?>> seen = new HashSet<>();
        for (ServiceDefinition definition : metricExtensions.getPriorityServiceDefinitions()) {
            MetricProvider provider = definition.getObj(metricExtensions.getServiceAutowire());
            if (provider == null || !seen.add(provider.getClass())) {
                continue;
            }
            ProviderType pt = provider.providerType();
            for (ServiceType st : provider.supportServiceTypes()) {
                index.computeIfAbsent(st, k -> new EnumMap<>(ProviderType.class)).put(pt, provider);
                log.info("[ProviderRegistry] 注册 MetricProvider: service={}, provider={}, impl={}",
                        st, pt, provider.getClass().getSimpleName());
            }
        }
        if (index.isEmpty()) {
            log.warn("[ProviderRegistry] MetricProvider 扩展点无任何实现, 采集调度将无实现可用");
        }
        return Collections.unmodifiableMap(index);
    }

    /**
     * 注册操作 提供者：同一实现类的多个别名只登记一次。
     *
     * @return 不可变索引
     */
    private Map<ServiceType, OperationProvider> buildOperation() {
        Map<ServiceType, OperationProvider> index = new EnumMap<>(ServiceType.class);
        Set<Class<?>> seen = new HashSet<>();
        for (ServiceDefinition definition : operationExtensions.getPriorityServiceDefinitions()) {
            OperationProvider provider = definition.getObj(operationExtensions.getServiceAutowire());
            if (provider == null || !seen.add(provider.getClass())) {
                continue;
            }
            ServiceType st = provider.serviceType();
            if (st == null) {
                continue;
            }
            index.put(st, provider);
            log.info("[ProviderRegistry] 注册 OperationProvider: service={}, impl={}",
                    st, provider.getClass().getSimpleName());
        }
        if (index.isEmpty()) {
            log.warn("[ProviderRegistry] OperationProvider 扩展点无任何实现, Redis/DB 操作将无实现可用");
        }
        return Collections.unmodifiableMap(index);
    }
}

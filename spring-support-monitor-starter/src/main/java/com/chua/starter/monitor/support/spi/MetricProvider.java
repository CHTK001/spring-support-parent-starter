package com.chua.starter.monitor.support.spi;

import com.chua.starter.monitor.support.enums.ProviderType;
import com.chua.starter.monitor.support.enums.ServiceType;
import reactor.core.publisher.Mono;

/**
 * 指标采集 Provider SPI
 * <p>
 * 由 Registry 按 {@link ServiceType} + {@link ProviderType} 双键索引,
 * 调度器调用 {@link #collect} 获得 {@link CollectResult}。
 * </p>
 *
 * @author CH
 * @since 2026/8/4
 */
public interface MetricProvider {

    /**
     * 该 Provider 对应的采集方式
     *
     * @return Provider 类型
     */
    ProviderType providerType();

    /**
     * 该 Provider 支持的服务类型集合
     *
     * @return 支持的服务类型集合
     */
    java.util.Set<ServiceType> supportServiceTypes();

    /**
     * 执行一次采集
     *
     * @param context 采集上下文
     * @return 响应式采集结果
     */
    Mono<CollectResult> collect(CollectContext context);

    /**
     * 判断是否支持当前任务
     *
     * @param context 上下文
     * @return true 表示支持
     */
    default boolean supports(CollectContext context) {
        if (context == null || context.getTask() == null) {
            return false;
        }
        ServiceType st = ServiceType.of(context.getTask().getServiceType());
        ProviderType pt = ProviderType.of(context.getTask().getProvider());
        if (pt != providerType()) {
            return false;
        }
        return supportServiceTypes().contains(st);
    }
}
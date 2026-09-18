package com.chua.starter.server.support.spi;

import reactor.core.publisher.Mono;

/**
* 主机采集 提供者 SPI（响应式）
* <p>
* 采集方式可插拔扩展：新增采集方式只需实现本接口并注册为 Spring Bean，
* 注册中心按 {@link #type()} 自动路由，调度零改动。
* 无论主动（SSH/HTTP/SNMP/Prometheus）还是被动（Agent 上报），
* 上层统一通过 {@link Mono} 获取数据，屏蔽底层差异。
* 与 监控 模块 {@code MetricProvider} 的设计保持一致。
* </p>
*
* @author CH
* @since 4.0.0.42
 */
public interface HostCollectProvider {

    /**
    * 该 提供者 对应的采集方式
    *
    * @return 采集方式类型
    */
    com.chua.starter.server.support.enums.HostCollectType type();

    /**
    * 执行一次采集（响应式）
    *
    * @param context 采集上下文（含目标主机）
    * @return 采集结果 Mono
    */
    Mono<CollectOutcome> collect(HostCollectContext context);

    /**
    * 判断是否支持当前采集任务
    *
    * @param context 采集上下文
    * @return true 表示支持
    */
    default boolean supports(HostCollectContext context) {
        if (context == null || context.getHost() == null) {
            return false;
        }
        return type() != null && type() == context.getMode();
    }
}

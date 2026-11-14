package com.chua.starter.monitor.support.config;

import org.springframework.boot.actuate.web.exchanges.HttpExchangeRepository;
import org.springframework.boot.actuate.web.exchanges.InMemoryHttpExchangeRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Actuator 请求记录仓库配置。
 *
 * <p>发布管理「项目 → 观测与诊断 → 链路追踪」在项目未接入 APM 时，回退读目标项目的
 * Actuator 请求记录（{@code /actuator/httpexchanges}，旧版为 {@code /actuator/httptrace}）。
 * 该端点由 {@code HttpExchangesEndpointAutoConfiguration} 条件装配，前提是容器里有
 * {@link HttpExchangeRepository} —— 没有仓库 bean 时端点整体不注册，页面拿到 404
 * 只能显示「未暴露 httptrace / httpexchanges」，监控自身作为被观测项目时就是空的。</p>
 *
 * <p>这里补一个进程内仓库（只保留最近 {@value #CAPACITY} 条，重启即清空）。
 * 记录内容按 Actuator 默认口径：请求/响应头与耗时，不含 Authorization 头。</p>
 *
 * <p>暴露面由 {@code management.endpoints.web.exposure.include} 控制：
 * 本地联调开满（发布管理要能逐个端点验收），生产 profile 收窄到 health/info。</p>
 *
 * @author CH
 * @since 4.0.0.50
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(HttpExchangeRepository.class)
public class MonitorActuatorConfiguration {

    /**
     * 进程内保留的请求记录条数。
     *
     * <p>列表页一屏几十条，留一屏的数倍便于回溯；再大只是白占内存，
     * 流量大时按环形覆盖。</p>
     */
    private static final int CAPACITY = 200;

    /**
     * 请求记录仓库（进程内环形缓冲）。
     *
     * @return 请求记录仓库
     */
    @Bean
    @ConditionalOnMissingBean(HttpExchangeRepository.class)
    public HttpExchangeRepository httpExchangeRepository() {
        InMemoryHttpExchangeRepository repository = new InMemoryHttpExchangeRepository();
        repository.setCapacity(CAPACITY);
        return repository;
    }
}

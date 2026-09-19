package com.chua.starter.common.support.serviceinfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.annotation.Order;

import java.util.Map;

/**
 * 自动发现并注册各 启动 的 服务信息。
 *
 * <p>通过 Spring 容器中的 {@link ServiceInfoContributor} Bean 收集
 * 各 启动 的端点和开关信息，自动注册到 {@link ServiceInfoRegistry}。</p>
 *
 * <h3>收集时机</h3>
 * <p>使用 {@link ContextRefreshedEvent} 事件而非 Bean 构造时机收集,
 * 保证所有 启动 的 {@code @Bean ServiceInfoContributor} 都已经注册到容器。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@AutoConfiguration
@Order(Integer.MIN_VALUE + 1)
public class ServiceInfoContributorAutoConfiguration implements ApplicationListener<ContextRefreshedEvent> {

    /** 日志 */
    private static final Logger log = LoggerFactory.getLogger(ServiceInfoContributorAutoConfiguration.class);

    /** Application上下文 */
    private final ApplicationContext applicationContext;

    /**
     * 创建 服务信息contributorauto配置 实例
     * @param applicationContext application上下文
     */
    public ServiceInfoContributorAutoConfiguration(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    /** onapplication事件 */
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (event.getApplicationContext() != this.applicationContext) {
            return;
        }
        /**
    * collect。
    */
        collect();
    }

    /**
     * 收集所有 {@link ServiceInfoContributor} 并注册到 {@link ServiceInfoRegistry}。
     *
     * <p>单个 contributor 抛异常不会影响其它 contributor 的注册。</p>
     */
    void collect() {
        Map<String, ServiceInfoContributor> contributors;
        try {
            contributors = applicationContext.getBeansOfType(ServiceInfoContributor.class);
        } catch (Exception e) {
            log.warn("[ServiceInfo] 无法收集 Contributor 列表", e);
            return;
        }
        ServiceInfoRegistry registry = ServiceInfoRegistry.getInstance();
        for (Map.Entry<String, ServiceInfoContributor> entry : contributors.entrySet()) {
            String beanName = entry.getKey();
            ServiceInfoContributor contributor = entry.getValue();
            try {
                contributor.contribute(registry);
            } catch (Exception e) {
                log.warn("[ServiceInfo] Contributor [{}] 注册失败, 已跳过: {}", beanName, e.getMessage(), e);
            }
        }
        log.debug("[ServiceInfo] 已收集 {} 个 Contributor, 注册端点 {} 个, 开关 {} 个",
                contributors.size(),
                registry.getEndpoints().size(),
                registry.getFlags().size());
    }
}

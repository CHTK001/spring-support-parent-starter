package com.chua.starter.common.support.serviceinfo;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.util.Map;

/**
 * 自动发现并注册各 starter 的 ServiceInfo。
 *
 * <p>通过 Spring 容器中的 {@link ServiceInfoContributor} Bean 收集
 * 各 starter 的端点和开关信息，自动注册到 {@link ServiceInfoRegistry}。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@AutoConfiguration
public class ServiceInfoContributorAutoConfiguration {

    @Bean
    static ServiceInfoContributorCollector serviceInfoContributorCollector(ApplicationContext applicationContext) {
        return new ServiceInfoContributorCollector(applicationContext);
    }

    static class ServiceInfoContributorCollector {

        private final ApplicationContext applicationContext;

        ServiceInfoContributorCollector(ApplicationContext applicationContext) {
            this.applicationContext = applicationContext;
            collect();
        }

        void collect() {
            Map<String, ServiceInfoContributor> contributors =
                    applicationContext.getBeansOfType(ServiceInfoContributor.class);
            ServiceInfoRegistry registry = ServiceInfoRegistry.getInstance();
            for (ServiceInfoContributor contributor : contributors.values()) {
                contributor.contribute(registry);
            }
        }
    }
}

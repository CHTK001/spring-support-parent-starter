package com.chua.support.prometheus.starter;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * prometheus配置
 *
 * @author CH
 * @since 2025/5/20 8:43
 */
@Slf4j
@EnableConfigurationProperties(PrometheusProperties.class)
public class PrometheusConfiguration implements ApplicationContextAware {

    private PrometheusProperties prometheusProperties;

    @Bean
    MeterRegistryCustomizer<MeterRegistry> configurer() {
        log.info("导入应用");
        return registry -> registry.config().commonTags("application", prometheusProperties.getName());
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        prometheusProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate(PrometheusProperties.PRE, PrometheusProperties.class);
    }

}

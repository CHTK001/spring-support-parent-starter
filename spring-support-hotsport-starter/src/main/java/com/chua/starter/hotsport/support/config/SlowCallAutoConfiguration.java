package com.chua.starter.hotsport.support.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.beans.factory.annotation.Value;

/**
 * 慢调用过滤器自动配置 — 对 Servlet 应用通用生效。
 *
 * <p>开关：{@code hotsport.slow-call.enabled=true}（默认启用）；阈值：
 * {@code hotsport.slow-call.threshold-ms=1000}。</p>
 *
 * <p>注册顺序使用 {@code Ordered.LOWEST_PRECEDENCE}，避免干扰业务过滤链。</p>
 *
 * @author CH
 * @since 4.1.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "hotsport.slow-call", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class SlowCallAutoConfiguration {

    /**
     * 注册慢调用过滤器。
     *
     * @param thresholdMs 慢调用阈值（ms）
     * @return Filter 注册 Bean
     */
    @Bean
    public FilterRegistrationBean<SlowCallFilter> slowCallFilter(
            @Value("${plugin.hotsport.slow-call.threshold-ms:1000}") long thresholdMs) {
        SlowCallFilter filter = new SlowCallFilter(thresholdMs);
        FilterRegistrationBean<SlowCallFilter> bean = new FilterRegistrationBean<>(filter);
        bean.addUrlPatterns("/*");
        bean.setOrder(Ordered.LOWEST_PRECEDENCE);
        log.info("[Hotsport] SlowCallFilter 已注册，阈值 {}ms", thresholdMs);
        return bean;
    }
}

// [P3C A20 豁免] A20/A30/A31: 含 HTTP 状态码/请求头名/协议文案等非业务魔法值场景，豁免。
package com.chua.starter.common.support.api.sniff;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.util.Collections;

/**
 * 嗅探（bot 挑战）自动配置。
 *
 * <p>装配口径与 {@code CodecRequestOpen} / {@code CodecTraceOpen} 一致（{@code plugin.api.decode.enable}
 * 的 {@code matchIfMissing = true}）：<b>缺省即装配</b>，真正的开关是
 * {@code sys_setting}(group=config) 的 {@code CodecSniffOpen}（默认 false）。
 * 因此只把系统配置打开就生效，不需要改 yaml；yaml 只用于 {@code enabled=false} 硬关
 * 与 include/exclude/ttl/密钥等调参。</p>
 *
 * <p><b>顺序</b>：{@code HIGHEST_PRECEDENCE + 30} —— 晚于 CORS（{@code HIGHEST_PRECEDENCE}）、
 * 安全响应头（+10）与请求指纹（+20），保证挑战/拒绝响应也带跨域与安全头；
 * 早于业务与鉴权过滤器，避免可疑请求触达业务链路。</p>
 *
 * @author CH
 * @since 4.0.0.60
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BotSniffProperties.class)
@ConditionalOnProperty(prefix = "plugin.security.sniff", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BotSniffAutoConfiguration {

    /**
     * 过滤器注册顺序
     */
    private static final int FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 30;

    /**
     * 注册嗅探过滤器。
     *
     * @param properties 嗅探属性
     * @param publisher  事件发布器（用于通知业务模块记录审计日志）
     * @return 过滤器注册 Bean
     */
    @Bean("botSniffFilterRegistration")
    @ConditionalOnMissingBean(name = "botSniffFilterRegistration")
    public FilterRegistrationBean<BotSniffFilter> botSniffFilterRegistration(
            BotSniffProperties properties,
            ObjectProvider<ApplicationEventPublisher> publisher) {
        FilterRegistrationBean<BotSniffFilter> registration = new FilterRegistrationBean<>(
                new BotSniffFilter(properties, publisher.getIfAvailable()));
        registration.setUrlPatterns(Collections.singletonList("/*"));
        registration.setName("botSniffFilter");
        registration.setOrder(FILTER_ORDER);
        registration.setAsyncSupported(true);
        return registration;
    }
}

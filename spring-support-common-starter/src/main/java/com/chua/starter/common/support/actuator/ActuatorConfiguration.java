package com.chua.starter.common.support.actuator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

/**
 * Actuator 配置类
 * <p>
 * 统一管理 Actuator 相关的配置，包括：
 * <ul>
 *     <li>认证过滤器 - 保护 Actuator 端点</li>
 *     <li>IP 白名单 - 限制访问来源</li>
 * </ul>
 * </p>
 *
 * <h3>配置示例：</h3>
 * <pre>
 * plugin:
 *   actuator:
 *     enable-auth: true          # 是否开启认证
 *     username: admin            # 用户名
 *     password: admin123         # 密码
 *     ip-whitelist:              # IP 白名单
 *       - 127.0.0.1
 *       - 192.168.1.0/24
 *     exclude-paths:             # 排除的路径
 *       - /actuator/health
 * </pre>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/06/21
@ConditionalOnProperty(prefix = "plugin.actuator", name = "enable", havingValue = "true", matchIfMissing = false)
 */
@Slf4j
@RequiredArgsConstructor
@EnableConfigurationProperties(ActuatorProperties.class)
public class ActuatorConfiguration {

    private final ActuatorProperties actuatorProperties;

    /**
     * Actuator 认证过滤器
     * <p>
     * 对 /actuator/* 端点进行 Basic Auth 认证，防止未授权访问。
     * 支持 IP 白名单和路径排除。
     * </p>
     *
     * @return FilterRegistrationBean 过滤器注册 Bean
     */
    @Bean
    @ConditionalOnMissingBean(name = "actuatorAuthenticationFilterRegistration")
    @ConditionalOnProperty(name = "plugin.actuator.enable-auth", havingValue = "true")
    public FilterRegistrationBean<ActuatorAuthenticationFilter> actuatorAuthenticationFilterRegistration() {
        log.info(">>>>>>> 注册 Actuator 认证过滤器 [plugin.actuator.enable-auth=true]");
        
        FilterRegistrationBean<ActuatorAuthenticationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ActuatorAuthenticationFilter(actuatorProperties));
        registration.addUrlPatterns("/actuator/*");
        registration.setName("actuatorAuthenticationFilter");
        registration.setOrder(Integer.MIN_VALUE + 10);
        registration.setAsyncSupported(true);
        
        log.debug("Actuator 认证配置: username={}, ipWhitelistSize={}, excludePathsSize={}", 
                actuatorProperties.getUsername(),
                actuatorProperties.getIpWhitelist().size(),
                actuatorProperties.getExcludePaths().size());
        
        return registration;
    }
}

package com.chua.starter.common.support.actuator;
import com.chua.starter.common.support.application.ModuleEnvironmentRegistration;
import com.chua.starter.common.support.serviceinfo.ServiceInfoRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


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
 * @since 2024/06/21
 * @author CH
 */
@ConditionalOnProperty(prefix = "plugin.actuator", name = "enable", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ActuatorProperties.class)
public class ActuatorConfiguration  {
    /**
     * 日志
    */
    private static final Logger log = LoggerFactory.getLogger(ActuatorConfiguration.class);
    /**
     * 构造函数
     *
     * @param actuatorProperties actuator属性
     */
    public ActuatorConfiguration(ActuatorProperties actuatorProperties) {
        this.actuatorProperties = actuatorProperties;
    }

        /**
         * Actuator属性
        */
        private final ActuatorProperties actuatorProperties;

    /**
     * 注册 Actuator 配置到全局环境
     */
    @PostConstruct
    public void registerEnvironment() {
        ServiceInfoRegistry registry = ServiceInfoRegistry.getInstance();
        registry.registerFlag("actuator-auth", "监控", actuatorProperties.isEnableAuth(),
                "Actuator 认证过滤器", null);
        new ModuleEnvironmentRegistration("plugin.actuator", actuatorProperties, actuatorProperties.isEnable());
    }

    /**
     * Actuator 认证过滤器
     * <p>
     * 对 /actuator/* 端点进行 基础 认证 认证，防止未授权访问。
     * 支持 IP 白名单和路径排除。
     * </p>
     *
     * @return FilterRegistrationBean 过滤器注册 Bean
     */
    @Bean
    @ConditionalOnMissingBean(name = "actuatorAuthenticationFilterRegistration")
    @ConditionalOnProperty(name = "plugin.actuator.enable-auth", havingValue = "true")
    public FilterRegistrationBean<ActuatorAuthenticationFilter> actuatorAuthenticationFilterRegistration() {
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

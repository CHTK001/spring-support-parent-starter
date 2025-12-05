package com.chua.starter.configcenter.support.configuration;

import com.chua.common.support.config.ConfigCenter;
import com.chua.common.support.config.setting.ConfigCenterSetting;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.configcenter.support.processor.ConfigValueBeanPostProcessor;
import com.chua.starter.configcenter.support.properties.ConfigCenterProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * ConfigValue自动配置
 * <p>
 * 配置@ConfigValue注解的自动处理和热更新功能。
 * </p>
 *
 * @author CH
 * @since 2024-12-05
 * @version 1.0.0
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(ConfigCenterProperties.class)
@ConditionalOnProperty(prefix = ConfigCenterProperties.PRE, name = "enable", havingValue = "true")
public class ConfigValueAutoConfiguration {

    @Autowired
    private Environment environment;

    /**
     * 创建配置中心
     *
     * @param properties 配置属性
     * @return 配置中心实例
     */
    @Bean
    public ConfigCenter configCenter(ConfigCenterProperties properties) {
        String active = environment.getProperty("spring.profiles.active");
        ConfigCenter configCenter = ServiceProvider.of(ConfigCenter.class)
                .getNewExtension(properties.getProtocol(), ConfigCenterSetting.builder()
                        .address(properties.getAddress())
                        .username(properties.getUsername())
                        .password(properties.getPassword())
                        .connectionTimeout(properties.getConnectTimeout())
                        .readTimeout(properties.getReadTimeout())
                        .profile(StringUtils.defaultString(properties.getNamespaceId(), active))
                        .build());

        if (configCenter != null) {
            configCenter.start();
            log.info("配置中心启动成功: protocol={}, address={}",
                    properties.getProtocol(), properties.getAddress());
        }

        return configCenter;
    }

    /**
     * 创建ConfigValue Bean后置处理器
     *
     * @param configCenter 配置中心
     * @return Bean后置处理器
     */
    @Bean
    public ConfigValueBeanPostProcessor configValueBeanPostProcessor(
            @Autowired(required = false) ConfigCenter configCenter) {
        log.info("注册ConfigValue后置处理器，热更新功能{}",
                configCenter != null && configCenter.isSupportListener() ? "已启用" : "未启用");
        return new ConfigValueBeanPostProcessor(configCenter);
    }
}

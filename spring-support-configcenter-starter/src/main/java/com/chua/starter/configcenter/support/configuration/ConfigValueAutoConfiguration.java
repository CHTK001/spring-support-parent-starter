package com.chua.starter.configcenter.support.configuration;

import com.chua.common.support.config.ConfigCenter;
import com.chua.starter.common.support.application.ModuleEnvironmentRegistration;
import com.chua.starter.configcenter.support.holder.ConfigCenterHolder;
import com.chua.starter.configcenter.support.processor.ConfigValueBeanPostProcessor;
import com.chua.starter.configcenter.support.processor.ValueAnnotationBeanPostProcessor;
import com.chua.starter.configcenter.support.properties.ConfigCenterProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ConfigValue自动配置
 * <p>
 * 配置@ConfigValue注解的自动处理和热更新功能。
 * 复用 EnvironmentPostProcessor 阶段创建的 ConfigCenter 实例，
 * 避免重复创建和资源浪费。
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

    /**
     * 获取配置中心 Bean
     * <p>
     * 复用 ConfigCenterConfigurationEnvironmentPostProcessor 创建的实例，
     * 避免重复创建 ConfigCenter。
     * </p>
     *
     * @return 配置中心实例
     */
    @Bean
    public ConfigCenter configCenter() {
        ConfigCenter configCenter = ConfigCenterHolder.getInstance();
        if (configCenter != null) {
            log.info("[ConfigCenter] 复用已创建的 ConfigCenter 实例, 支持监听: {}", 
                    configCenter.isSupportListener());
        } else {
            log.warn("[ConfigCenter] ConfigCenter 实例未初始化, @ConfigValue 热更新功能将不可用");
        }
        return configCenter;
    }

    /**
     * 创建 ConfigValue Bean 后置处理器
     * <p>
     * 负责扫描 @ConfigValue 注解并注册热更新监听。
     * </p>
     *
     * @param configCenter 配置中心
     * @param properties   配置属性
     * @return Bean后置处理器
     */
    @Bean
    public ConfigValueBeanPostProcessor configValueBeanPostProcessor(
            ConfigCenter configCenter, 
            ConfigCenterProperties properties) {
        boolean hotReloadEnabled = properties.getHotReload().isEnabled() 
                && properties.getHotReload().isConfigValueAnnotationEnabled();
        boolean supportListener = configCenter != null && configCenter.isSupportListener();
        long refreshDelayMs = properties.getHotReload().getRefreshDelayMs();
        
        log.info("[ConfigCenter] 注册 ConfigValue 后置处理器, 热更新功能: {}", 
                (hotReloadEnabled && supportListener) ? "已启用" : "未启用");
        
        return new ConfigValueBeanPostProcessor(configCenter, hotReloadEnabled, refreshDelayMs, properties.getHotReload().isLogOnChange());
    }

    /**
     * 创建 @Value 注解 Bean 后置处理器
     * <p>
     * 负责扫描 @Value 注解并注册热更新监听和配置缓存。
     * </p>
     *
     * @param configCenter 配置中心
     * @param properties   配置属性
     * @return Bean后置处理器
     */
    @Bean
    public ValueAnnotationBeanPostProcessor valueAnnotationBeanPostProcessor(
            ConfigCenter configCenter, 
            ConfigCenterProperties properties) {
        boolean hotReloadEnabled = properties.getHotReload().isEnabled() 
                && properties.getHotReload().isValueAnnotationEnabled();
        boolean supportListener = configCenter != null && configCenter.isSupportListener();
        long refreshDelayMs = properties.getHotReload().getRefreshDelayMs();
        
        log.info("[ConfigCenter] 注册 @Value 后置处理器, 热更新功能: {}", 
                (hotReloadEnabled && supportListener) ? "已启用" : "未启用");
        
        return new ValueAnnotationBeanPostProcessor(configCenter, hotReloadEnabled, refreshDelayMs);
    }

    /**
     * 注册配置中心属性到全局环境
     *
     * @param properties 配置中心属性
     * @return 模块环境注册器
     */
    @Bean
    public ModuleEnvironmentRegistration configCenterModuleEnvironment(ConfigCenterProperties properties) {
        return new ModuleEnvironmentRegistration(ConfigCenterProperties.PRE, properties, properties.isEnable());
    }

    /**
     * 应用关闭时清理资源
     */
    @PreDestroy
    public void destroy() {
        ConfigCenterHolder.shutdown();
    }
}

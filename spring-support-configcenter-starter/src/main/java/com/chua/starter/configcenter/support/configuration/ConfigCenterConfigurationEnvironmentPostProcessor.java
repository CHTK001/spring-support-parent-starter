package com.chua.starter.configcenter.support.configuration;

import com.chua.common.support.config.ConfigCenter;
import com.chua.common.support.config.ConfigListener;
import com.chua.common.support.config.setting.ConfigCenterSetting;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.configcenter.support.holder.ConfigCenterHolder;
import com.chua.starter.configcenter.support.properties.ConfigCenterProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.boot.env.SystemEnvironmentPropertySourceEnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 配置中心环境后置处理器
 * <p>
 * 在 Spring 环境准备阶段从配置中心加载配置，并注册配置变更监听器。
 * </p>
 *
 * @author CH
 * @since 2024/9/9
 */
@Slf4j
@EnableConfigurationProperties(ConfigCenterProperties.class)
public class ConfigCenterConfigurationEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /**
     * 已加载的配置文件名称
     */
    private static final Set<String> LOADED_CONFIG_NAMES = ConcurrentHashMap.newKeySet();

    /**
     * 环境引用（用于配置更新）
     */
    private static volatile ConfigurableEnvironment environmentRef;

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        log.info("[配置中心]开始处理配置中心");
        environmentRef = environment;
        
        ConfigCenterProperties configCenterProperties = Binder.get(environment)
                .bindOrCreate(ConfigCenterProperties.PRE, ConfigCenterProperties.class);
        if (!configCenterProperties.isEnable()) {
            log.warn("[配置中心]配置中心未启用");
            return;
        }
        
        log.info("[配置中心]开始加载配置中心: {}", configCenterProperties.getProtocol());
        String active = environment.getProperty("spring.profiles.active");
        log.info("[配置中心]当前环境: {}", active);
        
        // 创建 ConfigCenter 实例
        ConfigCenter configCenter = ServiceProvider.of(ConfigCenter.class)
                .getNewExtension(configCenterProperties.getProtocol(), ConfigCenterSetting.builder()
                        .address(configCenterProperties.getAddress())
                        .username(configCenterProperties.getUsername())
                        .password(configCenterProperties.getPassword())
                        .connectionTimeout(configCenterProperties.getConnectTimeout())
                        .readTimeout(configCenterProperties.getReadTimeout())
                        .profile(StringUtils.defaultString(configCenterProperties.getNamespaceId(), active))
                        .build());
        
        if (null == configCenter) {
            log.warn("[配置中心]暂不支持{}, 请重新设置!", configCenterProperties.getProtocol());
            return;
        }

        configCenter.start();
        
        // 保存到全局持有者，供后续使用
        ConfigCenterHolder.setInstance(configCenter);

        // 加载配置
        loadConfigurations(environment, configCenter, active);
        
        // 注册配置变更监听（根据配置决定是否启用）
        if (configCenterProperties.getHotReload().isEnabled()) {
            registerConfigListener(configCenter, configCenterProperties);
            log.info("[配置中心]热更新已启用");
        } else {
            log.info("[配置中心]热更新已禁用");
        }
        
        log.info("[配置中心]配置中心初始化完成，支持监听: {}", configCenter.isSupportListener());
    }

    /**
     * 加载配置文件
     * <p>
     * 配置加载优先级（从高到低）：
     * 1. 远程配置中心：Application-{appName}-{profile}（如：Application-xxx-dev）
     * 2. 远程配置中心：Application-{appName}（如：Application-xxx）
     * 3. spring.profiles.include 指定的配置（带环境后缀）：application-{name}-{profile}.yml
     * 4. spring.profiles.include 指定的配置（不带环境后缀）：application-{name}.yml
     * </p>
     *
     * @param environment 环境对象
     * @param configCenter 配置中心实例
     * @param active 激活的环境
     */
    private void loadConfigurations(ConfigurableEnvironment environment, ConfigCenter configCenter, String active) {
        // 1. 优先加载默认的 Application 配置（基于 spring.application.name）
        String applicationName = environment.getProperty("spring.application.name");
        if (StringUtils.isNotEmpty(applicationName) && StringUtils.isNotEmpty(active)) {
            // 1.1 优先加载 Application-{appName}-{profile}（远程配置中心，带环境后缀）
            String appConfigWithProfile = "Application-%s-%s".formatted(applicationName, active);
            loadConfigIfExists(environment, configCenter, appConfigWithProfile);
            
            // 1.2 加载 Application-{appName}（远程配置中心，不带环境后缀）
            String appConfig = "Application-%s".formatted(applicationName);
            loadConfigIfExists(environment, configCenter, appConfig);
        }
        
        // 2. 加载 spring.profiles.include 指定的配置
        String include = environment.getProperty("spring.profiles.include");
        if (StringUtils.isEmpty(include)) {
            return;
        }
        
        List<String> strings = Splitter.on(",").trimResults().omitEmptyStrings().splitToList(include);
        List<String> loaded = new ArrayList<>(strings.size());
        
        // 2.1 优先加载带环境后缀的配置
        for (String string : strings) {
            String newName = "application-%s-%s.yml".formatted(string, active);
            Map<String, Object> stringObjectMap = configCenter.get(newName);
            if (null == stringObjectMap || stringObjectMap.isEmpty()) {
                continue;
            }

            environment.getPropertySources()
                    .addLast(new OriginTrackedMapPropertySource(newName, stringObjectMap));
            LOADED_CONFIG_NAMES.add(newName);
            log.info("[配置中心]加载配置: {}", newName);
            loaded.add(string);
        }
        
        // 2.2 加载不带环境后缀的配置
        for (String string : strings) {
            if (loaded.contains(string)) {
                log.debug("[配置中心]已加载: {}-{}, 忽略{}", string, active, string);
                continue;
            }
            String newName = "application-" + string + ".yml";
            Map<String, Object> stringObjectMap = configCenter.get(newName);
            if (null == stringObjectMap) {
                continue;
            }

            environment.getPropertySources()
                    .addLast(new OriginTrackedMapPropertySource(newName, stringObjectMap));
            LOADED_CONFIG_NAMES.add(newName);
            log.info("[配置中心]加载配置: {}", newName);
        }
    }

    /**
     * 加载配置文件（如果存在）
     *
     * @param environment 环境对象
     * @param configCenter 配置中心实例
     * @param configName 配置名称
     */
    private void loadConfigIfExists(ConfigurableEnvironment environment, ConfigCenter configCenter, String configName) {
        Map<String, Object> configMap = configCenter.get(configName);
        if (configMap == null || configMap.isEmpty()) {
            log.debug("[配置中心]配置不存在，跳过: {}", configName);
            return;
        }

        environment.getPropertySources()
                .addLast(new OriginTrackedMapPropertySource(configName, configMap));
        LOADED_CONFIG_NAMES.add(configName);
        log.info("[配置中心]加载配置: {}", configName);
    }

    /**
     * 注册配置变更监听器
     *
     * @param configCenter 配置中心
     * @param properties   配置属性
     */
    private void registerConfigListener(ConfigCenter configCenter, ConfigCenterProperties properties) {
        if (!configCenter.isSupportListener()) {
            log.warn("[配置中心]当前配置中心不支持监听功能");
            return;
        }

        long refreshDelayMs = properties.getHotReload().getRefreshDelayMs();
        boolean logOnChange = properties.getHotReload().isLogOnChange();

        // 为每个已加载的配置文件注册监听
        for (String configName : LOADED_CONFIG_NAMES) {
            configCenter.addListener(configName, new EnvironmentConfigListener(configName, refreshDelayMs, logOnChange));
            log.info("[配置中心]注册配置监听: {}", configName);
        }
    }

    /**
     * 环境配置监听器
     * <p>
     * 当配置中心的配置发生变化时，更新 Spring Environment
     * </p>
     */
    private static class EnvironmentConfigListener implements ConfigListener {
        
        private final String configName;
        private final long refreshDelayMs;
        private final boolean logOnChange;
        private volatile long lastRefreshTime = 0;

        EnvironmentConfigListener(String configName, long refreshDelayMs, boolean logOnChange) {
            this.configName = configName;
            this.refreshDelayMs = refreshDelayMs;
            this.logOnChange = logOnChange;
        }

        @Override
        public void onChange(String key, String oldValue, String newValue) {
            if (logOnChange) {
                log.info("[配置中心]配置变更: configName={}, key={}, oldValue={}, newValue={}", 
                        configName, key, oldValue, newValue);
            }
        }

        @Override
        public void onUpdate(String key, String oldValue, String newValue) {
            if (environmentRef == null) {
                return;
            }
            
            // 防抖处理：避免配置频繁变更导致抖动
            long now = System.currentTimeMillis();
            if (now - lastRefreshTime < refreshDelayMs) {
                log.debug("[配置中心]配置更新过于频繁，跳过本次更新: configName={}", configName);
                return;
            }
            lastRefreshTime = now;
            
            if (logOnChange) {
                log.info("[配置中心]配置更新: configName={}, key={}", configName, key);
            }
            
            // 重新加载配置
            ConfigCenter configCenter = ConfigCenterHolder.getInstance();
            if (configCenter == null) {
                return;
            }
            
            Map<String, Object> newConfig = configCenter.get(configName);
            if (newConfig == null || newConfig.isEmpty()) {
                return;
            }
            
            // 更新 PropertySource
            MutablePropertySources propertySources = environmentRef.getPropertySources();
            if (propertySources.contains(configName)) {
                propertySources.replace(configName, new OriginTrackedMapPropertySource(configName, newConfig));
            } else {
                propertySources.addLast(new OriginTrackedMapPropertySource(configName, newConfig));
            }
            
            if (logOnChange) {
                log.info("[配置中心]已更新环境配置: {}", configName);
            }
        }

        @Override
        public void onDelete(String key, String oldValue) {
            if (logOnChange) {
                log.info("[配置中心]配置删除: configName={}, key={}", configName, key);
            }
        }
    }

    @Override
    public int getOrder() {
        return SystemEnvironmentPropertySourceEnvironmentPostProcessor.DEFAULT_ORDER - 100;
    }
}

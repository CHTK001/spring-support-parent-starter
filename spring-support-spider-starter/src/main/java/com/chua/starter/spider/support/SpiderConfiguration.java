package com.chua.starter.spider.support;

import com.chua.common.support.ai.brain.Brain;
import com.chua.spider.support.brain.SpiderBrainHook;
import com.chua.spider.support.brain.SpiderBrainRegistry;
import com.chua.spider.support.model.SpiderBrainDefinition;
import com.chua.starter.common.support.application.ModuleEnvironmentRegistration;
import com.chua.starter.spider.support.properties.SpiderProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;

/**
 * 爬虫模块自动配置。
 *
 * <p>用于把 Spring 配置转换为 Spider 默认大脑配置。业务没有显式调用
 * {@code site.setBrain(...)} 时，会自动回退到这里注册的默认配置。
 *
 * @author CH
 * @since 2026/04/02
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(SpiderProperties.class)
@ConditionalOnClass({SpiderBrainRegistry.class, SpiderBrainDefinition.class})
@ConditionalOnProperty(prefix = SpiderProperties.PREFIX, name = "enable", havingValue = "true", matchIfMissing = true)
public class SpiderConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = SpiderProperties.PREFIX + ".brain", name = "enable", havingValue = "true")
    public SpiderBrainDefinition spiderBrainDefinition(SpiderProperties properties,
                                                       ObjectProvider<Brain> brainProvider,
                                                       ObjectProvider<SpiderBrainHook> hookProvider) {
        SpiderProperties.BrainProperties brain = properties.getBrain();
        SpiderBrainDefinition definition = new SpiderBrainDefinition();
        definition.setEnabled(true);
        definition.setTakeover(brain.getTakeover());
        definition.setProvider(brain.getProvider());
        definition.setApiKey(firstNonBlank(brain.getApiKey(), brain.getAppKey()));
        definition.setBaseUrl(brain.getBaseUrl());
        definition.setModel(brain.getModel());
        definition.setSystemPrompt(brain.getSystemPrompt());
        definition.setSessionId(brain.getSessionId());
        definition.setCredentialKey(brain.getCredentialKey());
        definition.setMcpConfigPaths(new ArrayList<>(brain.getMcpConfigPaths()));
        definition.setBrainInstance(brainProvider.getIfAvailable());
        definition.setHooks(new ArrayList<>(hookProvider.orderedStream().toList()));
        return definition;
    }

    @Bean
    @ConditionalOnBean(SpiderBrainDefinition.class)
    public SpiderBrainRegistrar spiderBrainRegistrar(SpiderBrainDefinition spiderBrainDefinition) {
        return new SpiderBrainRegistrar(spiderBrainDefinition);
    }

    @Bean
    public ModuleEnvironmentRegistration spiderModuleEnvironment(SpiderProperties properties) {
        return new ModuleEnvironmentRegistration(SpiderProperties.PREFIX, properties, properties.isEnable());
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    @Slf4j
    @RequiredArgsConstructor
    public static class SpiderBrainRegistrar implements InitializingBean, DisposableBean {

        private final SpiderBrainDefinition spiderBrainDefinition;

        @Override
        public void afterPropertiesSet() {
            SpiderBrainRegistry.registerDefault(spiderBrainDefinition);
            log.info("[Spider] 默认大脑已注册, provider={}, model={}",
                    spiderBrainDefinition.getProvider(), spiderBrainDefinition.getModel());
        }

        @Override
        public void destroy() {
            SpiderBrainRegistry.clearDefault();
            log.info("[Spider] 默认大脑已清理");
        }
    }
}

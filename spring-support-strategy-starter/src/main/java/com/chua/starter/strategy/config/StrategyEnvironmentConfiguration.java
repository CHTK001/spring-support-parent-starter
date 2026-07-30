package com.chua.starter.strategy.config;

import com.chua.starter.common.support.application.ModuleEnvironmentRegistration;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 策略模块环境配置
 * <p>
 * 将策略模块的配置注册到 spring-common 的全局环境配置中心，
 * 便于统一管理和监控模块的开关状态。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-02
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(StrategyProperties.class)
public class StrategyEnvironmentConfiguration {

    private final StrategyProperties strategyProperties;

    /**
     * 注册策略配置到全局环境
     */
    @PostConstruct
    public void registerEnvironment() {
        new ModuleEnvironmentRegistration(
                "plugin.strategy",
                strategyProperties,
                strategyProperties.isEnable()
        );
        log.debug("[策略模块][环境配置]已注册策略模块配置到全局环境中心");
    }
}


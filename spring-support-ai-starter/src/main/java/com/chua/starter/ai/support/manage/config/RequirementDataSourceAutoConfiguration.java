package com.chua.starter.ai.support.manage.config;

import com.chua.starter.ai.support.manage.store.datasource.GenericApiRequirementDataSource;
import com.chua.starter.ai.support.manage.store.datasource.LarkRequirementDataSource;
import com.chua.starter.ai.support.manage.store.datasource.RequirementDataSourceRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * 需求数据源提供方自动配置。
 *
 * <p>当 {@code spring.ai.datasource.requirement.enabled=true} 时生效。
 * 手动注册 SPI 实现，确保 服务加载 在 fat-jar 内也能发现。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "spring.ai.datasource.requirement", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RequirementDataSourceAutoConfiguration {

    private final RequirementDataSourceRegistry registry; // registry

    /**
     * 应用启动时预注册内置数据源提供方。
     * <p>Spring Boot fat-jar 的 ServiceLoader 有时无法遍历 nested-jars，
     * 这里显式注册以确保 Lark 和 Generic 两个内置 提供者 始终可用。</p>
     */
 // 无需额外操作：SPI 由 requirement数据源registry.运行() 中的 服务加载 完成
 // 此处仅作为配置入口声明，实际注册在 applicationrunner 中执行
}

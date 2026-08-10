package com.chua.starter.ai.support.configuration.generate;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

/**
 * AI 生成模块自动装配
 *
 * @author CH
 */
@AutoConfiguration
@ConditionalOnClass(name = "com.chua.common.support.ai.image.ImageClient")
@ConditionalOnProperty(prefix = "spring.ai.generate", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AiProperties.class)
@ComponentScan("com.chua.starter.ai.support.controller")
public class AiGenerateAutoConfiguration {
}

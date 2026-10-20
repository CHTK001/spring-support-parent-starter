package com.chua.starter.ai.support.configuration.generate;

import com.chua.starter.ai.support.configuration.core.AiProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

/**
 * AI 生成模块自动装配
 *
 * @author CH
 * @since 4.0.0
 */
@AutoConfiguration
@ConditionalOnClass(name = "com.chua.common.support.ai.image.ImageClient")
@ConditionalOnProperty(prefix = "plugin.ai.generate", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AiProperties.class)
@ComponentScan("com.chua.starter.ai.support.controller")
public class AiGenerateAutoConfiguration {
}

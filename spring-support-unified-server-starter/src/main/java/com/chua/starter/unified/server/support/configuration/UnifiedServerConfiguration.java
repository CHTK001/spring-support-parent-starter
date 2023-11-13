package com.chua.starter.unified.server.support.configuration;

import com.chua.starter.unified.server.support.properties.UnifiedServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 统一服务器配置
 *
 * @author CH
 */
@EnableConfigurationProperties(UnifiedServerProperties.class)
public class UnifiedServerConfiguration {
}

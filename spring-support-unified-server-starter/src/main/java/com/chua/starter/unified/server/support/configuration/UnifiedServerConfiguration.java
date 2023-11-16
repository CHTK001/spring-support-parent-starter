package com.chua.starter.unified.server.support.configuration;

import com.chua.starter.unified.server.support.properties.UnifiedServerProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

/**
 * 统一服务器配置
 *
 * @author CH
 */
@ComponentScan(basePackages = "com.chua.starter.unified.server.support")
@MapperScan(basePackages = "com.chua.starter.unified.server.support")
@EnableConfigurationProperties(UnifiedServerProperties.class)
public class UnifiedServerConfiguration {


}

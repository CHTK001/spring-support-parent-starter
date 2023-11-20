package com.chua.starter.unified.server.support.configuration;

import com.chua.starter.unified.server.support.properties.UnifiedServerProperties;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;

/**
 * 统一服务器配置
 *
 * @author CH
 */
@EnableCaching
@ComponentScan(basePackages = "com.chua.starter.unified.server.support")
@MapperScan(basePackages = "com.chua.starter.unified.server.support", annotationClass = Mapper.class)
@EnableConfigurationProperties(UnifiedServerProperties.class)
public class UnifiedServerConfiguration {


}

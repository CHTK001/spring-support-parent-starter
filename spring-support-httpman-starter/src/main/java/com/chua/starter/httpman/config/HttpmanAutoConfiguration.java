package com.chua.starter.httpman.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

/**
 * 接口调试控制台（HttpMan）自动配置
 * <p>包根为 {@code com.chua.starter.httpman}，不在宿主应用的扫描路径内，因此必须通过
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * 注册本类，由它扫描控制器、服务、数据访问，并使 {@link HttpmanProperties} 生效。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.chua.starter.httpman")
@EnableConfigurationProperties(HttpmanProperties.class)
@MapperScan("com.chua.starter.httpman.mapper")
public class HttpmanAutoConfiguration {
}

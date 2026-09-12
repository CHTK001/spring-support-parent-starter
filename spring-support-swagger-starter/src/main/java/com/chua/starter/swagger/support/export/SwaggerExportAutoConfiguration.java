package com.chua.starter.swagger.support.export;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
* Swagger 导出自动配置。
*
* <p>启用 {@link SwaggerExportProperties} + {@link SwaggerExportRunner}。</p>
*
* @since 4.0.0.42
* @author CH
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication
@ConditionalOnClass(name = "io.swagger.v3.oas.models.OpenAPI")
@EnableConfigurationProperties(SwaggerExportProperties.class)
public class SwaggerExportAutoConfiguration {
}
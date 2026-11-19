package com.chua.starter.cdp.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

/**
 * CDP 调试模块自动配置
 *
 * <p>扫描 {@code com.chua.starter.cdp} 下的全部组件（引擎服务、REST 控制器、
 * WebSocket 端点、实时页面反向代理），并注册 {@link CdpProperties}
 * （{@code spring.cdp.*}）。宿主引入 {@code spring-support-cdp-starter}
 * 依赖即获得完整 CDP 能力；{@code spring.cdp.enabled=false} 可整体关闭。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.chua.starter.cdp")
@EnableConfigurationProperties(CdpProperties.class)
public class CdpAutoConfiguration {
}

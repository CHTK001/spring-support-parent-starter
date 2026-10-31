package com.chua.starter.cdp.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * CDP 调试模块自动配置
 *
 * <p>包根为 {@code com.chua.starter.cdp}，不在宿主应用的扫描路径内，因此必须通过
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * 注册本类，由它扫描控制器、服务、WS 处理器。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.chua.starter.cdp")
public class CdpAutoConfiguration {
}

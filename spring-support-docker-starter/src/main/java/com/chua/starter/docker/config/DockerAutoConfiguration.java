package com.chua.starter.docker.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * Docker 模块自动配置。
 *
 * <p>Docker 已从 {@code spring-support-server-starter} 拆成独立模块，包根为
 * {@code com.chua.starter.docker}，不在宿主应用的扫描路径内，因此必须通过
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * 注册本类，由它扫描控制器、服务、镜像库 SPI 实现。</p>
 *
 * <p>本模块只依赖 {@code spring-support-server-starter}（主机台账、凭据解析、
 * SSH 连接管理），不反向依赖 monitor，避免循环依赖。</p>
 *
 * @author CH
 * @since 4.0.0.46
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.chua.starter.docker")
public class DockerAutoConfiguration {
}

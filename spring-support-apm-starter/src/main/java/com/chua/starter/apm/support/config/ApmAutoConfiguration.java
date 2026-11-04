package com.chua.starter.apm.support.config;

import com.chua.starter.apm.support.ApmRegistry;
import com.chua.starter.apm.support.model.ApmDescriptor;
import com.chua.starter.apm.support.properties.ApmProperties;
import com.chua.starter.apm.support.server.ApmStandaloneServer;
import com.chua.starter.apm.support.servlet.ApmServlet;
import com.chua.starter.apm.support.web.ApmDispatcher;
import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;

/**
 * APM 模块自动配置。
 * <p>
 * 装配链路：{@link ApmRegistry}（自动探测当前系统有没有 APM）→ {@link ApmDispatcher}
 * （统一路由，协议无关）→ 两种出口之一：
 * </p>
 * <ul>
 *   <li>{@code plugin.apm.mode=embedded}（默认）：注册 {@link ApmServlet}，随宿主 Spring 启动；</li>
 *   <li>{@code plugin.apm.mode=standalone}：用 {@link ApmStandaloneServer} 单独起一个 HTTP 服务。</li>
 * </ul>
 * <p>
 * 没有任何 APM 时模块照常装配，接口照常可访问（{@code /status} 返回
 * {@code present=false} 与原因），前端据此渲染「本服务器未接入 APM」的降级页。
 * </p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(ApmProperties.class)
@ConditionalOnProperty(prefix = "plugin.apm", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ApmAutoConfiguration {

    /**
     * 引擎注册表
     *
     * @param properties 配置
     * @return 注册表
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public ApmRegistry apmRegistry(ApmProperties properties) {
        return new ApmRegistry(properties);
    }

    /**
     * 统一路由器
     *
     * @param registry   注册表
     * @param properties 配置
     * @return 路由器
     */
    @Bean
    @ConditionalOnMissingBean
    public ApmDispatcher apmDispatcher(ApmRegistry registry, ApmProperties properties) {
        return new ApmDispatcher(registry, properties);
    }

    /**
     * 嵌入模式：把 {@link ApmServlet} 注册进宿主容器
     *
     * @param dispatcher 路由器
     * @param mapper     JSON 序列化器
     * @param properties 配置
     * @return Servlet 注册
     */
    @Bean
    @ConditionalOnClass(name = "jakarta.servlet.Servlet")
    @ConditionalOnProperty(prefix = "plugin.apm", name = "mode", havingValue = "embedded", matchIfMissing = true)
    public ServletRegistrationBean<ApmServlet> apmServletRegistration(ApmDispatcher dispatcher,
                                                                     ObjectMapper mapper,
                                                                     ApmProperties properties) {
        String path = normalizedPath(properties);
        String mapping = path + "/*";
        ServletRegistrationBean<ApmServlet> registration =
                new ServletRegistrationBean<>(new ApmServlet(dispatcher, mapper), mapping);
        registration.setName("apmServlet");
        registration.setLoadOnStartup(1);
        registration.setAsyncSupported(true);
        log.info("[APM] 嵌入模式已注册 Servlet: {}", mapping);
        return registration;
    }

    /**
     * 独立模式：单独起一个 HTTP 服务
     *
     * @param dispatcher 路由器
     * @param mapper     JSON 序列化器
     * @param properties 配置
     * @return 独立服务
     */
    @Bean(destroyMethod = "stop")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "plugin.apm", name = "mode", havingValue = "standalone")
    public ApmStandaloneServer apmStandaloneServer(ApmDispatcher dispatcher,
                                                   ObjectMapper mapper,
                                                   ApmProperties properties) {
        ApmStandaloneServer server = new ApmStandaloneServer(properties, dispatcher, mapper);
        server.start();
        return server;
    }

    /**
     * 注册到统一服务信息面板：一眼看出本机有没有 APM、是哪个
     *
     * @param registry   注册表
     * @param properties 配置
     * @return 服务信息贡献者
     */
    @Bean
    @ConditionalOnMissingBean(name = "apmServiceInfoContributor")
    @ConditionalOnProperty(prefix = "plugin.apm", name = "register-service-info",
            havingValue = "true", matchIfMissing = true)
    public ServiceInfoContributor apmServiceInfoContributor(ApmRegistry registry, ApmProperties properties) {
        String path = normalizedPath(properties);
        return infoRegistry -> {
            ApmDescriptor descriptor = registry.describe();
            String suffix = descriptor.present()
                    ? "[" + descriptor.name() + ": " + descriptor.mode() + "]"
                    : "[" + descriptor.message() + "]";
            infoRegistry.registerFlag("apm", "APM", descriptor.present(), "APM 模块", suffix);
            infoRegistry.registerEndpoint("APM", "APM 接口", path + "/status",
                    "APM 统一接口（是否存在/启用了 APM）");
        };
    }

    /**
     * 规范化统一前缀
     *
     * @param properties 配置
     * @return 前缀，以 {@code /} 开头且不以 {@code /} 结尾
     */
    private String normalizedPath(ApmProperties properties) {
        String path = properties.getPath() == null ? "/api/apm/v1" : properties.getPath().trim();
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path;
    }
}

package com.chua.starter.common.support.serviceinfo;

import org.springframework.core.env.Environment;

/**
 * 端点探测器: 探测 Swagger / Actuator 等基础端点是否启用。
 *
 * <p>在应用就绪时由 {@link ServiceInfoBannerListener} 调用,
 * 把探测结果注册到 {@link ServiceInfoRegistry}。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public class ServiceInfoEndpointDetector {

    /** 环境 */
    private final Environment environment;

    /**
     * 创建 ServiceInfoEndpointDetector 实例
     * @param environment environment
     */
    public ServiceInfoEndpointDetector(Environment environment) {
        this.environment = environment;
    }

    /**
     * 探测基础端点 (应用自身 / Swagger / Actuator) 并注册到 {@link ServiceInfoRegistry}。
     */
    public void detectAndRegister() {
        String port = environment.getProperty("server.port", "8080");
        String contextPath = environment.getProperty("server.servlet.context-path", "");
        if (contextPath == null) contextPath = "";
        String trimmedContext = contextPath.replaceAll("^/|/$", "");
        String base = "http://127.0.0.1:" + port
                + (trimmedContext.isEmpty() ? "" : "/" + trimmedContext);

        ServiceInfoRegistry registry = ServiceInfoRegistry.getInstance();

        registry.registerEndpoint("通用", "应用自身",
                base + "/service-info", "服务信息聚合端点");

        boolean hasSwagger = hasProperty("springdoc.swagger-ui.enabled")
                || hasProperty("knife4j.enable")
                || hasProperty("plugin.swagger.enable");
        if (hasSwagger) {
            registry.registerEndpoint("文档", "swagger",
                    base + "/doc.html", "Knife4j 接口文档");
        }
        registry.registerFlag("swagger", "文档", hasSwagger, "Knife4j 接口文档");

        boolean hasActuator = hasProperty("management.endpoints.web.exposure.include");
        if (hasActuator) {
            registry.registerEndpoint("监控", "actuator",
                    base + "/actuator", "Spring Boot Actuator");
        }
        registry.registerFlag("actuator", "监控", hasActuator, "Spring Boot Actuator");
    }

    /** 是否拥有Property */
    private boolean hasProperty(String key) {
        return environment.getProperty(key) != null;
    }
}

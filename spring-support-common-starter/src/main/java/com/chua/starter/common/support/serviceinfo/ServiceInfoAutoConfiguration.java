package com.chua.starter.common.support.serviceinfo;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 服务信息自动配置 (统一入口)。
 *
 * <p>这是 spring-support-common-starter 提供的统一服务体系, 任何业务 starter
 * 都通过 {@link ServiceInfoRegistry} 注册自己的端点和开关, 启动时统一呈现。</p>
 *
 * <h3>职责</h3>
 * <ol>
 *   <li>注册基础端点 (应用自身 / Swagger / Actuator 等)</li>
 *   <li>启动后输出美化日志 (使用 {@link AnsiStyle} 统一颜色) -
 *       唯一的 ANSI 渲染点, 业务模块不允许自行输出 ANSI 转义</li>
 *   <li>暴露 {@code GET /service-info} REST 端点</li>
 * </ol>
 *
 * <h3>不要破坏的唯一性</h3>
 * <ul>
 *   <li>本类是业务 starter 唯一允许实现 {@link CommandLineRunner} 的位置 (用于启动横幅渲染)</li>
 *   <li>所有业务模块都应改用 {@link ServiceInfoRegistry} 注册数据</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@AutoConfiguration
@ConditionalOnWebApplication
public class ServiceInfoAutoConfiguration {

    @Bean
    public ServiceInfoController serviceInfoController() {
        return new ServiceInfoController();
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public ServiceInfoRunner serviceInfoRunner(ApplicationContext context, Environment environment) {
        return new ServiceInfoRunner(context, environment);
    }

    @RestController
    @RequestMapping("/service-info")
    public static class ServiceInfoController {
        @GetMapping
        public Map<String, Object> info() {
            return ServiceInfoRegistry.getInstance().getInfo();
        }
    }

    /**
     * 全局启动信息 Runner, 由 ServiceInfoAutoConfiguration 单例化。
     * 业务模块只需要往 {@link ServiceInfoRegistry} 注册, 这里会一并展示。
     */
    public static class ServiceInfoRunner implements CommandLineRunner {

        private final ApplicationContext context;
        private final Environment environment;

        public ServiceInfoRunner(ApplicationContext context, Environment environment) {
            this.context = context;
            this.environment = environment;
        }

        @Override
        public void run(String... args) {
            String port = environment.getProperty("server.port", "8080");
            String contextPath = environment.getProperty("server.servlet.context-path", "");
            if (contextPath == null) contextPath = "";
            String trimmedContext = contextPath.replaceAll("^/|/$", "");
            String base = "http://127.0.0.1:" + port
                    + (trimmedContext.isEmpty() ? "" : "/" + trimmedContext);

            ServiceInfoRegistry registry = ServiceInfoRegistry.getInstance();

            if (!registry.getEndpoints().containsKey("通用/应用自身")) {
                registry.registerEndpoint("通用", "应用自身",
                        base + "/service-info", "服务信息聚合端点");
            }

            boolean hasSwagger = hasProperty("springdoc.swagger-ui.enabled")
                    || hasProperty("knife4j.enable")
                    || hasProperty("plugin.swagger.enable");
            if (!registry.getEndpoints().containsKey("文档/swagger") && hasSwagger) {
                registry.registerEndpoint("文档", "swagger",
                        base + "/doc.html", "Knife4j 接口文档");
            }
            registry.registerFlag("swagger", "文档", hasSwagger, "Knife4j 接口文档");

            boolean hasActuator = hasProperty("management.endpoints.web.exposure.include");
            if (!registry.getEndpoints().containsKey("监控/actuator") && hasActuator) {
                registry.registerEndpoint("监控", "actuator",
                        base + "/actuator", "Spring Boot Actuator");
            }
            registry.registerFlag("actuator", "监控", hasActuator, "Spring Boot Actuator");

            printBanner(registry);
        }

        private boolean hasProperty(String key) {
            return environment.getProperty(key) != null;
        }

        /**
         * 启动信息横幅: 唯一允许渲染 ANSI 转义的位置。
         */
        private void printBanner(ServiceInfoRegistry registry) {
            String sep = AnsiStyle.separator(80, '=');
            StringBuilder sb = new StringBuilder();
            sb.append('\n').append(sep).append('\n');
            sb.append(AnsiStyle.bold(AnsiStyle.yellow("  服务信息"))).append('\n');
            sb.append(sep).append('\n');

            if (!registry.getEndpoints().isEmpty()) {
                sb.append(AnsiStyle.cyan("[端点]")).append('\n');
                for (ServiceInfoRegistry.EndpointInfo ep : registry.getEndpoints().values()) {
                    sb.append("  ")
                            .append(pad(ep.getGroup() + "/" + ep.getName(), 32))
                            .append(' ').append(AnsiStyle.yellow(ep.getUrl()))
                            .append(AnsiStyle.gray("  ; " + ep.getDescription()))
                            .append('\n');
                }
            }

            if (!registry.getFlags().isEmpty()) {
                sb.append(AnsiStyle.cyan("[开关]")).append('\n');
                java.util.Map<String, java.util.List<ServiceInfoRegistry.FlagInfo>> byGroup =
                        new java.util.LinkedHashMap<>();
                for (ServiceInfoRegistry.FlagInfo flag : registry.getFlags().values()) {
                    byGroup.computeIfAbsent(flag.getGroup(), k -> new java.util.ArrayList<>()).add(flag);
                }
                for (java.util.Map.Entry<String, java.util.List<ServiceInfoRegistry.FlagInfo>> e : byGroup.entrySet()) {
                    sb.append("  ").append(AnsiStyle.bold(e.getKey())).append('\n');
                    for (ServiceInfoRegistry.FlagInfo flag : e.getValue()) {
                        sb.append("    ")
                                .append(pad(flag.getName(), 28))
                                .append(' ').append(AnsiStyle.cnLabel(flag.getValue()));
                        if (flag.getSuffix() != null && !flag.getSuffix().isEmpty()) {
                            sb.append(' ').append(AnsiStyle.cyan(flag.getSuffix()));
                        }
                        if (flag.getDescription() != null && !flag.getDescription().isEmpty()) {
                            sb.append(' ').append(AnsiStyle.gray("; " + flag.getDescription()));
                        }
                        sb.append('\n');
                    }
                }
            }

            sb.append(sep);
            System.out.println(sb);
        }

        private String pad(String s, int len) {
            if (s == null) return "";
            if (s.length() >= len) return s;
            StringBuilder sb = new StringBuilder(s);
            while (sb.length() < len) sb.append(' ');
            return sb.toString();
        }
    }
}
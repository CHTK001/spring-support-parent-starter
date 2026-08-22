package com.chua.starter.common.support.serviceinfo;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 * 服务信息自动配置 (统一入口)。
 *
 * <p>这是 spring-support-common-starter 提供的统一服务体系, 任何业务 starter
 * 都通过 {@link ServiceInfoRegistry} 注册自己的端点和开关, 启动时统一呈现。</p>
 *
 * <h3>职责</h3>
 * <ol>
 *   <li>注册基础端点 (应用自身 / Swagger / Actuator 等)</li>
 *   <li>启动完成后输出美化日志 (使用 {@link AnsiStyle} 统一颜色) -
 *       唯一的 ANSI 渲染点, 业务模块不允许自行输出 ANSI 转义</li>
 *   <li>暴露 {@code GET /service-info} REST 端点 (可选认证)</li>
 * </ol>
 *
 * <h3>运行时序保证</h3>
 * <ul>
 *   <li>基础端点检测在 {@link org.springframework.boot.context.event.ApplicationReadyEvent}
 *       阶段执行; 此时所有 starter 的 {@code @Bean ServiceInfoContributor} 都已注册。</li>
 *   <li>横幅渲染通过 {@link ServiceInfoBannerListener} 在 ready/failed 事件中触发,
 *       比业务 {@code CommandLineRunner} 更晚 (即使它们没有显式 {@code @Order})。</li>
 * </ul>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@AutoConfiguration
@ConditionalOnWebApplication
@ConditionalOnProperty(name = "plugin.service-info.enable", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ServiceInfoProperties.class)
public class ServiceInfoAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    /** ServiceInfoController */
    public ServiceInfoController serviceInfoController() {
        return new ServiceInfoController();
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    /** ServiceInfoEndpointDetector */
    public ServiceInfoEndpointDetector serviceInfoEndpointDetector(Environment environment) {
        return new ServiceInfoEndpointDetector(environment);
    }

    @Bean
    /**
     * ServiceInfoBannerListener
     * @param detector detector
     * @param properties properties
     * @param properties properties
     * @param properties properties
     * @param 静态方法 静态方法
     * @param registry registry
     * @param 32 32
     * @param 28 28
     * @param s s
     * @param s s
     * @param len len
     * @param len len
     */
    public ServiceInfoBannerListener serviceInfoBannerListener(
            ServiceInfoEndpointDetector detector, ServiceInfoProperties properties) {
        return new ServiceInfoBannerListener(detector, properties);
    }

    /**
     * 注册 ServiceInfo 认证过滤器 (可选)。
     *
     * <p>当 {@code plugin.service-info.auth.enable=true} 时注册, 否则不创建。</p>
     */
    @Bean
    @ConditionalOnProperty(name = "plugin.service-info.auth.enable", havingValue = "true")
    @ConditionalOnMissingBean(name = "serviceInfoAuthenticationFilterRegistration")
    public FilterRegistrationBean<ServiceInfoAuthenticationFilter> serviceInfoAuthenticationFilterRegistration(
            ServiceInfoProperties properties) {
        FilterRegistrationBean<ServiceInfoAuthenticationFilter> registration =
                new FilterRegistrationBean<>();
        registration.setFilter(new ServiceInfoAuthenticationFilter(properties));
        registration.addUrlPatterns("/service-info", "/service-info/*");
        registration.setName("serviceInfoAuthenticationFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setAsyncSupported(true);
        return registration;
    }

    /**
     * REST 端点: 返回所有注册到 {@link ServiceInfoRegistry} 的端点和开关信息。
     *
     * <p>注意: 此端点暴露应用内部信息 (包括路径、开关状态), 默认应仅内网访问;
     * 公网访问请通过 {@code plugin.service-info.auth.enable=true} 启用认证。</p>
     */
    @RestController
    @RequestMapping("/service-info")
    public static class ServiceInfoController {
        @GetMapping
        /** Info */
        public Map<String, Object> info() {
            return ServiceInfoRegistry.getInstance().getInfo();
        }
    }

    /**
     * 启动信息横幅: 唯一允许渲染 ANSI 转义的位置。
     *
     * <p>由 {@link ServiceInfoBannerListener} 调用, package-private 静态方法,
     * 避免业务 starter 误用。</p>
     */
    static void printBanner(ServiceInfoRegistry registry) {
        if (registry.getEndpoints().isEmpty() && registry.getFlags().isEmpty()) {
            return;
        }
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
                        .append(AnsiStyle.gray("  ; " + nullToEmpty(ep.getDescription())))
                        .append('\n');
            }
        }

        if (!registry.getFlags().isEmpty()) {
            sb.append(AnsiStyle.cyan("[开关]")).append('\n');
            java.util.Map<String, java.util.List<ServiceInfoRegistry.FlagInfo>> byGroup =
                    new java.util.LinkedHashMap<>();
            for (ServiceInfoRegistry.FlagInfo flag : registry.getFlags().values()) {
                byGroup.computeIfAbsent(nullToEmpty(flag.getGroup()), k -> new java.util.ArrayList<>()).add(flag);
            }
            for (java.util.Map.Entry<String, java.util.List<ServiceInfoRegistry.FlagInfo>> e : byGroup.entrySet()) {
                sb.append("  ").append(AnsiStyle.bold(e.getKey())).append('\n');
                for (ServiceInfoRegistry.FlagInfo flag : e.getValue()) {
                    String label = flag.getDescription() != null && !flag.getDescription().isEmpty()
                            ? flag.getDescription()
                            : flag.getName();
                    sb.append("    ")
                            .append(pad(label, 28))
                            .append(' ').append(AnsiStyle.cnLabel(flag.getValue()));
                    if (flag.getSuffix() != null && !flag.getSuffix().isEmpty()) {
                        sb.append(' ').append(AnsiStyle.cyan(flag.getSuffix()));
                    }
                    sb.append('\n');
                }
            }
        }

        sb.append(sep);
        log.info("{}", sb);
    }

    /** NullToEmpty */
    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** Pad */
    private static String pad(String s, int len) {
        if (s == null) s = "";
        if (s.length() >= len) return s;
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < len) sb.append(' ');
        return sb.toString();
    }
}

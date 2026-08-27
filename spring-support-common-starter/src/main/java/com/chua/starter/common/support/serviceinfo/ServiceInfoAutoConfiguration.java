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
import lombok.extern.slf4j.Slf4j;
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
        String c = AnsiStyle.CYAN;
        String r = AnsiStyle.RESET;
        String b = AnsiStyle.BOLD;
        String y = AnsiStyle.YELLOW;
        String gr = AnsiStyle.GRAY;
        int W = 70;

        StringBuilder sb = new StringBuilder('\n');
        sb.append(c).append("╔").append(repeat('═', W)).append("╗").append(r).append('\n');
        sb.append(c).append("║").append(r).append(ansiPad("  " + b + y + "服务信息", W - 2)).append(c).append("║").append(r).append('\n');
        sb.append(c).append("╠").append(repeat('═', W)).append("╣").append(r).append('\n');

        if (!registry.getEndpoints().isEmpty()) {
            sb.append(c).append("╭").append(repeat('─', W)).append("╮").append(r).append('\n');
            sb.append(c).append("║").append(r).append(ansiPad("  [端点]", W - 2)).append(c).append("║").append(r).append('\n');
            sb.append(c).append("╰").append(repeat('─', W)).append("╯").append(r).append('\n');
            for (ServiceInfoRegistry.EndpointInfo ep : registry.getEndpoints().values()) {
                String label = nullToEmpty(ep.getGroup()) + "/" + ep.getName();
                String url = ep.getUrl();
                String desc = nullToEmpty(ep.getDescription());
                int labelLen = ansiWidth(label);
                int urlLen = ansiWidth(url);
                int descLen = ansiWidth(desc);
                int inner = W - 4;
                int pad1 = Math.max(2, inner - labelLen - urlLen - descLen - 2);
                sb.append(c).append("║").append(r)
                        .append("    ").append(label).append(r)
                        .append(ansiPad("", pad1)).append(c)
                        .append(url).append(r)
                        .append("  ").append(gr).append("; ").append(desc).append(r)
                        .append(c).append("║").append(r).append('\n');
            }
        }

        if (!registry.getFlags().isEmpty()) {
            java.util.Map<String, java.util.List<ServiceInfoRegistry.FlagInfo>> byGroup =
                    new java.util.LinkedHashMap<>();
            for (ServiceInfoRegistry.FlagInfo flag : registry.getFlags().values()) {
                byGroup.computeIfAbsent(nullToEmpty(flag.getGroup()), k -> new java.util.ArrayList<>()).add(flag);
            }
            for (java.util.Map.Entry<String, java.util.List<ServiceInfoRegistry.FlagInfo>> e : byGroup.entrySet()) {
                sb.append(c).append("╭").append(repeat('─', W)).append("╮").append(r).append('\n');
                sb.append(c).append("║").append(r).append(ansiPad("  " + b + e.getKey(), W - 2)).append(c).append("║").append(r).append('\n');
                sb.append(c).append("╰").append(repeat('─', W)).append("╯").append(r).append('\n');
                for (ServiceInfoRegistry.FlagInfo flag : e.getValue()) {
                    String label = flag.getDescription() != null && !flag.getDescription().isEmpty()
                            ? flag.getDescription()
                            : flag.getName();
                    String suffix = (flag.getSuffix() != null && !flag.getSuffix().isEmpty())
                            ? " " + c + flag.getSuffix() + r : "";
                    int labelLen = ansiWidth(label);
                    int sufLen = suffix.isEmpty() ? 0 : ansiWidth(suffix);
                    int inner = W - 4;
                    int pad1 = Math.max(2, inner - labelLen - sufLen);
                    sb.append(c).append("║").append(r)
                            .append("      ").append(label).append(r)
                            .append(ansiPad("", pad1)).append(c)
                            .append(AnsiStyle.cnLabel(flag.getValue())).append(r)
                            .append(suffix).append(c).append("║").append(r).append('\n');
                }
            }
        }

        sb.append(c).append("╚").append(repeat('═', W)).append("╝").append(r);
        log.info("{}", sb);
    }

    /** Repeat */
    private static String repeat(char ch, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) sb.append(ch);
        return sb.toString();
    }

    /** AnsiWidth 计算去掉 ANSI 转义后的可见字符长度 */
    private static int ansiWidth(String s) {
        if (s == null) return 0;
        int width = 0;
        for (int i = 0; i < s.length(); ) {
            if (s.charAt(i) == '\u001B' && i + 2 < s.length() && s.charAt(i + 1) == '[') {
                int j = i + 2;
                while (j < s.length() && Character.isLetterOrDigit(s.charAt(j))) j++;
                i = j;
            } else {
                width++;
                i++;
            }
        }
        return width;
    }

    /** AnsiPad 在忽略 ANSI 颜色的情况下填充空格使输出宽度恰好为 target */
    private static String ansiPad(String s, int target) {
        if (target <= 0) return "";
        int w = ansiWidth(s);
        int need = Math.max(0, target - w);
        StringBuilder sb = new StringBuilder(s);
        for (int i = 0; i < need; i++) sb.append(' ');
        return sb.toString();
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

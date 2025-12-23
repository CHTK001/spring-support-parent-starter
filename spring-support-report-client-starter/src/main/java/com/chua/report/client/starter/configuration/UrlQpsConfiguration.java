package com.chua.report.client.starter.configuration;

import com.chua.report.client.starter.interceptor.UrlQpsInterceptor;
import com.chua.report.client.starter.properties.ReportProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * URL QPS 统计配置
 * <p>
 * 配置 URL QPS 拦截器
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025/01/17
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = ReportProperties.PRE + ".url-qps", name = "enabled", havingValue = "true", matchIfMissing = false)
public class UrlQpsConfiguration implements WebMvcConfigurer {

    private final ReportProperties reportProperties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        ReportProperties.UrlQps urlQpsConfig = reportProperties.getUrlQps();
        Set<String> excludePatterns = urlQpsConfig.getExcludePatterns();

        // 转换排除模式为 Ant 风格
        List<String> excludePaths = new ArrayList<>();
        for (String pattern : excludePatterns) {
            if (!pattern.endsWith("/**")) {
                excludePaths.add(pattern + "/**");
            }
            excludePaths.add(pattern);
        }

        registry.addInterceptor(new UrlQpsInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(excludePaths);

        log.info("[UrlQpsConfiguration] 已注册 URL QPS 拦截器，排除路径: {}", excludePaths);
    }
}

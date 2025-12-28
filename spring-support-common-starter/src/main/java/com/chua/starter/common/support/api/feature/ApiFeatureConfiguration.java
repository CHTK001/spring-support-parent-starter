package com.chua.starter.common.support.api.feature;

import com.chua.starter.common.support.api.annotations.ApiFeature;
import com.chua.starter.common.support.api.interceptor.ApiControlInterceptor;
import com.chua.starter.common.support.api.properties.ApiProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.Environment;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Map;

/**
 * API 功能开关配置
 * <p>
 * 自动扫描并注册所有 @ApiFeature 注解标记的功能开关。
 * </p>
 *
 * @author CH
 * @since 2024/12/08
 * @version 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "plugin.api.feature.enable", havingValue = "true", matchIfMissing = true)
public class ApiFeatureConfiguration implements WebMvcConfigurer {

    private final ApiProperties apiProperties;
    private final Environment environment;
    private final RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Bean
    public ApiFeatureManager apiFeatureManager() {
        return new ApiFeatureManager();
    }

    @Bean
    public ApiControlInterceptor apiControlInterceptor(ApiFeatureManager featureManager) {
        return new ApiControlInterceptor(apiProperties, environment, featureManager);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiControlInterceptor(apiFeatureManager()))
                .addPathPatterns("/**")
                .excludePathPatterns("/error", "/actuator/**");
    }

    /**
     * 启动时扫描所有 @ApiFeature 注解
     */
    @PostConstruct
    public void scanApiFeatures() {
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = requestMappingHandlerMapping.getHandlerMethods();
        var featureManager = apiFeatureManager();

        int count = 0;
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
            HandlerMethod handlerMethod = entry.getValue();
            RequestMappingInfo mappingInfo = entry.getKey();

            // 方法级别注解
            ApiFeature apiFeature = AnnotationUtils.findAnnotation(handlerMethod.getMethod(), ApiFeature.class);
            if (apiFeature == null) {
                // 类级别注解
                apiFeature = AnnotationUtils.findAnnotation(handlerMethod.getBeanType(), ApiFeature.class);
            }

            if (apiFeature != null) {
                featureManager.registerFeature(apiFeature.value(), apiFeature, handlerMethod, mappingInfo);
                count++;
            }
        }

        if (count > 0) {
            log.info("╔══════════════════════════════════════════════════════════════════════════════");
            log.info("║ [功能开关]已扫描注册 {} 个 @ApiFeature 功能开关", count);
            log.info("╚══════════════════════════════════════════════════════════════════════════════");
        }
    }
}

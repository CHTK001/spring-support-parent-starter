package com.chua.starter.common.support.api.feature;

import com.chua.starter.common.support.api.annotations.ApiFeature;
import com.chua.starter.common.support.api.interceptor.ApiControlInterceptor;
import com.chua.starter.common.support.api.properties.ApiProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.SmartInitializingSingleton;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.Environment;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Map;

/**
 * API 功能开关自动配置。
 *
 * <p>扫描 Spring MVC 控制器中所有 {@link ApiFeature} 注解标记的方法/类，
 * 注册到 {@link ApiFeatureManager} 中统一管理；同时注册
 * {@link ApiControlInterceptor} 拦截器，对未启用的功能开关返回 403。</p>
 *
 * <h2>启用条件</h2>
 * <pre>
 *   # application.yml
 *   plugin:
 *     api:
 *       feature:
 *         enable: true   # 默认 true
 * </pre>
 *
 * @since 2024/12/08
 * @version 1.0.0
 * @author CH
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "plugin.api.feature.enable", havingValue = "true", matchIfMissing = true)
public class ApiFeatureConfiguration implements WebMvcConfigurer, SmartInitializingSingleton {

    /**
     * 全路径拦截模式：拦截所有 HTTP 请求
     */
    private static final String PATH_PATTERN_ALL = "/**";

    /**
     * 排除路径：Spring Boot 默认错误页
     */
    private static final String PATH_PATTERN_ERROR = "/error";

    /**
     * 排除路径：Spring Boot Actuator 全部端点
     */
    private static final String PATH_PATTERN_ACTUATOR = "/actuator/**";

    /**
     * 日志横幅：上边框
     */
    private static final String LOG_BANNER_TOP =
            "╔══════════════════════════════════════════════════════════════════════════════";

    /**
     * 日志横幅：下边框
     */
    private static final String LOG_BANNER_BOTTOM =
            "╚══════════════════════════════════════════════════════════════════════════════";

    /**
     * 日志横幅：中间内容模板（带一个占位符用于注册数量）
     */
    private static final String LOG_BANNER_MIDDLE_TEMPLATE = "║ [功能开关]已扫描注册 {} 个 @ApiFeature 功能开关";

    /**
     * API 配置属性
     */
    private final ApiProperties apiProperties;

    /**
     * Spring 环境对象，用于读取运行时配置
     */
    private final Environment environment;

    /**
     * Spring MVC 请求映射处理器，用于枚举所有 Controller 方法
     */
    private final RequestMappingHandlerMapping requestMappingHandlerMapping;

    /**
     * 构造器：注入 API 配置、Spring 环境与请求映射处理器。
     *
     * @param apiProperties                 API 配置属性
     * @param environment                   Spring 环境对象
     * @param requestMappingHandlerMapping  Spring MVC 请求映射处理器（@Lazy 打破
     *                                      WebMvcConfigurer 与自定义版本映射 Bean 的装配环）
     */
    public ApiFeatureConfiguration(ApiProperties apiProperties,
                                   Environment environment,
                                   @Lazy RequestMappingHandlerMapping requestMappingHandlerMapping) {
        this.apiProperties = apiProperties;
        this.environment = environment;
        this.requestMappingHandlerMapping = requestMappingHandlerMapping;
    }

    /**
     * 创建 {@link ApiFeatureManager} Bean，统一管理所有 @ApiFeature 功能开关。
     *
     * @return 功能开关管理器实例
     */
    @Bean
    public ApiFeatureManager apiFeatureManager() {
        return new ApiFeatureManager();
    }

    @Autowired
    private ObjectProvider<ApiSwitchOverrideProvider> overrideProvider;

    /**
     * 创建 {@link ApiControlInterceptor} Bean，用于拦截未启用的功能开关。
     *
     * @param featureManager 功能开关管理器
     * @return API 控制拦截器实例
     */
    @Bean
    public ApiControlInterceptor apiControlInterceptor(ApiFeatureManager featureManager) {
        return new ApiControlInterceptor(apiProperties, environment, featureManager, overrideProvider);
    }

    /**
     * 注册 API 控制拦截器，覆盖全部路径，排除错误页与 Actuator 端点。
     *
     * @param registry Spring MVC 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiControlInterceptor(apiFeatureManager()))
                .addPathPatterns(PATH_PATTERN_ALL)
                .excludePathPatterns(PATH_PATTERN_ERROR, PATH_PATTERN_ACTUATOR);
    }

    /**
     * 所有单例创建完成后扫描 Spring MVC 中所有 {@link ApiFeature} 注解标记的方法或类，
     * 注册到 {@link ApiFeatureManager} 中。
     *
     * <p>不能在 {@code @PostConstruct} 中执行：创建期解析 {@code RequestMappingHandlerMapping}
     * 会触发 {@code WebMvcConfigurer} 集合解析，与正在创建的自身形成循环引用
     * （@Lazy 仅在构造期生效，init 期使用仍会触发）。</p>
     *
     * <p>查找顺序：先方法级注解，再类级注解。同一功能开关 value 重复注册时，
     * 后注册的会覆盖前者。</p>
     */
    @Override
    public void afterSingletonsInstantiated() {
        scanApiFeatures();
    }

    private void scanApiFeatures() {
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = requestMappingHandlerMapping.getHandlerMethods();
        ApiFeatureManager featureManager = apiFeatureManager();

        int count = 0;
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
            HandlerMethod handlerMethod = entry.getValue();
            RequestMappingInfo mappingInfo = entry.getKey();

            // 先尝试方法级别注解，未命中再回退到类级别注解
            ApiFeature apiFeature = AnnotationUtils.findAnnotation(handlerMethod.getMethod(), ApiFeature.class);
            if (apiFeature == null) {
                apiFeature = AnnotationUtils.findAnnotation(handlerMethod.getBeanType(), ApiFeature.class);
            }

            if (apiFeature != null) {
                featureManager.registerFeature(apiFeature.value(), apiFeature, handlerMethod, mappingInfo);
                count++;
            }
        }

        if (count > 0) {
            log.info(LOG_BANNER_TOP);
            log.info(LOG_BANNER_MIDDLE_TEMPLATE, count);
            log.info(LOG_BANNER_BOTTOM);
        }
    }
}

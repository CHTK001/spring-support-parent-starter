package com.chua.starter.common.support.api.configuration;
import com.chua.common.support.lang.version.Version;
import com.chua.starter.common.support.api.control.ApiVersionRequestMappingHandlerMapping;
import com.chua.starter.common.support.api.decode.ApiRequestDecodeBodyAdvice;
import com.chua.starter.common.support.api.decode.ApiRequestDecodeRegister;
import com.chua.starter.common.support.api.encode.ApiResponseEncodeRegister;
import com.chua.starter.common.support.api.encode.ApiResponseEncodeResponseBodyAdvice;
import com.chua.starter.common.support.api.properties.ApiProperties;
import com.chua.starter.common.support.api.response.ApiExceptionAdvice;
import com.chua.starter.common.support.api.response.ApiUniformResponseBodyAdvice;
import com.chua.common.support.application.GlobalSettingFactory;
import com.chua.starter.common.support.application.ModuleEnvironmentRegistration;
import com.chua.starter.common.support.serviceinfo.ServiceInfoRegistry;
import jakarta.annotation.Priority;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * API 统一配置
 * <p>
 * 整合版本控制、平台标识、编解码等 API 相关配置。
 * </p>
 * <p>
 * <b>注意：</b> 此配置类已迁移至 {@code utils-support-springboot-starter} 模块
 * （包路径：{@code com.chua.springboot.support.api}）。
 * 旧路径保留用于向后兼容，新项目请使用 {@code utils-support-springboot-starter} 中的实现。
 * </p>
 *
 * @author CH
 * @since 2024/12/07
 * @version 1.0.0
 * @deprecated 请使用 {@code com.chua.springboot.support.api.configuration.ApiConfiguration}
 */
@EnableConfigurationProperties(ApiProperties.class)
@Priority(0)
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ApiConfiguration implements EnvironmentAware  {
    private static final Logger log = LoggerFactory.getLogger(ApiConfiguration.class);

    private ApiProperties apiProperties;
    private Environment environment;

    // ==================== 版本控制配置 ====================

    private void logControlStatus() {
        ServiceInfoRegistry registry = ServiceInfoRegistry.getInstance();

        ApiProperties.Version version = apiProperties.getVersion();
        registry.registerFlag("api-version", "API控制",
                version != null && version.isEnable(),
                "@ApiVersion 版本控制", null);

        ApiProperties.Platform platform = apiProperties.getPlatform();
        String platformSuffix = platform != null ? "[平台: " + platform.getPlatformName() + "]" : null;
        registry.registerFlag("api-platform", "API控制",
                platform != null && platform.isEnable(),
                "@ApiPlatform 平台控制", platformSuffix);

        registry.registerFlag("api-profile", "API控制", true,
                "@ApiProfile 环境控制", null);

        ApiProperties.DeprecatedConfig deprecated = apiProperties.getDeprecated();
        registry.registerFlag("api-deprecated", "API控制",
                deprecated != null && deprecated.isEnable(),
                "@ApiDeprecated 废弃提示", null);

        ApiProperties.FeatureConfig feature = apiProperties.getFeature();
        registry.registerFlag("api-feature", "API控制",
                feature != null && feature.isEnable(),
                "@ApiFeature 功能开关", null);

        ApiProperties.InternalConfig internal = apiProperties.getInternal();
        registry.registerFlag("api-internal", "API控制",
                internal != null && internal.isEnable(),
                "@ApiInternal 内部接口", null);

        ApiProperties.MockConfig mock = apiProperties.getMock();
        String mockSuffix = (mock != null && mock.isEnable()) ? "[环境: " + mock.getProfiles() + "]" : null;
        registry.registerFlag("api-mock", "API控制",
                mock != null && mock.isEnable(),
                "@ApiMock Mock模式", mockSuffix);

        ApiProperties.GrayConfig gray = apiProperties.getGray();
        registry.registerFlag("api-gray", "API控制",
                gray != null && gray.isEnable(),
                "@ApiGray 灰度发布", null);
    }

    // ==================== 响应编码配置 ====================

    /**
     * 响应编码处理
     *
     * @param apiResponseEncodeRegister 编码注册器
     * @return 响应编码处理器
     */
    @Bean("apiResponseEncodeResponseBodyAdvice")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "plugin.api.encode.enable", havingValue = "true", matchIfMissing = true)
    public ApiResponseEncodeResponseBodyAdvice responseEncodeResponseBodyAdvice(@Lazy ApiResponseEncodeRegister apiResponseEncodeRegister) {
        return new ApiResponseEncodeResponseBodyAdvice(apiResponseEncodeRegister);
    }

    /**
     * 响应编码注册器
     *
     * @return 编码注册器
     */
    @Bean("apiResponseEncodeRegister")
    @Lazy
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "plugin.api.encode.enable", havingValue = "true", matchIfMissing = true)
    public ApiResponseEncodeRegister apiResponseEncodeRegister() {
        return new ApiResponseEncodeRegister(apiProperties.getEncode());
    }

    // ==================== 请求解码配置 ====================

    /**
     * 请求解码处理
     *
     * @param apiRequestDecodeRegister 解码注册器
     * @return 请求解码处理器
     */
    @Bean("apiRequestDecodeBodyAdvice")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "plugin.api.decode.enable", havingValue = "true", matchIfMissing = true)
    public ApiRequestDecodeBodyAdvice apiRequestDecodeBodyAdvice(ApiRequestDecodeRegister apiRequestDecodeRegister) {
        return new ApiRequestDecodeBodyAdvice(apiRequestDecodeRegister);
    }

    /**
     * 请求解码注册器
     *
     * @return 解码注册器
     */
    @Bean("apiRequestDecodeRegister")
    @Lazy
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "plugin.api.decode.enable", havingValue = "true", matchIfMissing = true)
    public ApiRequestDecodeRegister apiRequestDecodeRegister() {
        return new ApiRequestDecodeRegister(apiProperties.getDecode());
    }


    /**
     * 创建统一响应体建议实例。
     * 当容器中没有提供UniformResponseBodyAdvice实例且plugin.parameter.enable为true时，使用此实现。
     *
     * @return {@link ApiUniformResponseBodyAdvice} 统一响应体建议实例
     * @example
     * <pre>
     * // 使用示例
     * &#64;Autowired
     * private UniformResponseBodyAdvice uniformResponseBodyAdvice;
     * </pre>
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "plugin.api.uniform", havingValue = "true", matchIfMissing = true)
    public ApiUniformResponseBodyAdvice uniformResponseBodyAdvice() {
        return new ApiUniformResponseBodyAdvice();
    }
    /**
     * 创建执行器服务实例。
     * 用于处理异步任务的线程池。
     *
     * @return {@link ExecutorService} 执行器服务实例
     * @example
     * <pre>
     * // 使用示例
     * &#64;Autowired
     * &#64;Qualifier("uniform")
     * private ExecutorService executorService;
     * </pre>
     */
    @Bean("uniform")
    public ExecutorService executor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("uniform-pool").factory());
    }

    // ==================== 异常处理配置 ====================

    /**
     * 统一异常处理
     *
     * @return 异常处理器
     */
    @Bean
    @ConditionalOnMissingBean
    public ApiExceptionAdvice apiExceptionAdvice() {
        return new ApiExceptionAdvice();
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
        this.apiProperties = Binder.get(environment).bindOrCreate(ApiProperties.PRE, ApiProperties.class);
        try {
            GlobalSettingFactory.PREFIX = apiProperties.getPlatform().getPlatformName();
        } catch (Exception ignored) {
        }
        new ModuleEnvironmentRegistration(ApiProperties.PRE, apiProperties, true);
    }
}


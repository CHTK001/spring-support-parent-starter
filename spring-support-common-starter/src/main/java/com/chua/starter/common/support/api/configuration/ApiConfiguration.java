package com.chua.starter.common.support.api.configuration;
import com.chua.starter.common.support.api.control.ApiVersionRequestMappingHandlerMapping;
import com.chua.starter.common.support.api.decode.ApiRequestDecodeBodyAdvice;
import com.chua.starter.common.support.api.decode.ApiRequestDecodeRegister;
import com.chua.starter.common.support.api.encode.ApiResponseEncodeRegister;
import com.chua.starter.common.support.api.encode.ApiResponseEncodeResponseBodyAdvice;
import com.chua.starter.common.support.api.properties.ApiProperties;
import com.chua.starter.common.support.api.response.ApiExceptionAdvice;
import com.chua.starter.common.support.api.response.ApiUniformResponseBodyAdvice;
import com.chua.starter.common.support.application.GlobalSettingFactory;
import com.chua.starter.common.support.application.ModuleEnvironmentRegistration;
import jakarta.annotation.Priority;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lombok.extern.slf4j.Slf4j;

/**
 * API 统一配置
 * <p>
 * 整合版本控制、平台标识、编解码等 API 相关配置。
 * </p>
 *
 * @author CH
 * @since 2024/12/07
 * @version 1.0.0
 */
@Slf4j
@EnableConfigurationProperties(ApiProperties.class)
@Priority(0)
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ApiConfiguration implements WebMvcRegistrations, EnvironmentAware {
        private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_RESET = "\u001B[0m";

    private ApiProperties apiProperties;
    private Environment environment;

    // ==================== 版本控制配置 ====================

    @Override
    public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
        return requestMappingInfoHandlerMapping();
    }

    @Bean
    @ConditionalOnMissingBean
    public RequestMappingHandlerMapping requestMappingInfoHandlerMapping() {
        if (apiProperties.isControlEnabled()) {
            log.info("[API控制] [注册开始]");
            
            // 版本控制
            ApiProperties.Version version = apiProperties.getVersion();
            if (version != null && version.isEnable()) {
                log.info("[API控制] [@ApiVersion] [版本控制] [{}开启{}]", ANSI_GREEN, ANSI_RESET);
            } else {
                log.info("[API控制] [@ApiVersion] [版本控制] [{}关闭{}]", ANSI_RED, ANSI_RESET);
            }
            
            // 平台控制
            ApiProperties.Platform platform = apiProperties.getPlatform();
            if (platform != null && platform.isEnable()) {
                log.info("[API控制] [@ApiPlatform] [平台控制] [{}开启{}] [平台: {}]", ANSI_GREEN, ANSI_RESET, platform.getPlatformName());
            } else {
                log.info("[API控制] [@ApiPlatform] [平台控制] [{}关闭{}]", ANSI_RED, ANSI_RESET);
            }
            
            log.info("[API控制] [@ApiProfile] [环境控制] [{}开启{}]", ANSI_GREEN, ANSI_RESET);
            
            // 废弃接口控制
            ApiProperties.DeprecatedConfig deprecated = apiProperties.getDeprecated();
            if (deprecated != null && deprecated.isEnable()) {
                log.info("[API控制] [@ApiDeprecated] [废弃提示] [{}开启{}]", ANSI_GREEN, ANSI_RESET);
            } else {
                log.info("[API控制] [@ApiDeprecated] [废弃提示] [{}关闭{}]", ANSI_RED, ANSI_RESET);
            }
            
            // 功能开关控制
            ApiProperties.FeatureConfig feature = apiProperties.getFeature();
            if (feature != null && feature.isEnable()) {
                log.info("[API控制] [@ApiFeature] [功能开关] [{}开启{}]", ANSI_GREEN, ANSI_RESET);
            } else {
                log.info("[API控制] [@ApiFeature] [功能开关] [{}关闭{}]", ANSI_RED, ANSI_RESET);
            }
            
            // 内部接口控制
            ApiProperties.InternalConfig internal = apiProperties.getInternal();
            if (internal != null && internal.isEnable()) {
                log.info("[API控制] [@ApiInternal] [内部接口] [{}开启{}]", ANSI_GREEN, ANSI_RESET);
            } else {
                log.info("[API控制] [@ApiInternal] [内部接口] [{}关闭{}]", ANSI_RED, ANSI_RESET);
            }
            
            // Mock 控制
            ApiProperties.MockConfig mock = apiProperties.getMock();
            if (mock != null && mock.isEnable()) {
                log.info("[API控制] [@ApiMock] [Mock模式] [{}开启{}] [环境: {}]", ANSI_GREEN, ANSI_RESET, mock.getProfiles());
            } else {
                log.info("[API控制] [@ApiMock] [Mock模式] [{}关闭{}]", ANSI_RED, ANSI_RESET);
            }
            
            // 灰度发布控制
            ApiProperties.GrayConfig gray = apiProperties.getGray();
            if (gray != null && gray.isEnable()) {
                log.info("[API控制] [@ApiGray] [灰度发布] [{}开启{}]", ANSI_GREEN, ANSI_RESET);
            } else {
                log.info("[API控制] [@ApiGray] [灰度发布] [{}关闭{}]", ANSI_RED, ANSI_RESET);
            }
            
            log.info("[API控制] [注册完成]");
            return new ApiVersionRequestMappingHandlerMapping(apiProperties, environment);
        }
        log.debug("[API控制] 未开启版本/平台控制，使用默认 RequestMappingHandlerMapping");
        return new RequestMappingHandlerMapping();
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
    @ConditionalOnBean(ApiResponseEncodeRegister.class)
    @ConditionalOnProperty(name = "plugin.api.encode.enable", havingValue = "true", matchIfMissing = true)
    public ApiResponseEncodeResponseBodyAdvice responseEncodeResponseBodyAdvice(ApiResponseEncodeRegister apiResponseEncodeRegister) {
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
    @ConditionalOnBean(ApiRequestDecodeRegister.class)
    @ConditionalOnProperty(name = "plugin.api.decode.enable", havingValue = "true", matchIfMissing = true)
    public ApiRequestDecodeBodyAdvice apiRequestDecodeBodyAdvice(ApiRequestDecodeRegister apiRequestDecodeRegister) {
        ApiRequestDecodeBodyAdvice advice = new ApiRequestDecodeBodyAdvice(apiRequestDecodeRegister);
        ApiProperties.RequestDecodeProperties decodeConfig = apiProperties.getDecode();
        advice.setRejectOnDecodeFailure(decodeConfig.isRejectOnDecodeFailure());
        return advice;
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


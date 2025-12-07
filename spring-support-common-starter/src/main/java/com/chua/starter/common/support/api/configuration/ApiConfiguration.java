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
import jakarta.annotation.Priority;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
            log.info(">>>>>>> 开启版本控制功能");
            log.info(">>>>>>> @ApiProfile 环境控制");
            log.info(">>>>>>> @ApiVersion 版本控制");
            return new ApiVersionRequestMappingHandlerMapping(apiProperties, environment);
        }
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
    @ConditionalOnProperty(name = "plugin.api.encode.enable", havingValue = "true")
    public ApiResponseEncodeResponseBodyAdvice responseEncodeResponseBodyAdvice(ApiResponseEncodeRegister apiResponseEncodeRegister) {
        return new ApiResponseEncodeResponseBodyAdvice(apiResponseEncodeRegister);
    }

    /**
     * 响应编码注册器
     *
     * @return 编码注册器
     */
    @Bean("apiResponseEncodeRegister")
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
    @ConditionalOnProperty(name = "plugin.api.decode.enable", havingValue = "true")
    public ApiRequestDecodeBodyAdvice apiRequestDecodeBodyAdvice(ApiRequestDecodeRegister apiRequestDecodeRegister) {
        return new ApiRequestDecodeBodyAdvice(apiRequestDecodeRegister);
    }

    /**
     * 请求解码注册器
     *
     * @return 解码注册器
     */
    @Bean("apiRequestDecodeRegister")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "plugin.api.decode.enable", havingValue = "true")
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
    @ConditionalOnProperty(name = "plugin.api.uniform", havingValue = "true", matchIfMissing = false)
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
    }
}


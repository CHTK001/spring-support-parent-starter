package com.chua.starter.common.support.api.control;

import com.chua.common.support.utils.ArrayUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.api.annotations.ApiPlatform;
import com.chua.starter.common.support.api.annotations.ApiProfile;
import com.chua.starter.common.support.api.annotations.ApiVersion;
import com.chua.starter.common.support.api.properties.ApiProperties;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.Environment;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.mvc.condition.RequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;

/**
 * API 版本控制请求映射处理器
 * <p>
 * 支持 @ApiVersion、@ApiProfile、@ApiPlatform 注解的版本和平台控制。
 * </p>
 *
 * @author CH
 * @since 2020-11-15
 */
public class ApiVersionRequestMappingHandlerMapping extends RequestMappingHandlerMapping {

    private final ApiProperties apiProperties;
    private final boolean platformOpen;
    private final boolean versionOpen;
    private final String platform;
    private final String active;

    public ApiVersionRequestMappingHandlerMapping(ApiProperties apiProperties, Environment environment) {
        this.apiProperties = apiProperties;
        this.active = environment.resolvePlaceholders("spring.profiles.active");
        this.versionOpen = apiProperties.getVersion().isEnable() ;
        this.platformOpen = apiProperties.getPlatform().isEnable() 
                && StringUtils.isNotBlank(apiProperties.getPlatform().getPlatformName());
        this.platform = apiProperties.getPlatform().getPlatformName();
    }

    /**
     * 获取类级别的版本条件
     *
     * @param handlerType 处理器类型
     * @return 版本条件
     */
    @Override
    protected RequestCondition<?> getCustomTypeCondition(Class<?> handlerType) {
        if (!versionOpen) {
            return super.getCustomTypeCondition(handlerType);
        }
        return new ApiVersionCondition(AnnotationUtils.findAnnotation(handlerType, ApiVersion.class));
    }

    /**
     * 获取方法的映射信息
     *
     * @param method      方法
     * @param handlerType 处理器类型
     * @return 映射信息
     */
    @Override
    @Nullable
    protected RequestMappingInfo getMappingForMethod(Method method, Class<?> handlerType) {
        if (!platformOpen) {
            return super.getMappingForMethod(method, handlerType);
        }

        if (StringUtils.isNotBlank(active)) {
            if (!isMatchProfile(method, handlerType)) {
                return null;
            }
        }

        ApiPlatform apiPlatform = AnnotationUtils.findAnnotation(method, ApiPlatform.class);
        if (null == apiPlatform) {
            apiPlatform = AnnotationUtils.findAnnotation(handlerType, ApiPlatform.class);
        }

        if (apiPlatform == null || ArrayUtils.isEmpty(apiPlatform.value())) {
            return super.getMappingForMethod(method, handlerType);
        }

        if (ArrayUtils.containsIgnoreCase(apiPlatform.value(), platform)) {
            return super.getMappingForMethod(method, handlerType);
        }

        return null;
    }

    /**
     * 判断是否匹配环境
     *
     * @param method      方法
     * @param handlerType 处理器类型
     * @return 是否匹配
     */
    private boolean isMatchProfile(Method method, Class<?> handlerType) {
        ApiProfile apiProfile = AnnotationUtils.findAnnotation(method, ApiProfile.class);
        if (null == apiProfile) {
            apiProfile = AnnotationUtils.findAnnotation(handlerType, ApiProfile.class);
        }

        if (null != apiProfile) {
            // 检查当前激活的环境是否在注解配置的环境列表中
            return ArrayUtils.containsIgnoreCase(apiProfile.value(), active);
        }

        return true;
    }

    /**
     * 获取方法级别的版本条件
     *
     * @param method 方法
     * @return 版本条件
     */
    @Override
    protected RequestCondition<?> getCustomMethodCondition(Method method) {
        if (!versionOpen) {
            return super.getCustomMethodCondition(method);
        }
        return new ApiVersionCondition(AnnotationUtils.findAnnotation(method, ApiVersion.class));
    }
}


package com.chua.starter.common.support.control;

import com.chua.common.support.utils.ArrayUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.annotations.ApiPlatform;
import com.chua.starter.common.support.annotations.ApiProfile;
import com.chua.starter.common.support.annotations.ApiVersion;
import com.chua.starter.common.support.properties.ControlProperties;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.Environment;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.mvc.condition.RequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;

/**
 * API version setting 配置
 *
 * @author w
 * @since 2020-11-15
 */
public class ApiVersionRequestMappingHandlerMapping extends RequestMappingHandlerMapping {

    private final ControlProperties controlProperties;
    private final boolean platformOpen;
    private final boolean versionOpen;
    private final String platform;
    private final String active;

    public ApiVersionRequestMappingHandlerMapping(ControlProperties controlProperties, Environment environment) {
        this.controlProperties = controlProperties;
        this.active = environment.resolvePlaceholders("spring.profiles.active");
        this.versionOpen = controlProperties.getVersion().isEnable() && StringUtils.isNotBlank(controlProperties.getVersion().getName());
        this.platformOpen = controlProperties.getPlatform().isEnable() && StringUtils.isNotBlank(controlProperties.getPlatform().getName());
        this.platform = controlProperties.getPlatform().getName();
    }

    /**
     * class condition
     * - 在class上加@ApiVersion注解&url加{version}
     *
     * @param handlerType class type
     * @return ApiVersionCondition
     */
    @Override
    protected RequestCondition<?> getCustomTypeCondition(Class<?> handlerType) {
        if(!versionOpen) {
            return super.getCustomTypeCondition(handlerType);
        }
        return new ApiVersionCondition(AnnotationUtils.findAnnotation(handlerType, ApiVersion.class));
    }
    @Override
    @Nullable
    protected RequestMappingInfo getMappingForMethod(Method method, Class<?> handlerType) {
        if(!platformOpen) {
            return super.getMappingForMethod(method, handlerType);
        }


        if(StringUtils.isNotBlank(active)) {
            if(!isMatchProfile(method, handlerType)) {
                return null;
            }
        }

        ApiPlatform apiPlatform = AnnotationUtils.findAnnotation(method, ApiPlatform.class);
        if(null == apiPlatform) {
            apiPlatform = AnnotationUtils.findAnnotation(handlerType, ApiPlatform.class);
        }

        if(apiPlatform == null || ArrayUtils.isEmpty(apiPlatform.value())) {
            return super.getMappingForMethod(method, handlerType);
        }

        if(ArrayUtils.containsIgnoreCase(apiPlatform.value(), platform)) {
            return super.getMappingForMethod(method, handlerType);
        }

        return null;
    }

    /**
     * 判断是否匹配环境
     *
     * @param method      method object
     * @param handlerType class type
     * @return boolean
     */
    private boolean isMatchProfile(Method method, Class<?> handlerType) {
        ApiProfile apiProfile = AnnotationUtils.findAnnotation(method, ApiProfile.class);
        if(null == apiProfile) {
            apiProfile = AnnotationUtils.findAnnotation(handlerType, ApiProfile.class);
        }

        if(null != apiProfile) {
            return StringUtils.containsIgnoreCase(apiProfile.value(), active);
        }

        return true;
    }

    /**
     * method condition
     * - 在方法上加@ApiVersion注解&url加{version}
     *
     * @param method method object
     * @return ApiVersionCondition
     */
    @Override
    protected RequestCondition<?> getCustomMethodCondition(Method method) {
        if(!versionOpen) {
            return super.getCustomMethodCondition(method);
        }
        return new ApiVersionCondition(AnnotationUtils.findAnnotation(method, ApiVersion.class));
    }
}

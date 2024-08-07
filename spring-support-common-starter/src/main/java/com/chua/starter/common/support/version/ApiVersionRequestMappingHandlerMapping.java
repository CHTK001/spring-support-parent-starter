package com.chua.starter.common.support.version;

import com.chua.starter.common.support.annotations.ApiPlatform;
import com.chua.starter.common.support.annotations.ApiVersion;
import com.chua.starter.common.support.properties.VersionProperties;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.servlet.mvc.condition.RequestCondition;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;

/**
 * API version setting
 *
 * @author w
 * @since 2020-11-15
 */
public class ApiVersionRequestMappingHandlerMapping extends RequestMappingHandlerMapping {

    private final VersionProperties versionProperties;

    public ApiVersionRequestMappingHandlerMapping(VersionProperties versionProperties) {
        this.versionProperties = versionProperties;
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
        ApiVersion apiVersion = AnnotationUtils.findAnnotation(handlerType, ApiVersion.class);
        return null == apiVersion ? super.getCustomTypeCondition(handlerType) : new ApiVersionCondition(apiVersion,
                AnnotationUtils.findAnnotation(handlerType, ApiPlatform.class),
                versionProperties);
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
        ApiVersion apiVersion = AnnotationUtils.findAnnotation(method, ApiVersion.class);
        return null == apiVersion ? super.getCustomMethodCondition(method) : new ApiVersionCondition(apiVersion, AnnotationUtils.findAnnotation(method, ApiPlatform.class), versionProperties);
    }
}

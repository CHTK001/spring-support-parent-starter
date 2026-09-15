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

// 注：Method 为 Spring RequestMappingHandlerMapping 覆写方法签名参数，豁免
import java.lang.reflect.Method;

/**
* API 版本控制请求映射处理器
* <p>
* 支持 @api版本、@api配置文件、@apiplatform 注解的版本和平台控制。
* </p>
*
* @since 2020-11-15
* @author CH
 */
public class ApiVersionRequestMappingHandlerMapping extends RequestMappingHandlerMapping {

    /** API属性 */
    private final ApiProperties apiProperties;
    /** Platformopen */
    private final boolean platformOpen;
    /** 版本打开 */
    private final boolean versionOpen;
    /** Platform */
    private final String platform;
    /** 是否激活配置文件 */
    private final String[] activeProfiles;

    /**
    * 创建 api版本请求mapping处理器mapping 实例
    * @param apiProperties api属性
    * @param environment 环境
    * @param environment 环境
     */
    public ApiVersionRequestMappingHandlerMapping(ApiProperties apiProperties, Environment environment) {
        this.apiProperties = apiProperties;
        this.activeProfiles = environment.getActiveProfiles();
        this.versionOpen = apiProperties.getVersion().isEnable();
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
        /**
        * api版本条件。
        * @param ApiVersion.class api版本.类
        * @return api版本条件的结果
         */
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

        // 检查环境配置
        if (activeProfiles != null && activeProfiles.length > 0) {
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
            for (String profile : activeProfiles) {
                if (ArrayUtils.containsIgnoreCase(apiProfile.value(), profile)) {
                    return true;
                }
            }
            return false;
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
        /**
        * api版本条件。
        * @param ApiVersion.class api版本.类
        * @return api版本条件的结果
         */
        return new ApiVersionCondition(AnnotationUtils.findAnnotation(method, ApiVersion.class));
    }
}


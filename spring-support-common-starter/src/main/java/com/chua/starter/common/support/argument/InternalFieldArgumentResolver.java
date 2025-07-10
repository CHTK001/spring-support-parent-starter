package com.chua.starter.common.support.argument;

import com.chua.common.support.utils.ClassUtils;
import com.chua.starter.common.support.annotations.ApiParam;
import com.chua.starter.common.support.configuration.resolver.RequestParamsMapMethodArgumentResolver;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.annotation.RequestParamMapMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * 内部字段解析器
 *
 * @author CH
 * @since 2025/7/8 11:32
 */
public class InternalFieldArgumentResolver extends RequestParamMapMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        Class<?> type = parameter.getParameterType();
        if (!ClassUtils.isJavaType(type)) {
            List<Field> declaredFields = ClassUtils.getFields(type);
            for (Field declaredField : declaredFields) {
                if (!Modifier.isStatic(declaredField.getModifiers()) && declaredField.isAnnotationPresent(ApiParam.class)) {
                    return true;
                }
            }
        }

        return parameter.hasParameterAnnotation(ApiParam.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) throws Exception {
        ApiParam apiParam = parameter.getParameterAnnotation(ApiParam.class);
        if (null == apiParam) {
            return super.resolveArgument(parameter, mavContainer, webRequest, binderFactory);
        }
        if (!apiParam.allowReceive()) {
            return null;
        }

        return convertValue(parameter, mavContainer, webRequest, binderFactory);
    }

    /**
     * 转换参数值
     */
    private Object convertValue(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
        RequestParamsMapMethodArgumentResolver requestParamsMapMethodArgumentResolver = new RequestParamsMapMethodArgumentResolver();
        return requestParamsMapMethodArgumentResolver.resolveArgument(parameter, mavContainer, webRequest, binderFactory);
    }
}

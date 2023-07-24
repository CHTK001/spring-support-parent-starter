package com.chua.starter.common.support.configuration.resolver;

import com.chua.common.support.utils.ArrayUtils;
import com.chua.starter.common.support.annotations.RequestParams;
import org.springframework.core.MethodParameter;
import org.springframework.web.method.annotation.RequestParamMapMethodArgumentResolver;

import java.util.Map;

/**
 *
 * @author CH
 */
public class RequestParamsMapMethodArgumentResolver extends RequestParamMapMethodArgumentResolver {
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        RequestParams requestParam = parameter.getParameterAnnotation(RequestParams.class);
        return (requestParam != null && Map.class.isAssignableFrom(parameter.getParameterType()) &&
                !ArrayUtils.isEmpty(requestParam.name()));
    }
}

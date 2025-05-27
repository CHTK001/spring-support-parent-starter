package com.chua.starter.swagger.support.filter;

import com.chua.starter.swagger.support.annotation.HiddenParam;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springframework.core.MethodParameter;
import org.springframework.util.ClassUtils;
import org.springframework.web.method.HandlerMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author CH
 * @since 2025/5/27 13:37
 */
public class CustomOperationCustomizer implements GlobalOperationCustomizer {

    static Class USER_VALUE = null;

    static {
        try {
            USER_VALUE = ClassUtils.forName("com.chua.starter.oauth.client.support.annotation.UserValue", CustomOperationCustomizer.class.getClassLoader());
        } catch (Exception ignored) {
        }
    }

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        if (null != operation.getParameters()) {
            filterMethod(operation, handlerMethod);
        }
        return operation;
    }

    private void filterMethod(Operation operation, HandlerMethod handlerMethod) {
        List<Parameter> parameters = operation.getParameters();
        List<Parameter> rs = new ArrayList<>(parameters.size());
        MethodParameter[] methodParameters = handlerMethod.getMethodParameters();
        for (MethodParameter methodParameter : methodParameters) {
            if (USER_VALUE != null && methodParameter.hasParameterAnnotation(USER_VALUE)) {
                continue;
            }

            if (methodParameter.hasParameterAnnotation(HiddenParam.class)) {
                continue;
            }

            io.swagger.v3.oas.annotations.Parameter parameterAnnotation = methodParameter.getParameterAnnotation(io.swagger.v3.oas.annotations.Parameter.class);
            if (parameterAnnotation != null && parameterAnnotation.hidden()) {
                continue;
            }
            Optional<Parameter> first = parameters.stream().filter(parameter -> parameter.getName().equals(methodParameter.getParameterName())).findFirst();
            first.ifPresent(rs::add);
        }
        operation.setParameters(rs);
    }
}

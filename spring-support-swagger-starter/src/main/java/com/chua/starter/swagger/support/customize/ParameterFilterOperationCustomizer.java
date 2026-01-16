package com.chua.starter.swagger.support.customize;

import com.chua.starter.swagger.support.Knife4jProperties;
import com.chua.starter.swagger.support.annotation.HiddenParam;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.annotations.ParameterObject;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springframework.core.MethodParameter;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.method.HandlerMethod;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 参数过滤操作定制器
 * 用于过滤隐藏参数、UserValue参数等
 *
 * @author CH
 * @since 2025/5/27 13:37
 */
public class ParameterFilterOperationCustomizer implements GlobalOperationCustomizer {

    /**
     * UserValue注解类（可选依赖）
     */
    private static final Class<?> USER_VALUE_CLASS = loadUserValueClass();

    /**
     * 加载UserValue注解类
     *
     * @return UserValue注解类，如果不存在则返回null
     */
    private static Class<?> loadUserValueClass() {
        try {
            return ClassUtils.forName("com.chua.starter.oauth.client.support.annotation.UserValue", 
                    ParameterFilterOperationCustomizer.class.getClassLoader());
        } catch (Exception ignored) {
            return null;
        }
    }

    private final Knife4jProperties knife4jProperties;
    private final Map<String, String> defaultHeaders;

    /**
     * 构造函数
     *
     * @param knife4jProperties Knife4j配置属性
     */
    public ParameterFilterOperationCustomizer(Knife4jProperties knife4jProperties) {
        this.knife4jProperties = knife4jProperties;
        this.defaultHeaders = Optional.ofNullable(knife4jProperties.getDefaultHeader())
                .orElse(Collections.emptyMap());
    }

    /**
     * 定制操作
     *
     * @param operation 操作对象
     * @param handlerMethod 处理器方法
     * @return 定制后的操作对象
     */
    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        if (operation.getParameters() != null) {
            filterMethod(operation, handlerMethod);
        }
        return operation;
    }

    /**
     * 过滤方法参数
     *
     * @param operation 操作对象
     * @param handlerMethod 处理器方法
     */
    private void filterMethod(Operation operation, HandlerMethod handlerMethod) {
        List<Parameter> parameters = operation.getParameters();
        List<Parameter> filteredParameters = new ArrayList<>(parameters.size());
        MethodParameter[] methodParameters = handlerMethod.getMethodParameters();
        
        for (MethodParameter methodParameter : methodParameters) {
            if (shouldSkipParameter(methodParameter)) {
                continue;
            }
            registerParameter(filteredParameters, parameters, methodParameter);
        }
        
        // 添加默认请求头
        addDefaultHeaders(filteredParameters);
        operation.setParameters(filteredParameters);
    }

    /**
     * 判断是否应该跳过参数
     *
     * @param methodParameter 方法参数
     * @return 是否跳过
     */
    private boolean shouldSkipParameter(MethodParameter methodParameter) {
        // 跳过UserValue注解的参数
        if (USER_VALUE_CLASS != null && methodParameter.hasParameterAnnotation(getUserValueAnnotationClass())) {
            return true;
        }
        
        // 跳过HiddenParam注解的参数
        if (methodParameter.hasParameterAnnotation(HiddenParam.class)) {
            return true;
        }
        
        // 跳过hidden=true的@Parameter注解
        io.swagger.v3.oas.annotations.Parameter parameterAnnotation = 
                methodParameter.getParameterAnnotation(io.swagger.v3.oas.annotations.Parameter.class);
        if (parameterAnnotation != null && parameterAnnotation.hidden()) {
            return true;
        }
        
        // 跳过BindingResult参数
        return methodParameter.getParameterType().isAssignableFrom(BindingResult.class);
    }

    /**
     * 获取 UserValue 注解类型
     *
     * @return UserValue 注解 Class
     */
    @SuppressWarnings("unchecked")
    private Class<? extends Annotation> getUserValueAnnotationClass() {
        return (Class<? extends Annotation>) USER_VALUE_CLASS;
    }

    /**
     * 注册参数
     *
     * @param resultList 结果列表
     * @param parameters 参数列表
     * @param methodParameter 方法参数
     */
    private void registerParameter(List<Parameter> resultList, List<Parameter> parameters, 
                                   MethodParameter methodParameter) {
        if (!methodParameter.hasParameterAnnotation(ParameterObject.class)) {
            parameters.stream()
                    .filter(parameter -> parameter.getName().equals(methodParameter.getParameterName()))
                    .findFirst()
                    .ifPresent(resultList::add);
            return;
        }
        java.lang.reflect.Parameter parameter = methodParameter.getParameter();
        register(resultList, parameters, parameter.getType());
    }

    /**
     * 注册参数（递归处理嵌套对象）
     *
     * @param resultList 结果列表
     * @param parameters 参数列表
     * @param type 类型
     */
    private void register(List<Parameter> resultList, List<Parameter> parameters, Class<?> type) {
        ReflectionUtils.doWithFields(type, field -> {
            if (field.isAnnotationPresent(HiddenParam.class)) {
                return;
            }
            registerParameter(resultList, parameters, field);
        });
    }

    /**
     * 注册字段参数
     *
     * @param resultList 结果列表
     * @param parameters 参数列表
     * @param field 字段
     */
    private void registerParameter(List<Parameter> resultList, List<Parameter> parameters, Field field) {
        if (!field.isAnnotationPresent(ParameterObject.class)) {
            parameters.stream()
                    .filter(parameter -> parameter.getName().equals(field.getName()))
                    .findFirst()
                    .ifPresent(resultList::add);
            return;
        }
        register(resultList, parameters, field.getType());
    }

    /**
     * 添加默认请求头
     *
     * @param parameters 参数列表
     */
    private void addDefaultHeaders(List<Parameter> parameters) {
        for (Map.Entry<String, String> entry : defaultHeaders.entrySet()) {
            parameters.add(new Parameter()
                    .in("header")
                    .name(entry.getKey())
                    .schema(new io.swagger.v3.oas.models.media.StringSchema()
                            .example(entry.getValue())));
        }
    }
}


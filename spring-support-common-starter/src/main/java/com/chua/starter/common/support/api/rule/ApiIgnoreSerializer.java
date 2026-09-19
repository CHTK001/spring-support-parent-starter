package com.chua.starter.common.support.api.rule;

import com.chua.common.support.utils.ArrayUtils;
import com.chua.starter.common.support.api.annotations.ApiFieldIgnore;
import com.chua.starter.common.support.api.annotations.ApiGroup;
import com.chua.spring.support.configuration.SpringBeanUtils;
import com.chua.starter.common.support.utils.RequestUtils;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;

/**
 * 忽略字段
 *
 * @see ApiFieldIgnore
 * @see ApiGroup
 * @since 2025/1/1
 * @author CH
 */
public class ApiIgnoreSerializer extends JsonSerializer<Object> implements ContextualSerializer {
    /** 请求mapping处理器mapping */
    private final RequestMappingHandlerMapping requestMappingHandlerMapping;
    private Class<?>[] group; // 群体

    /**
     * 创建 apiignore序列化器 实例
     * @param group 群体
     */
    public ApiIgnoreSerializer(Class<?>[] group) {
        /**
         * this。
         */
        this();
        this.group = group;
    }

    /** 创建 apiignore序列化器 实例 */
    public ApiIgnoreSerializer() {
        requestMappingHandlerMapping = SpringBeanUtils.getRequestMappingHandlerMapping();
    }

    @Override
    /** 序列化 */
    public void serialize(Object value, JsonGenerator jsonGenerator, SerializerProvider serializers) throws IOException {
        jsonGenerator.writeNull();
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider serializerProvider, BeanProperty beanProperty) throws JsonMappingException {
        if (beanProperty != null) {
            ApiFieldIgnore apiFieldIgnore = beanProperty.getAnnotation(ApiFieldIgnore.class);
            if (apiFieldIgnore == null) {
                apiFieldIgnore = beanProperty.getContextAnnotation(ApiFieldIgnore.class);
            }
            if (apiFieldIgnore != null&& isMatch(apiFieldIgnore)) {
                /**
                 * apiignore序列化器。
                 * @param apiFieldIgnore.value( api字段ignore.值(
                 */
                return new ApiIgnoreSerializer(apiFieldIgnore.value());
            }
            return serializerProvider.findValueSerializer(beanProperty.getType(), beanProperty);
        }
        return serializerProvider.findNullValueSerializer(null);
    }

    /**
     * 是否匹配
     *
     * @param apiFieldIgnore api字段ignore
     * @return 是否匹配的结果
     */
    private boolean isMatch(ApiFieldIgnore apiFieldIgnore) {
        Class<?>[] value = apiFieldIgnore.value();
        if(value.length == 0) {
            return true;
        }

        HttpServletRequest servletRequest = RequestUtils.getRequest();
        if(null == servletRequest) {
            return false;
        }

        HandlerExecutionChain handler = null;
        try {
            handler = requestMappingHandlerMapping.getHandler(servletRequest);
        } catch (Exception ignored) {
            return false;
        }

        if(null == handler) {
            return false;
        }

        Object handler1 = handler.getHandler();
        if(null == handler1 || !(handler1 instanceof HandlerMethod handlerMethod)) {
            return false;
        }
        ApiGroup apiGroup = handlerMethod.getMethodAnnotation(ApiGroup.class);
        if(null == apiGroup) {
            return false;
        }
        Class<?>[] group = apiGroup.value();
        return isMatch(value, group);
    }

    /**
     * 是否匹配
     *
     * @param value 值
     * @param group 群体
     * @return 是否匹配
     */
    private boolean isMatch(Class<?>[] value, Class<?>[] group) {
        for (Class<?> aClass : group) {
            if(ArrayUtils.contains(value, aClass)) {
                return true;
            }
        }
        return false;
    }
}


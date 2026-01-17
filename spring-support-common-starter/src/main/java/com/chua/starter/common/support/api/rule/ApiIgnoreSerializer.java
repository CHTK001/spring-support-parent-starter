package com.chua.starter.common.support.api.rule;

import com.chua.common.support.core.utils.ArrayUtils;
import com.chua.starter.common.support.api.annotations.ApiFieldIgnore;
import com.chua.starter.common.support.api.annotations.ApiGroup;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
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
 * @author CH
 * @see ApiFieldIgnore
 * @see ApiGroup
 * @since 2025/1/1
 */
public class ApiIgnoreSerializer extends JsonSerializer<Object> implements ContextualSerializer {
    private final RequestMappingHandlerMapping requestMappingHandlerMapping;
    private Class<?>[] group;

    public ApiIgnoreSerializer(Class<?>[] group) {
        this();
        this.group = group;
    }

    public ApiIgnoreSerializer() {
        requestMappingHandlerMapping = SpringBeanUtils.getRequestMappingHandlerMapping();
    }

    @Override
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
                return new ApiIgnoreSerializer(apiFieldIgnore.value());
            }
            return serializerProvider.findValueSerializer(beanProperty.getType(), beanProperty);
        }
        return serializerProvider.findNullValueSerializer(null);
    }

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
     * @param value value
     * @param group group
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


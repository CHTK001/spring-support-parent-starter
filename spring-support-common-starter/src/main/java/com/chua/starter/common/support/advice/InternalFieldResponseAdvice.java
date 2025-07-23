package com.chua.starter.common.support.advice;

import com.chua.starter.common.support.annotations.ApiParam;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

/**
 * 内部字段返回
 *
 * @author CH
 * @since 2025/7/8 13:13
 */
@SuppressWarnings("ALL")
@ControllerAdvice
public class InternalFieldResponseAdvice implements ResponseBodyAdvice<Object> {
    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType, Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        if (body == null) return null;
        return processInternalFields(body);
    }

    /**
     * 处理内部字段
     */
    private Object processInternalFields(Object obj) {
        if (obj instanceof Collection collection) {
            return processInternalCollection(collection);
        }
        Arrays.stream(obj.getClass().getDeclaredFields())
                .map(it -> {
                    ApiParam apiParam = it.getDeclaredAnnotation(ApiParam.class);
                    if (apiParam == null) {
                        return null;
                    }
                    return new FieldAndAnnotation(it, apiParam);
                })
                .filter(Objects::nonNull)
                .filter(it -> !it.allowReturn())
                .forEach(it -> {
                    try {
                        it.cleanValue(obj);
                    } catch (Exception e) {
                        // 处理异常
                    }
                });
        return obj;
    }

    /**
     * 处理内部集合
     */
    private Collection processInternalCollection(Collection collection) {
        return collection.stream().map(this::processInternalFields).toList();
    }

    /**
     * 字段和注解的映射关系
     */
    @Data
    @AllArgsConstructor
    static class FieldAndAnnotation {
        private Field field;
        private ApiParam apiParam;

        /**
         * 是否允许返回
         */
        public boolean allowReturn() {
            return apiParam.allowReturn();
        }

        public void cleanValue(Object obj) {
            try {
                field.setAccessible(true);
                field.set(obj, null);
            } catch (Exception e) {
                // 处理异常
            }
        }
    }
}

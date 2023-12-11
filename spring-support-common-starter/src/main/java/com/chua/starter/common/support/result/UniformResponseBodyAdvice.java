package com.chua.starter.common.support.result;

import com.chua.common.support.annotations.Ignore;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.concurrent.Callable;

/**
 * @author CH
 */
@RestControllerAdvice
@Slf4j
public class UniformResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter methodParameter, Class<? extends HttpMessageConverter<?>> aClass) {
        return true;
    }

    @SneakyThrows
    @Override
    public Object beforeBodyWrite(Object o, MethodParameter methodParameter, MediaType mediaType, Class<? extends HttpMessageConverter<?>> aClass, ServerHttpRequest serverHttpRequest, ServerHttpResponse serverHttpResponse) {
        Class<?> declaringClass = methodParameter.getDeclaringClass();
        String typeName = declaringClass.getTypeName();
        if (typeName.contains("swagger")) {
            return o;
        }


        if(mediaType.getSubtype().contains("spring-boot.actuator")) {
            return o;
        }

        if(mediaType.getSubtype().contains("event-stream")) {
            return o;
        }

        if(AnnotationUtils.isAnnotationDeclaredLocally(Ignore.class, declaringClass)) {
            return o;
        }

        if(null != methodParameter.getMethodAnnotation(Ignore.class)) {
            return o;
        }

        if (o instanceof org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice || o instanceof byte[] || o instanceof Callable) {
            return o;
        }

        if (o instanceof ReturnPageResult) {
            return o;
        }
        if (o instanceof ResultData) {
            return o;
        }

        if (o instanceof ReturnResult || (null != o && (o.getClass().getTypeName().endsWith("result.PageResult")))) {
            return o;
        }

//        if (aClass == ResultDataHttpMessageConverter.class) {
//            return ResultData.success(o);
//        }
        return Result.success(o);
    }
}

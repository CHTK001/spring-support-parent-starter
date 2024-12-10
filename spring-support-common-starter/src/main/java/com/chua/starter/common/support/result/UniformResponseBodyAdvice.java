package com.chua.starter.common.support.result;

import com.chua.common.support.annotations.Ignore;
import com.chua.common.support.annotations.IgnoreReturnType;
import com.chua.common.support.lang.code.ReturnBodyResult;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.StringUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

import static com.chua.common.support.lang.code.ReturnCode.SUCCESS;

/**
 * 统一返回值
 * @author CH
 */
@RestControllerAdvice
@Slf4j
@SuppressWarnings("ALL")
public class UniformResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter methodParameter, Class<? extends HttpMessageConverter<?>> aClass) {
        return true;
    }

    @SneakyThrows
    @Override
    public Object beforeBodyWrite(Object o, MethodParameter methodParameter, MediaType mediaType, Class<? extends HttpMessageConverter<?>> aClass, ServerHttpRequest serverHttpRequest, ServerHttpResponse serverHttpResponse) {
        Class<?> declaringClass = methodParameter.getMethod().getReturnType();
        if(ResponseEntity.class.isAssignableFrom(declaringClass)) {
            return o;
        }

        String url = serverHttpRequest.getURI().toURL().toExternalForm();

        if (url.contains("swagger")) {
            return o;
        }

        String subtype = mediaType.getSubtype();
        if(subtype.contains("spring-boot.actuator")) {
            return o;
        }

        if(subtype.contains("event-stream") || subtype.contains("octet-stream")) {
            return o;
        }

        if(o instanceof ReturnBodyResult) {
            ReturnBodyResult item = (ReturnBodyResult) o;
            serverHttpResponse.getHeaders()
                    .add("Content-Type", item.getContentType());
            if(StringUtils.isNotBlank(item.getFileName())) {
                serverHttpResponse.getHeaders().add("Content-Disposition", "attachment; filename=" + URLEncoder.encode(item.getFileName(), StandardCharsets.UTF_8));
            }
            return item.getData();
        }


        if(AnnotationUtils.isAnnotationDeclaredLocally(IgnoreReturnType.class, declaringClass)) {
            return o;
        }

        if(null != methodParameter.getMethodAnnotation(IgnoreReturnType.class)) {
            return o;
        }

        if (o instanceof org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice || o instanceof byte[] || o instanceof Callable) {
            return o;
        }

        if (o instanceof ReturnPageResult) {
            return o;
        }

        if (o instanceof ResultData resultData) {
            if(resultData.getData() instanceof byte[] && resultData.getCode().equals(SUCCESS.getCode())) {
                return resultData.getData();
            }
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

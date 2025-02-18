package com.chua.starter.common.support.result;

import com.chua.common.support.annotations.IgnoreReturnType;
import com.chua.common.support.http.ContentDisposition;
import com.chua.common.support.http.ContentType;
import com.chua.common.support.lang.code.ReturnCode;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.code.preconditioning.ReturnPreconditioning;
import com.chua.starter.common.support.annotations.ApiReturnFormatIgnore;
import jakarta.annotation.Resource;
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
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.chua.common.support.lang.code.ReturnCode.SUCCESS;
import static com.chua.common.support.utils.ClassUtils.isAssignableFrom;

/**
 * 统一返回值
 * @author CH
 */
@RestControllerAdvice
@Slf4j
@SuppressWarnings("ALL")
public class UniformResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    @Resource(name = "uniform")
    private ExecutorService executorService;

    @Override
    public boolean supports(MethodParameter methodParameter, Class<? extends HttpMessageConverter<?>> aClass) {
        return true;
    }

    @SneakyThrows
    @Override
    public Object beforeBodyWrite(Object o, MethodParameter methodParameter, MediaType mediaType, Class<? extends HttpMessageConverter<?>> aClass, ServerHttpRequest serverHttpRequest, ServerHttpResponse serverHttpResponse) {
        Method method = methodParameter.getMethod();
        Class<?> declaringClass = method.getReturnType();
        if (isIgnoreReturnFormat(methodParameter, method, declaringClass, serverHttpRequest, mediaType)) {
            return o;
        }

        if (o instanceof ReturnPreconditioning<?> preconditioning) {
            return bodyWithPreconditioning(preconditioning, serverHttpResponse);
        }

        if (o instanceof org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice
                || o instanceof byte[]
                || o instanceof Callable
                || o instanceof DeferredResult
        ) {
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

        return Result.success(o);
    }

    /**
     * 预处理
     *
     * @param preconditioning
     * @param serverHttpResponse
     * @return
     */
    private Object bodyWithPreconditioning(ReturnPreconditioning<?> preconditioning, ServerHttpResponse serverHttpResponse) {
        ContentType contentType = preconditioning.getContentType();
        if (null != contentType) {
            serverHttpResponse.getHeaders().setContentType(MediaType.valueOf(contentType.getMimeType()));
        }

        ContentDisposition contentDisposition = preconditioning.getContentDisposition();
        if (null != contentDisposition) {
            serverHttpResponse.getHeaders().setContentDisposition(org.springframework.http.ContentDisposition.attachment()
                    .filename(contentDisposition.getFilename(), contentDisposition.getCharset())
                    .name(contentDisposition.getName())
                    .build());
        }

        return createNewResult(preconditioning);
    }

    private Object createNewResult(ReturnPreconditioning<?> preconditioning) {
        ReturnResult<?> result = preconditioning.toResult();
        if (!preconditioning.isSuccessful()) {
            return result;
        }
        Object data = !preconditioning.hasUnpack() ? result : result.getData();
        if (preconditioning.isAsync() && Supplier.class.isAssignableFrom(data.getClass())) {
            DeferredResult deferredResult = new DeferredResult<>((long) preconditioning.getTimeoutMillis());
            executorService.execute(Thread.ofVirtual()
                    .unstarted(new Runnable() {
                        @Override
                        public void run() {
                            deferredResult.setResult(((Supplier<?>) data).get());
                        }
                    })
            );
            deferredResult.onTimeout(() -> {
                deferredResult.setErrorResult(ReturnResult.error(ReturnCode.SYSTEM_EXECUTION_TIMEOUT));
            });

            deferredResult.onError(new Consumer<Throwable>() {

                @Override
                public void accept(Throwable it) {
                    it.printStackTrace();
                    deferredResult.setErrorResult(ReturnResult.error(ReturnCode.SYSTEM_EXECUTION_ERROR));
                }
            });

            return deferredResult;
        }
        return data;
    }

    @SneakyThrows
    private boolean isIgnoreReturnFormat(MethodParameter methodParameter, Method method, Class<?> declaringClass, ServerHttpRequest serverHttpRequest, MediaType mediaType) {
        if (methodParameter.hasMethodAnnotation(ApiReturnFormatIgnore.class)) {
            return true;
        }
        if (declaringClass.isAnnotationPresent(ApiReturnFormatIgnore.class) ||
                isAssignableFrom(ResponseEntity.class, declaringClass)
        ) {
            return true;
        }

        String url = serverHttpRequest.getURI().toURL().toExternalForm();

        if (url.contains("swagger")) {
            return true;
        }

        String subtype = mediaType.getSubtype();
        if (subtype.contains("spring-boot.actuator")) {
            return true;
        }

        if (subtype.contains("event-stream") || subtype.contains("octet-stream")) {
            return true;
        }

        if (AnnotationUtils.isAnnotationDeclaredLocally(IgnoreReturnType.class, declaringClass)) {
            return true;
        }

        if (null != methodParameter.getMethodAnnotation(IgnoreReturnType.class)) {
            return true;
        }

        return false;
    }
}

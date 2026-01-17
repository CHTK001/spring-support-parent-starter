package com.chua.starter.common.support.api.response;
import com.chua.common.support.core.annotation.IgnoreReturnType;
import com.chua.common.support.network.http.ContentDisposition;
import com.chua.common.support.network.http.ContentType;
import com.chua.common.support.lang.code.ReturnCode;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.code.preconditioning.ReturnPreconditioning;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import com.chua.starter.common.support.api.properties.ApiProperties;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import reactor.core.publisher.Flux;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.chua.common.support.core.utils.ClassUtils.isAssignableFrom;

import lombok.extern.slf4j.Slf4j;

/**
 * 统一返回值处理
 * <p>
 * 自动将返回值包装为统一的 Result 格式。
 * </p>
 *
 * @author CH
 * @since 2024/01/01
 * @version 1.0.0
 */
@Slf4j
@RestControllerAdvice
@SuppressWarnings("ALL")
public class ApiUniformResponseBodyAdvice implements ResponseBodyAdvice<Object>, EnvironmentAware {
        /**
     * 忽略包装的URL关键字
     */
    private static final String SWAGGER_PATH = "swagger";
    
    /**
     * Actuator类型标识
     */
    private static final String ACTUATOR_SUBTYPE = "spring-boot.actuator";
    
    /**
     * 流式响应类型
     */
    private static final String EVENT_STREAM = "event-stream";
    private static final String OCTET_STREAM = "octet-stream";
    
    /**
     * PageResult类名后缀
     */
    private static final String PAGE_RESULT_SUFFIX = "result.PageResult";

    @Resource(name = "uniform")
    private ExecutorService executorService;

    private ApiProperties apiProperties;
    private String[] ignoreFormatPackages;
    private boolean noPackages;

    @Override
    public boolean supports(MethodParameter methodParameter, Class<? extends HttpMessageConverter<?>> aClass) {
        return true;
    }

    @SneakyThrows
    @Override
    public Object beforeBodyWrite(Object o, MethodParameter methodParameter, MediaType mediaType, Class<? extends HttpMessageConverter<?>> aClass, ServerHttpRequest serverHttpRequest, ServerHttpResponse serverHttpResponse) {
        if (o instanceof ReturnPreconditioning<?> preconditioning) {
            return bodyWithPreconditioning(preconditioning, serverHttpResponse);
        }

        if (o instanceof org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice
                || o instanceof byte[]
                || o instanceof ResponseEntity
                || o instanceof Callable
                || o instanceof DeferredResult
        ) {
            return o;
        }

        if (o instanceof ReturnPageResult) {
            return o;
        }


        if (o instanceof ReturnResult || isPageResultType(o)) {
            return o;
        }

        Class<?> parameterDeclaringClass = methodParameter.getDeclaringClass();
        if (isIgnorePackages(parameterDeclaringClass)) {
            return o;
        }
        Method method = methodParameter.getMethod();
        Class<?> declaringClass = method.getReturnType();
        if (isIgnoreReturnFormat(methodParameter, method, declaringClass, serverHttpRequest, mediaType)) {
            return o;
        }

        if (o instanceof Flux<?> flux) {
            return createFluxBody(flux, serverHttpResponse);
        }

        return ReturnResult.success(o);
    }

    private Object createFluxBody(Flux<?> flux, ServerHttpResponse serverHttpResponse) {
        return flux.map(it -> ReturnResult.success(it));
    }

    /**
     * 是否忽略返回格式
     *
     * @param parameterDeclaringClass 参数声明类
     * @return 是否忽略
     */
    private boolean isIgnorePackages(Class<?> parameterDeclaringClass) {
        if (null == apiProperties) {
            return false;
        }

        if (noPackages) {
            return false;
        }

        String typeName = parameterDeclaringClass.getTypeName();
        for (String ignoreFormatPackage : ignoreFormatPackages) {
            if (typeName.startsWith(ignoreFormatPackage)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 预处理
     *
     * @param preconditioning    预处理对象
     * @param serverHttpResponse 响应对象
     * @return 处理结果
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

                    deferredResult.onError(throwable -> {
                        log.error("[统一响应]异步任务执行失败", throwable);
                        deferredResult.setErrorResult(ReturnResult.error(ReturnCode.SYSTEM_EXECUTION_ERROR));
                    });

            return deferredResult;
        }
        return data;
    }

    @SneakyThrows
    private boolean isIgnoreReturnFormat(MethodParameter methodParameter, Method method, Class<?> declaringClass, ServerHttpRequest serverHttpRequest, MediaType mediaType) {
        // 检查方法级别注解
        if (methodParameter.hasMethodAnnotation(ApiReturnFormatIgnore.class)) {
            return true;
        }

        // 检查类级别注解或ResponseEntity类型
        if (declaringClass.isAnnotationPresent(ApiReturnFormatIgnore.class) ||
                isAssignableFrom(ResponseEntity.class, declaringClass)) {
            return true;
        }

        // 检查Swagger路径
        String url = serverHttpRequest.getURI().toURL().toExternalForm();
        if (url.contains(SWAGGER_PATH)) {
            return true;
        }

        // 检查响应类型
        String subtype = mediaType.getSubtype();
        if (subtype.contains(ACTUATOR_SUBTYPE) || 
            subtype.contains(EVENT_STREAM) || 
            subtype.contains(OCTET_STREAM)) {
            return true;
        }

        // 检查IgnoreReturnType注解
        if (AnnotationUtils.isAnnotationDeclaredLocally(IgnoreReturnType.class, declaringClass)) {
            return true;
        }

        return methodParameter.getMethodAnnotation(IgnoreReturnType.class) != null;
    }

    @Override
    public void setEnvironment(Environment environment) {
        apiProperties = Binder.get(environment)
                .bindOrCreate(ApiProperties.PRE, ApiProperties.class);
        ignoreFormatPackages = apiProperties.getIgnoreFormatPackages();
        noPackages = null == ignoreFormatPackages || ignoreFormatPackages.length == 0;
    }
    
    /**
     * 判断是否为PageResult类型
     *
     * @param obj 对象
     * @return 是否为PageResult类型
     */
    private boolean isPageResultType(Object obj) {
        if (obj == null) {
            return false;
        }
        String typeName = obj.getClass().getTypeName();
        return typeName.endsWith(PAGE_RESULT_SUFFIX);
    }
}


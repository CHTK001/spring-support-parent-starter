package com.chua.starter.common.support.api.response;
import com.chua.common.support.lang.code.ReturnCode;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.code.ReturnPreconditioning;
import com.chua.common.support.annotation.IgnoreReturnType;
import com.chua.starter.common.support.lang.ReturnPageResult;
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
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import reactor.core.publisher.Flux;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.chua.common.support.utils.ClassUtils.isAssignableFrom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 统一返回值处理
 * <p>
 * 自动将返回值包装为统一的 Result 格式。
 * </p>
 *
 * @since 2024/01/01
 * @version 1.0.0
 * @author CH
 */
@RestControllerAdvice
@SuppressWarnings({"unchecked", "rawtypes"})
public class ApiUniformResponseBodyAdvice implements ResponseBodyAdvice<Object>, EnvironmentAware {
    /** log */
    private static final Logger log= LoggerFactory.getLogger(ApiUniformResponseBodyAdvice.class);
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
    /** OCTESTREAM */
    private static final String OCTET_STREAM= "octet-stream";
    
    /**
     * PageResult类名后缀
     */
    private static final String PAGE_RESULT_SUFFIX = "result.PageResult";

    @Resource(name = "uniform")
    /** executor服务 */
    private ExecutorService executorService;

    /** apiProperties */
    private ApiProperties apiProperties;
    /** ignoreFormatPackages */
    private String[] ignoreFormatPackages;
    /** noPackages */
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
                || o instanceof StreamingResponseBody
                || o instanceof ResponseBodyEmitter
        ) {
            return o;
        }

        if (o instanceof ReturnPageResult) {
            return o;
        }


        if (o instanceof ReturnResult || isPageResultType(o)) {
            return o;
        }

        Class<?> controllerClass = methodParameter.getContainingClass();
        if (isIgnorePackages(controllerClass)) {
            return o;
        }
        Method method = methodParameter.getMethod();
        if (isIgnoreReturnFormat(methodParameter, method, controllerClass, serverHttpRequest, mediaType)) {
            return o;
        }

        if (o instanceof Flux<?> flux) {
            return createFluxBody(flux, serverHttpResponse);
        }

        return ReturnResult.ok(o);
    }

    private Object createFluxBody(Flux<?> flux, ServerHttpResponse serverHttpResponse) {
        return flux.map(it -> ReturnResult.<Object>ok(it));
    }

    /**
     * 是否忽略返回格式
     *
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
        return createNewResult(preconditioning);
    }

    private Object createNewResult(ReturnPreconditioning<?> preconditioning) {
        ReturnResult<?> result = preconditioning.asResult();
        if (!preconditioning.isSuccessful()) {
            return result;
        }
        Object data = preconditioning.getData();
        if (data instanceof Supplier) {
            DeferredResult<Object> deferredResult = new DeferredResult<>();
            executorService.execute(Thread.ofVirtual()
                    .unstarted(() -> {
                        try {
                            deferredResult.setResult(((Supplier<?>) data).get());
                        } catch (Exception throwable) {
                            deferredResult.setErrorResult(throwable);
                        }
                    })
            );
            deferredResult.onTimeout(() -> {
                        deferredResult.setErrorResult(ReturnResult.error(ReturnCode.SYSTEM_EXECUTION_TIMEOUT.getCode(), ReturnCode.SYSTEM_EXECUTION_TIMEOUT.getMsg()));
            });
            return deferredResult;
        }
        return data;
    }

    @SneakyThrows
    private boolean isIgnoreReturnFormat(MethodParameter methodParameter, Method method, Class<?> controllerClass, ServerHttpRequest serverHttpRequest, MediaType mediaType) {
        // 检查方法级别注解
        if (methodParameter.hasMethodAnnotation(ApiReturnFormatIgnore.class)) {
            return true;
        }

        // 检查类级别注解或ResponseEntity类型
        if (AnnotationUtils.findAnnotation(controllerClass, ApiReturnFormatIgnore.class) != null ||
                isAssignableFrom(ResponseEntity.class, method.getReturnType())) {
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
        if (AnnotationUtils.findAnnotation(controllerClass, IgnoreReturnType.class) != null) {
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

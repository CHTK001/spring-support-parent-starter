package com.chua.starter.unified.client.support.aspect;

import com.chua.common.support.json.JsonObject;
import com.chua.common.support.lang.code.ReturnCode;
import com.chua.common.support.task.limit.RateLimitMappingFactory;
import com.chua.common.support.task.limit.resolver.RateLimitResolver;
import com.chua.common.support.utils.ArrayUtils;
import com.chua.common.support.utils.MapUtils;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.unified.client.support.event.UnifiedEvent;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;

/**
 * @author CH
 */
@Slf4j
public class UnifiedLimitAspect {
    /**
     * 不同的接口，不同的流量控制
     * map的key为 Limiter.key
     */

    private final RateLimitMappingFactory rateLimitFactory = RateLimitMappingFactory.getInstance();
    private final ApplicationContext applicationContext;

    public UnifiedLimitAspect(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 全局控制器限制
     *
     * @param methodInvocation 连接点
     * @param method    方法
     * @return {@link Object}
     * @throws Throwable 可丢弃
     */
    public Object globalControllerLimit(MethodInvocation methodInvocation, Method method) throws Throwable {
        HttpServletRequest request = RequestUtils.getRequest();
        if(null == request) {
            return methodInvocation.proceed();
        }

        Map<String, Object> annotationAttributes = synthesizeAnnotation(method);
        if(annotationAttributes.isEmpty()) {
            return methodInvocation.proceed();
        }

        String[] url = MapUtils.getStringArray(annotationAttributes, "value", "path");

        RateLimitResolver resolver = rateLimitFactory.getRateLimitResolver(url);
        if(null == resolver) {
            return methodInvocation.proceed();
        }

        try {
            if(resolver.resolve(request.getRemoteAddr())) {
                return methodInvocation.proceed();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

        applicationContext.publishEvent(
                new UnifiedEvent(new JsonObject().fluentPut("requestAddress", RequestUtils.getIpAddress())
                .fluentPut("requestUrl", ArrayUtils.toString(url)), "STORE", "LIMIT"));
        throw new BusinessException(ReturnCode.SYSTEM_SERVER_BUSINESS.getMsg());
    }

    /**
     * 合成注解
     *
     * @param method 方法
     * @return {@link Map}<{@link String}, {@link Object}>
     */
    private Map<String, Object> synthesizeAnnotation(Method method) {
        if(method.isAnnotationPresent(RequestMapping.class)) {
            return AnnotationUtils.getAnnotationAttributes(AnnotationUtils.findAnnotation(method, RequestMapping.class));
        }

        if(method.isAnnotationPresent(GetMapping.class)) {
            return AnnotationUtils.getAnnotationAttributes(AnnotationUtils.findAnnotation(method, GetMapping.class));
        }


        if(method.isAnnotationPresent(PostMapping.class)) {
            return AnnotationUtils.getAnnotationAttributes(AnnotationUtils.findAnnotation(method, PostMapping.class));
        }

        if(method.isAnnotationPresent(PutMapping.class)) {
            return AnnotationUtils.getAnnotationAttributes(AnnotationUtils.findAnnotation(method, PutMapping.class));
        }

        if(method.isAnnotationPresent(DeleteMapping.class)) {
            return AnnotationUtils.getAnnotationAttributes(AnnotationUtils.findAnnotation(method, DeleteMapping.class));
        }


        return Collections.emptyMap();
    }

}

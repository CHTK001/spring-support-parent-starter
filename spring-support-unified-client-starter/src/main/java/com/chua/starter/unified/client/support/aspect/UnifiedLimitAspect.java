package com.chua.starter.unified.client.support.aspect;

import com.chua.common.support.lang.code.ReturnCode;
import com.chua.common.support.task.limit.RateLimitMappingFactory;
import com.chua.common.support.task.limit.resolver.RateLimitResolver;
import com.chua.common.support.utils.MapUtils;
import com.chua.starter.common.support.utils.RequestUtils;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
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

        Annotation annotation = AnnotationUtils.findAnnotation(method, RequestMapping.class);
        if(null == annotation) {
            return methodInvocation.proceed();
        }

        Map<String, Object> annotationAttributes = AnnotationUtils.getAnnotationAttributes(annotation);
        String[] url = MapUtils.getStringArray(annotationAttributes, "value", "path");

        RateLimitResolver resolver = rateLimitFactory.getRateLimitResolver(url);
        if(null == resolver) {
            return methodInvocation.proceed();
        }

        if(resolver.resolve(request.getRemoteAddr())) {
            return methodInvocation.proceed();
        }

        throw new RuntimeException(ReturnCode.SYSTEM_SERVER_BUSINESS.getMsg());
    }

}

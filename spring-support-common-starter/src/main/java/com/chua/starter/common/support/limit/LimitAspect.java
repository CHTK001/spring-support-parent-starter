package com.chua.starter.common.support.limit;

import com.chua.common.support.lang.code.ReturnCode;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.task.limit.Limit;
import com.chua.common.support.task.limit.LimiterProvider;
import com.chua.common.support.task.limit.RateLimitMappingFactory;
import com.chua.common.support.task.limit.resolver.RateLimitResolver;
import com.chua.common.support.utils.MapUtils;
import com.chua.starter.common.support.properties.LimitProperties;
import com.chua.starter.common.support.utils.RequestUtils;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author CH
 */
@Slf4j
@Aspect
public class LimitAspect {
    /**
     * 不同的接口，不同的流量控制
     * map的key为 Limiter.key
     */
    private final Map<String, LimiterProvider> limitMap = Maps.newConcurrentMap();

    private final RateLimitMappingFactory rateLimitFactory = RateLimitMappingFactory.getInstance();
    private LimitProperties limitProperties;

    public LimitAspect(LimitProperties limitProperties) {
        this.limitProperties = limitProperties;
    }

    @Around("@annotation(com.chua.common.support.task.limit.Limit)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!limitProperties.isEnable()) {
            return joinPoint.proceed();
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        //limit的注解优先
        Limit limit = method.getAnnotation(Limit.class);
        if (limit != null) {
            return doLimit(joinPoint, limit);
        }

        return globalControllerLimit(joinPoint, method);
    }

    /**
     * 全局控制器限制
     *
     * @param joinPoint 连接点
     * @param method    方法
     * @return {@link Object}
     * @throws Throwable 可丢弃
     */
    private Object globalControllerLimit(ProceedingJoinPoint joinPoint, Method method) throws Throwable {
        HttpServletRequest request = RequestUtils.getRequest();
        if(null == request) {
            return joinPoint.proceed();
        }

        Annotation annotation = AnnotationUtils.findAnnotation(method, RequestMapping.class);
        if(null == annotation) {
            return joinPoint.proceed();
        }

        Map<String, Object> annotationAttributes = AnnotationUtils.getAnnotationAttributes(annotation);
        String[] url = MapUtils.getStringArray(annotationAttributes, "value", "path");

        RateLimitResolver resolver = rateLimitFactory.getRateLimitResolver(url);
        if(null == resolver) {
            return joinPoint.proceed();
        }

        if(resolver.resolve(request.getRemoteAddr())) {
            return joinPoint.proceed();
        }

        throw new RuntimeException(ReturnCode.SYSTEM_SERVER_BUSINESS_ERROR.getMsg());
    }

    private Object doLimit(ProceedingJoinPoint joinPoint, Limit limit) throws Throwable {
        String key = limit.key();
        LimiterProvider rateLimiter = null;
        //验证缓存是否有命中key
        if (!limitMap.containsKey(key)) {
            // 创建令牌桶
            rateLimiter = create(limit.value(), limit.type());
            limitMap.put(key, rateLimiter);
            log.info("新建了令牌桶:{}，容量:{}", key, limit.value());
        }

        rateLimiter = limitMap.get(key);
        // 拿令牌
        boolean acquire = rateLimiter.tryAcquire(limit.key(), limit.timeout(), TimeUnit.SECONDS);
        // 拿不到命令，直接返回异常提示
        if (!acquire) {
            if (log.isDebugEnabled()) {
                log.debug("令牌桶:{}，获取令牌失败", key);
            }
            throw new RuntimeException(limit.msg());
        }


        return joinPoint.proceed();
    }

    private LimiterProvider create(double permitsPerSecond, String type) {
        return ServiceProvider.of(LimiterProvider.class).getNewExtension(type, permitsPerSecond);
    }
}

package com.chua.starter.common.support.limit;

import com.chua.common.support.constant.CommonConstant;
import com.chua.common.support.lang.code.ReturnCode;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.task.limit.Limit;
import com.chua.common.support.task.limit.LimiterProvider;
import com.chua.common.support.task.limit.RateLimitMappingFactory;
import com.chua.common.support.task.limit.resolver.RateLimitResolver;
import com.chua.common.support.utils.ArrayUtils;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.common.support.properties.LimitProperties;
import com.chua.starter.common.support.utils.RequestUtils;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
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

    @Around("@annotation(org.springframework.web.bind.annotation.GetMapping) || @annotation(org.springframework.web.bind.annotation.PostMapping) || @annotation(org.springframework.web.bind.annotation.DeleteMapping) || @annotation(org.springframework.web.bind.annotation.PutMapping) || @annotation(org.springframework.web.bind.annotation.RequestMapping) || @annotation(org.springframework.web.bind.annotation.PatchMapping)")
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

        String requestURI = request.getRequestURI();

        RateLimitResolver resolver = rateLimitFactory.getRateLimitResolver(new String[]{requestURI.replace(SpringBeanUtils.getContextPath(), "")});
        if(null == resolver) {
            return joinPoint.proceed();
        }

        if(resolver.resolve(request.getRemoteAddr())) {
            return joinPoint.proceed();
        }

        throw new RuntimeException(ReturnCode.SYSTEM_SERVER_BUSINESS_ERROR.getMsg());
    }

    private String[] resolver(String[] url) {
        String[] rs = new String[url.length ];
        for (int i = 0; i < url.length; i++) {
            String string = url[i];
            rs[i] = resolver(string);
        }

        return rs;
    }

    private String resolver(String string) {
        Environment environment = SpringBeanUtils.getEnvironment();
        return environment.resolvePlaceholders(string);
    }

    private String[] getUrl(Method method) {
        RequestMapping requestMapping = method.getDeclaredAnnotation(RequestMapping.class);
        if(null != requestMapping) {
            return ArrayUtils.defaultIfEmpty(requestMapping.value(), requestMapping.path());
        }
        GetMapping getMapping = method.getDeclaredAnnotation(GetMapping.class);
        if(null != getMapping) {
            return ArrayUtils.defaultIfEmpty(getMapping.value(), getMapping.path());
        }
        PostMapping postMapping = method.getDeclaredAnnotation(PostMapping.class);
        if(null != postMapping) {
            return ArrayUtils.defaultIfEmpty(postMapping.value(), postMapping.path());
        }
        PutMapping putMapping = method.getDeclaredAnnotation(PutMapping.class);
        if(null != putMapping) {
            return ArrayUtils.defaultIfEmpty(putMapping.value(), putMapping.path());
        }
        DeleteMapping deleteMapping = method.getDeclaredAnnotation(DeleteMapping.class);
        if(null != deleteMapping) {
            return ArrayUtils.defaultIfEmpty(deleteMapping.value(), deleteMapping.path());
        }
        PatchMapping patchMapping = method.getDeclaredAnnotation(PatchMapping.class);
        if(null != patchMapping) {
            return ArrayUtils.defaultIfEmpty(patchMapping.value(), patchMapping.path());
        }

        return CommonConstant.EMPTY_ARRAY;
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

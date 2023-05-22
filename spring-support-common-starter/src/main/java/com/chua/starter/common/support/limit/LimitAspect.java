package com.chua.starter.common.support.limit;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.task.limit.Limit;
import com.chua.common.support.task.limit.LimiterProvider;
import com.chua.starter.common.support.properties.LimitProperties;
import com.chua.starter.common.support.result.ResultData;
import com.chua.starter.common.support.result.ReturnCode;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

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
    private LimitProperties limitProperties;

    public LimitAspect(LimitProperties limitProperties) {
        this.limitProperties = limitProperties;
    }

    @Around("@annotation(com.chua.common.support.task.limit.Limit)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        //拿limit的注解
        Limit limit = method.getAnnotation(Limit.class);
        if (limit != null) {
            //key作用：不同的接口，不同的流量控制
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
                log.debug("令牌桶:{}，获取令牌失败", key);
                return this.responseFail(limit.msg());
            }
        }
        return joinPoint.proceed();
    }

    private LimiterProvider create(double permitsPerSecond, String type) {
        return ServiceProvider.of(LimiterProvider.class).getNewExtension(type, permitsPerSecond);
    }

    /**
     * 直接向前端抛出异常
     *
     * @param msg 提示信息
     * @return
     */
    private ResultData<Object> responseFail(String msg) {
        return ResultData.failure(ReturnCode.BUSINESS.getCode(), msg);
    }
}

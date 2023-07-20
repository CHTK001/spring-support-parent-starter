package com.chua.starter.common.support.logger;

import com.chua.starter.common.support.watch.NewTrackManager;
import com.chua.starter.common.support.watch.Span;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;

/**
 * guid
 *
 * @author CH
 */
@Aspect
public class WatchGuidAspect {

    /*** * 切入规则，拦截*Controller.java下所有方法 */
    @Pointcut("execution(* com..*Controller.*(..)) || " +
            "execution(* com..*Service.*(..))|| " +
            "execution(* org..*Executor.*(..))|| " +
            "execution(* com..*ServiceImpl.*(..)) || " +
            "execution(* com..*Mapper.*(..))")
    public void beanAspect() {

    }

    /**
     * 前置通知 记录开始时间
     *
     * @param joinPoint 切点
     * @throws InterruptedException InterruptedException
     */
    @Before("beanAspect()")
    public void doBefore(JoinPoint joinPoint) throws InterruptedException {
        Span entrySpan = NewTrackManager.createEntrySpan();
        Signature signature = joinPoint.getSignature();
        entrySpan.setType(signature.getDeclaringType().getTypeName());
        entrySpan.setTypeMethod(entrySpan.getType());
        entrySpan.setArgs(joinPoint.getArgs());
        if(signature instanceof MethodSignature) {
            entrySpan.setMethod(signature.getName());
            entrySpan.setTypeMethod(entrySpan.getType() + "." + entrySpan.getMethod());
        }
        entrySpan.setEnterTime(System.nanoTime());
    }

    /**
     * 后置通知 返回通知
     *
     * @param res 响应内容
     */
    @AfterReturning(returning = "res", pointcut = "beanAspect()")
    public void doAfterReturning(Object res) throws Throwable {
        Span currentSpan = NewTrackManager.getCurrentSpan();
        currentSpan.setEndTime(System.nanoTime());
    }

}

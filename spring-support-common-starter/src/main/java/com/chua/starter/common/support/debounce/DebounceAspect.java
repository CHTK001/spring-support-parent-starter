package com.chua.starter.common.support.debounce;

import com.chua.common.support.datasource.repository.wrapper.toolkit.WrapperClassUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * DebounceAop类用于实现方法执行的防抖动逻辑。
 * 通过AOP切面编程，对标注了@debounce注解的方法进行拦截，实现防抖功能。
 */
@Aspect
public class DebounceAspect implements ApplicationContextAware {

    private ApplicationContext applicationContext;
    private AutowireCapableBeanFactory autowireCapableBeanFactory;

    /**
     * 对标注了@debounce注解的方法进行拦截，实现防抖功能。
     * 如果同一个方法在指定的时间间隔内被多次调用，则只执行第一次调用的方法。
     *
     * @param pjp ProceedingJoinPoint对象，代表当前执行的方法。
     * @param debounce Debounce注解对象，包含防抖动的配置信息，如间隔时间。
     * @return 返回方法执行的结果；如果在防抖时间内则不执行方法，返回null。
     * @throws Throwable 如果方法执行过程中抛出异常，则抛出该异常。
     */
    @Around("@annotation(debounce)")
    public Object debounce(ProceedingJoinPoint pjp, Debounce debounce) throws Throwable {
        // 获取方法签名
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        DebounceKeyGenerator debounceKeyGenerator = getDebounceKeyGenerator(debounce);
        String key = getKey(debounce, debounceKeyGenerator, signature);
        // 根据方法获取对应的锁，如果不存在则创建一个新的ReentrantLock
        DebounceLock lock = getDebounceLock(debounce);

        try {
            // 尝试获取锁，如果在指定的时间内获取成功，则执行方法
            if (lock.tryLock(key, debounce.value())) {
                return pjp.proceed(); // 执行被拦截的方法
            } else {
                // 在防抖时间内，不执行方法，可根据需要处理此处逻辑
                return null;
            }
        } finally {
            lock.unlock(); // 释放锁
        }
    }

    /**
     * 获取一个特定类型的DebounceLock实例。
     *
     * @param debounce 提供锁类型的Debounce注解实例。
     * @return 返回通过注解指定的类创建的DebounceLock实例。
     */
    private DebounceLock getDebounceLock(Debounce debounce) {
        Class<? extends DebounceLock> aClass = debounce.lock();
        DebounceLock debounceLock = null;
        try {
            debounceLock = WrapperClassUtils.newInstance(aClass);
        } catch (Exception ignored) {
        }
        if(null == debounceLock) {
            return new DefaultDebounceLock();
        }
        autowireCapableBeanFactory.autowireBean(debounceLock);
        return debounceLock;
    }

    /**
     * 根据方法和参数名生成防抖动的键。
     *
     * @param debounce  提供键生成器类型的Debounce注解实例。
     * @param debounceKeyGenerator 用于生成防抖动键的生成器实例。
     * @param signature            方法签名，包含方法和参数信息。
     * @return 返回基于方法和参数名生成的防抖动键。
     */
    private String getKey(Debounce debounce, DebounceKeyGenerator debounceKeyGenerator, MethodSignature signature) {
        return debounceKeyGenerator.getKey(debounce.prefix(), signature.getMethod(), signature.getParameterNames());
    }

    /**
     * 获取一个特定类型的DebounceKeyGenerator实例。
     *
     * @param debounce 提供键生成器类型的Debounce注解实例。
     * @return 返回通过注解指定的类创建的DebounceKeyGenerator实例。
     */
    private DebounceKeyGenerator getDebounceKeyGenerator(Debounce debounce) {
        Class<? extends DebounceKeyGenerator> aClass = debounce.keyGenerator();
        DebounceKeyGenerator debounceKeyGenerator = null;
        try {
            debounceKeyGenerator = WrapperClassUtils.newInstance(aClass);
        } catch (Exception ignored) {
        }
        if(null == debounceKeyGenerator) {
            return new DefaultDebounceKeyGenerator();
        }
        autowireCapableBeanFactory.autowireBean(debounceKeyGenerator);
        return debounceKeyGenerator;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        this.autowireCapableBeanFactory = applicationContext.getAutowireCapableBeanFactory();
    }
}
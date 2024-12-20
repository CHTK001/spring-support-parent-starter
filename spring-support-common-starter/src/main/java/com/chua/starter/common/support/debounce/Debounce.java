package com.chua.starter.common.support.debounce;

import com.chua.common.support.lang.lock.Lock;
import com.chua.common.support.lang.lock.ObjectLock;
import com.chua.starter.common.support.lock.LockFactory;
import kim.nzxy.spel.SpELMethod;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 请求锁定（防抖）
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/10
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Debounce {

    /**
     * 设置默认的防抖时间间隔，单位为秒
     *
     * @return 设置默认的防抖时间间隔，单位为秒
     */
    int value() default 1;


    /**
     * 时间单位
     *
     * @return {@link TimeUnit}
     */
    TimeUnit unit() default TimeUnit.SECONDS;

    /**
     * 前缀
     *
     * @return {@link String}
     */
    String prefix() default "debounce_";

    /**
     * 键
     *
     * @return {@link String}
     */
    @SpELMethod(parameters = true, result = true)
    String key();
    /**
     * 密钥生成器
     *
     * @return {@link Class}<{@link ?} {@link extends} {@link DebounceKeyGenerator}>
     */
    Class<? extends DebounceKeyGenerator> keyGenerator() default DebounceKeyGenerator.class;


    /**
     * 锁类型
     *
     * @return {@link LockFactory.LockType}
     */
    LockFactory.LockType lockType() default LockFactory.LockType.OBJECT;
    /**
     * 锁
     *
     * @return {@link Class}<{@link ?} {@link extends} {@link Lock}>
     */
    Class<? extends Lock> lock() default ObjectLock.class;

    /**
     * 默认异常
     *
     * @return {@link Class}<{@link ?} {@link extends} {@link DebounceException}>
     */
    Class<? extends DebounceException> defaultException() default ReturnResultDebounceException.class;
}

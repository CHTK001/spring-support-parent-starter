package com.chua.starter.common.support.debounce;

import java.lang.annotation.*;

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
     * @return 设置默认的防抖时间间隔，单位为秒
     */
    long value() default 1;


    /**
     * 前缀
     *
     * @return {@link String}
     */
    String prefix() default "debounce_";
    /**
     * 密钥生成器
     *
     * @return {@link Class}<{@link ?} {@link extends} {@link DebounceKeyGenerator}>
     */
    Class<? extends DebounceKeyGenerator> keyGenerator() default DebounceKeyGenerator.class;

    /**
     * 锁
     *
     * @return {@link Class}<{@link ?} {@link extends} {@link DebounceLock}>
     */
    Class<? extends DebounceLock> lock() default DefaultDebounceLock.class;
}

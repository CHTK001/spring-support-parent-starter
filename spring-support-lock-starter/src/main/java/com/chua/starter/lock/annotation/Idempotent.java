package com.chua.starter.lock.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 幂等注解。
 *
 * @author CH
 * @since 2026-03-28
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    String key() default "";

    String prefix() default "";

    long timeout() default -1L;

    TimeUnit timeUnit() default TimeUnit.SECONDS;

    KeyStrategy keyStrategy() default KeyStrategy.SPEL;

    String message() default "请勿重复提交";

    boolean deleteOnSuccess() default false;

    DuplicateStrategy duplicateStrategy() default DuplicateStrategy.EXCEPTION;

    String fallbackMethod() default "";

    enum KeyStrategy {
        SPEL,
        PARAMS_MD5,
        BODY_MD5,
        TOKEN,
        USER_METHOD
    }

    enum DuplicateStrategy {
        EXCEPTION,
        RETURN_NULL,
        RETURN_PREVIOUS,
        FALLBACK
    }
}

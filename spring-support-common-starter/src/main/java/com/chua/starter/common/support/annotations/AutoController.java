package com.chua.starter.common.support.annotations;

import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * 自动控制器注解，用于标记需要自动生成控制器的方法
 *
 * @author CH
 * @since 2025/9/8 10:45
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
@Documented
public @interface AutoController {
}
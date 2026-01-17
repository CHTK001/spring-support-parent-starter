package com.chua.socket.support.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义事件注解
 * 标记方法用于处理自定义事件
 *
 * @author CH
 * @version 1.0.0
 * @since 2024-12-04
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OnEvent {

    /**
     * 事件名称
     *
     * @return 事件名称
     */
    String value();
}

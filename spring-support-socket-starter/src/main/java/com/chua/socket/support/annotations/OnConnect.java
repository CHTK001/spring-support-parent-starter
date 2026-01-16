package com.chua.socket.support.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 连接事件注解
 * 标记方法用于处理客户端连接事件
 *
 * @author CH
 * @version 1.0.0
 * @since 2024-12-04
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OnConnect {

}

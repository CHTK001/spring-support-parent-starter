package com.chua.starter.rsocket.support.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * RSocket连接事件注解
 * <p>
 * 标注在方法上，当有客户端连接时触发该方法
 * 
 * @author CH
 * @version 4.0.0.34
 * @since 2024/10/24
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OnConnect {
}


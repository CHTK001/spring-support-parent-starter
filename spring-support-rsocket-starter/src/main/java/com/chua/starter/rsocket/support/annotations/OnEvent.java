package com.chua.starter.rsocket.support.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * RSocket自定义事件注解
 * <p>
 * 标注在方法上，当接收到指定事件时触发该方法
 * 
 * @author CH
 * @version 4.0.0.34
 * @since 2024/10/24
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


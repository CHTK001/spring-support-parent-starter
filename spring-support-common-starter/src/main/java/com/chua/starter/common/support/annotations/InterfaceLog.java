package com.chua.starter.common.support.annotations;


import java.lang.annotation.*;

/**
 * 接口日志注解
 *
 * @author CH
 */
@Inherited
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface InterfaceLog {

}

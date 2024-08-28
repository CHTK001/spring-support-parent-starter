package com.chua.starter.common.support.annotations;

import com.chua.common.support.constant.Action;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 日志
 *
 * @author CH
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface UserLogger {

    // ========== 模块字段 ==========
    Action action() default Action.NONE;

    /**
     * 登录类型
     */
    String loginType() default "";
    /**
     * 操作模块(e.g.修改, 删除, 更新)
     *
     * 为空时，会尝试读取 {swagger name()} 属性
     */
    String module() default "";
    /**
     * 操作名(xxx查询了)
     *
     * 为空时，会尝试读取 {swagger  summary()} 属性
     */
    String name() default "";
    /**
     * 访问内容(支持spel)
     * {username} 当前用户
     * {$arg1...n} 入参
     * {$method} 方法
     * {$result} 返回值
     */
    String content() default "";

    // ========== 开关字段 ==========

    /**
     * 是否记录操作日志
     */
    boolean enable() default true;
    /**
     * 是否记录方法参数
     */
    boolean logArgs() default true;
    /**
     * 是否记录方法结果的数据
     */
    boolean logResultData() default true;

}

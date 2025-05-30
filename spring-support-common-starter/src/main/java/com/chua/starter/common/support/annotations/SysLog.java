package com.chua.starter.common.support.annotations;


import java.lang.annotation.*;

/**
 * 操作日志注解
 *
 * @author CH
 */
@Inherited
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface SysLog {

    // ========== 模块字段 ==========

    /**
     * 操作模块(e.g.修改, 删除, 更新)
     * <p>
     * 为空时，会尝试读取 {swagger name()} 属性
     */
    String module() default "";

    /**
     * 操作名(xxx查询了)
     * <p>
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

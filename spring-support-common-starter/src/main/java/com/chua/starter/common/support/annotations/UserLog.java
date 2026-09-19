package com.chua.starter.common.support.annotations;

import com.chua.common.support.enums.Action;

import java.lang.annotation.*;

/**
 * 用户操作日志注解
 * <p>
 * 用于记录用户行为日志，包括登录、操作等行为追踪。
 * 与 {@link SysLog} 的区别：用户日志 侧重用户行为追踪（登录类型、浏览器、UA等），
 * sys日志 侧重系统操作审计（接口调用、性能分析）。
 * </p>
 *
 * @see SysLog
 * @author CH
 * @since 4.0.0
 */
@Inherited
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface UserLog {

    // ========== 模块字段 ==========
    
    /**
     * 操作动作类型
     * @return 动作的结果
     */
    Action action() default Action.NONE;

    /**
     * 登录类型（如：密码, sms, OAuth等）
     * @return login类型的结果
     */
    String loginType() default "";
    
    /**
     * 操作模块(e.g.修改, 删除, 更新)
     * <p>
     * 为空时，会尝试读取 {Swagger 名称()} 属性
     * @return module的结果
     */
    String module() default "";
    
    /**
     * 操作名（xxx查询）
     * <p>
     * 为空时，会尝试读取 {Swagger summary()} 属性
     * @return 名称的结果
     */
    String name() default "";
    
    /**
     * 访问内容(支持spel表达式)
     * <ul>
     *   <li>{username} - 当前用户</li>
     *   <li>{$arg0..n} - 方法入参</li>
     *   <li>{$method} - 方法对象</li>
     *   <li>{$result} - 返回值</li>
     * </ul>
     * @return 内容的结果
     */
    String content() default "";

    // ========== 开关字段 ==========

    /**
     * 是否记录操作日志
     * @return enable的结果
     */
    boolean enable() default true;
    
    /**
     * 是否记录方法参数
     * @return 日志参数的结果
     */
    boolean logArgs() default true;
    
    /**
     * 是否记录方法结果的数据
     * @return 日志结果数据的结果
     */
    boolean logResultData() default true;
}

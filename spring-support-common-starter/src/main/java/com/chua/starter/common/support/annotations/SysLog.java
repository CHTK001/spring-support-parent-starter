package com.chua.starter.common.support.annotations;


import java.lang.annotation.*;

/**
* 操作日志注解
*
* @author CH
* @since 4.0.0
 */
@Inherited
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface SysLog {

    // ========== 模块字段 ==========

    /**
    * 操作模块(e.g.修改, 删除, 更新)
    * <p>
    * 为空时，会尝试读取 {Swagger 名称()} 属性
    * @return module的结果
     */
    String module() default "";

    /**
    * 操作描述（xxx查询）
    * <p>
    * 为空时，会尝试读取 {Swagger  summary()} 属性
    * @return 名称的结果
     */
    String name() default "";

    /**
    * 访问内容(支持spel)
    * {用户名} 当前用户
    * {$参数1...n} 入参
    * {$方法} 方法
    * {$结果} 返回值
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


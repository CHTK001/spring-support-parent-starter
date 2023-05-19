package com.chua.shell.support.shell;

import java.lang.annotation.*;

/**
 * 接受参数名称,默认值
 *
 * @author CH
 */
@Target(ElementType.PARAMETER)
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface ShellParam {

    /**
     * 名称
     *
     * @return 名称
     */
    String value();

    /**
     * 例子
     *
     * @return 例子
     */
    String[] example() default {};

    /**
     * 短名称
     *
     * @return 短名称
     */
    String shortName() default "";

    /**
     * 必要参数
     *
     * @return 参数
     */
    boolean required() default false;

    /**
     * 参数数量
     *
     * @return 参数数量
     */
    int numberOfArgs() default 1;

    /**
     * 有值
     *
     * @return 有值
     */
    boolean hasParam() default true;

    /**
     * 描述
     *
     * @return 描述
     */
    String describe() default "";

    /**
     * 默认值
     *
     * @return 默认值
     */
    String defaultValue() default "";

    /**
     * 是否缺省
     *
     * @return 是否缺省
     */
    boolean isDefault() default false;
}

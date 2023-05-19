package com.chua.shell.support.shell;

import java.lang.annotation.*;

/**
 * comman
 *
 * @author CH
 */
@Target(ElementType.METHOD)
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface ShellMapping {
    /**
     * 命令
     *
     * @return 命令
     */
    String[] value() default {};

    /**
     * 是否需要短命令
     *
     * @return 是否需要短命令
     */
    boolean needShort() default true;

    /**
     * 分组
     *
     * @return 分组
     */
    String group() default "Built-In Commands";

    /**
     * 描述
     *
     * @return 描述
     */
    String describe() default "";
}

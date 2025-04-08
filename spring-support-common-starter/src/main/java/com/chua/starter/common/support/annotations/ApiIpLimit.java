package com.chua.starter.common.support.annotations;

import java.lang.annotation.*;

/**
 * api ip限制
 *
 * @author ch
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiIpLimit {

    /**
     * ip
     *
     * @return ip
     */
    String[] value() default {};

    /**
     * 提示信息
     *
     * @return 提示信息
     */
    String message() default "暂无数据";

    /**
     * 类型
     *
     * @return 类型
     */
    IpType type() default IpType.PRIVATE;


    enum IpType {
        /**
         * 公网
         */
        PUBLIC,

        /**
         * 私网(不支持ip匹配)
         */
        PRIVATE
    }
}

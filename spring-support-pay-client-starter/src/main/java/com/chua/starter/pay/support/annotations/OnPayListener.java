package com.chua.starter.pay.support.annotations;

import com.chua.starter.pay.support.pojo.OrderCallbackRequest;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * 监听
 * @author CH
 * @since 2024/12/31
 */
@Documented
@Retention(value = java.lang.annotation.RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OnPayListener {
    /**
     * 监听支付来源
     * @return 监听
     */
    String value();

    /**
     * 监听状态
     * @return 监听
     */
    OrderCallbackRequest.Status status() default OrderCallbackRequest.Status.SUCCESS;
}

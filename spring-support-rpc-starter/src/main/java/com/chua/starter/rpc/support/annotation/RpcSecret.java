package com.chua.starter.rpc.support.annotation;

import java.lang.annotation.*;

/**
 * 加密
 *
 * @author CH
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE})
public @interface RpcSecret {
    /**
     * bean
     *
     * @return bean
     */
    String value();
}

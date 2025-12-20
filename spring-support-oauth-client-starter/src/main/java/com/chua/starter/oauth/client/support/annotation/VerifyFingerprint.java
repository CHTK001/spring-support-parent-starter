package com.chua.starter.oauth.client.support.annotation;

import java.lang.annotation.*;

/**
 * 浏览器指纹验证注解
 * <p>
 * 标记在Controller方法或类上，表示该接口需要验证浏览器指纹。
 * 只有标记了此注解的接口才会进行指纹验证。
 * </p>
 *
 * @author CH
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface VerifyFingerprint {
}

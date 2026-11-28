package com.chua.starter.common.support.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 令牌忽略注解。
 *
 * <p>用于标记 Controller 类或方法，认证过滤器在执行时跳过令牌校验。
 * 注解属于平台 API 契约，放在 common-starter；具体认证实现由宿主按需装配。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TokenForIgnore {
}

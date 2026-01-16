package com.chua.starter.oauth.client.support.annotation;

import java.lang.annotation.*;

/**
 * Token忽略注解
 * <p>用于标记Controller类或方法，被标记的类或方法在执行时将忽略Token验证</p>
 *
 * @author CH
 * @since 2023-08-01
 * 
 * @see org.springframework.web.bind.annotation.RequestParam
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TokenForIgnore {

}

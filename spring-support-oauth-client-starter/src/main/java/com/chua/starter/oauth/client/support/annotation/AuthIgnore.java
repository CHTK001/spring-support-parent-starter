package com.chua.starter.oauth.client.support.annotation;


import java.lang.annotation.*;

/**
 * 解析参数 (Header: application/json)
 *
 * @author CH
 * @see org.springframework.web.bind.annotation.RequestParam
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuthIgnore {

}

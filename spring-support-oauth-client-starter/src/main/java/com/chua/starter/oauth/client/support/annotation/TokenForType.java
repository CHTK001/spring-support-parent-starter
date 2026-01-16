package com.chua.starter.oauth.client.support.annotation;


import com.chua.starter.oauth.client.support.enums.AuthType;

import java.lang.annotation.*;

/**
 * 解析参数 (Header: application/json)
 *
 * @author CH
 * @since 2023-08-09
 * @see org.springframework.web.bind.annotation.RequestParam
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TokenForType {

    /**
     * 认证类型数组
     * 默认值为空，表示匹配所有认证类型
     * 示例: @TokenForType(value = {AuthType.WEB, AuthType.TOKEN})
     *
     * @return 认证类型数组
     */
    AuthType[] value() default {};
    
    /**
     * 分组信息数组
     * 用于指定该注解适用的分组
     * 示例: @TokenForType(group = {"admin", "user"})
     *
     * @return 分组信息数组
     */
    String[] group() default {};
}

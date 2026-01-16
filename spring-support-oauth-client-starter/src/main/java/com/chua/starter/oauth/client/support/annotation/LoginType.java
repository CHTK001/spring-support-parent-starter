package com.chua.starter.oauth.client.support.annotation;

import com.chua.starter.oauth.client.support.enums.AuthType;

import java.lang.annotation.*;

/**
 * 登录类型注解，用于标识不同的登录方式
 *
 * @author CH
 * @since 2022/7/29 8:23
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LoginType {
    /**
     * 登录类型值
     *
     * @return 登录类型，例如："oauth2"表示OAuth2登录，"ldap"表示LDAP登录，"local"表示本地登录
     */
    String value();

    /**
     * 登录类型值
     *
     * @return 登录类型，例如："oauth2"表示OAuth2登录，"ldap"表示LDAP登录，"local"表示本地登录
     */
    AuthType group() default AuthType.NONE;
}

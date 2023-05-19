package com.chua.starter.oauth.client.support.contants;

/**
 * 鉴权常量
 *
 * @author CH
 */
public interface AuthConstant {
    /**
     * ak
     */
    String ACCESS_KEY = "x-access-key";
    /**
     * sk
     */
    String SECRET_KEY = "x-secret-key";
    /**
     * ok
     */
    String OAUTH_KEY = "x-oauth-key";
    /**
     * ov
     */
    String OAUTH_VALUE = "x-oauth-value";

    /**
     * 鉴权
     */
    String OAUTH = "oauth";

    /**
     * 前缀
     */
    String TOKEN_PRE = "oauth:token:";
    /**
     * 令牌前缀
     */
    String PRE_KEY = "oauth:key:";
}

package com.chua.starter.oauth.client.support.protocol;

import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import jakarta.servlet.http.Cookie;

/**
 * @author CH
 */
public interface Protocol {

    /**
     * 认证
     *
     * @param cookie cookie
     * @param token  token
     * @return 认证
     */
    AuthenticationInformation approve(Cookie[] cookie, String token);

    /**
     * 刷新token
     *
     * @param cookie cookie
     * @param token  token
     */
    void refreshToken(Cookie[] cookie, String token);
}

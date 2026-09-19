package com.chua.starter.oauth.client.support.user;

import lombok.*;


/**
 * 登录结果
 *
 * @author CH
 * @since 4.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResult {

    /**
     * 令牌信息
     */
    private String token;

    /**
     * 刷新令牌
     */
    private String refreshToken;

    /**
     * 浏览器指纹
     * <p>登录时记录的浏览器指纹</p>
     */
    private String fingerprint;

    /**
     * 用户信息
     */
    private UserResume userResume;
}

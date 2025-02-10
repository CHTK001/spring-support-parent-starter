package com.chua.starter.oauth.client.support.user;

import lombok.*;


/**
 * 登录结果
 *
 * @author CH
 */
@Data
@NoArgsConstructor
@RequiredArgsConstructor
@AllArgsConstructor
public class LoginResult {

    /**
     * token信息
     */
    @NonNull
    private String token;

    /**
     * 刷新token
     */
    private String refreshToken;
    /**
     * 用户信息
     */
    private UserResult userResult;
}

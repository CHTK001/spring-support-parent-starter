package com.chua.starter.oauth.client.support.provider;

import lombok.Builder;
import lombok.Data;
/** @author CH */

@Data
@Builder
public class LoginResult {

    /** Access令牌 */
    private String accessToken;

    /** 令牌类型 */
    private String tokenType;

    /** Refresh令牌 */
    private String refreshToken;
    /** 用户信息 */
    private UserInfoVO userInfo;
    /** Expires */
    private Long expires;

}


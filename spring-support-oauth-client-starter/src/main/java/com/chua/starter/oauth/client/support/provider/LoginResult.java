package com.chua.starter.oauth.client.support.provider;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResult {

    private String accessToken;

    private String tokenType;

    private String refreshToken;
    private UserInfoVO userInfo;
    private Long expires;

}

package com.chua.starter.oauth.client.support.protocol;

import com.chua.common.support.annotations.Extension;
import com.chua.common.support.crypto.Codec;
import com.chua.common.support.json.Json;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.oauth.client.support.execute.AuthClientExecute;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.infomation.Information;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.google.common.collect.Sets;

import javax.servlet.http.Cookie;
import java.util.Collections;

import static com.chua.starter.oauth.client.support.execute.AuthClientExecute.DEFAULT_KEY;

@Extension("Static")
public class StaticProtocol extends AbstractProtocol{

    private final Codec AES = Codec.build("AES", DEFAULT_KEY);

    @Override
    public AuthenticationInformation approve(Cookie[] cookie, String token) {
        return newAuthenticationInformation(token, cookie);
    }


    /**
     * 新身份验证信息
     *
     * @return {@link AuthenticationInformation}
     */
    private AuthenticationInformation newAuthenticationInformation(String token, Cookie[] cookie) {
        UserResume userResume = new UserResume();
        UserResult userResult = AuthClientExecute.getInstance().getUserResult(token);

        if(null == userResult && null != cookie) {
            for (Cookie cookie1 : cookie) {
                if(!"JSESSIONID".equals(cookie1.getName())) {
                    userResult = Json.fromJson(AES.decodeHex(token), UserResult.class);
                    if(null != userResult) {
                        break;
                    }
                }
            }
        }

        if(null != userResult) {
            userResume.setUsername(userResult.getUsername());
            RequestUtils.setUsername(userResume.getUsername());
        }

        userResume.setRoles(Sets.newHashSet("OPS"));
        userResume.setPermission(Collections.emptySet());
        RequestUtils.setUserInfo(userResume);
        return new AuthenticationInformation(Information.OK, userResume);
    }
    @Override
    public void refreshToken(Cookie[] cookie, String token) {

    }
}

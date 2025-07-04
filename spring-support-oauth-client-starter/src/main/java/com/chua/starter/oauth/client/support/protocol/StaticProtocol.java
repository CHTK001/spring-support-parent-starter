package com.chua.starter.oauth.client.support.protocol;

import com.chua.common.support.annotations.Extension;
import com.chua.common.support.crypto.Codec;
import com.chua.common.support.json.Json;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.oauth.client.support.enums.AuthType;
import com.chua.starter.oauth.client.support.enums.LogoutType;
import com.chua.starter.oauth.client.support.enums.UpgradeType;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.infomation.Information;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.LoginResult;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.google.common.collect.Sets;
import io.micrometer.core.instrument.util.StringUtils;
import jakarta.servlet.http.Cookie;

import java.util.Collections;
import java.util.Map;

import static com.chua.common.support.constant.NameConstant.NULL;
import static com.chua.starter.common.support.constant.Constant.ADMIN;
import static com.chua.starter.common.support.constant.Constant.OPS;
import static com.chua.starter.oauth.client.support.execute.AuthClientExecute.DEFAULT_KEY;

@Extension("Static")
public class StaticProtocol extends AbstractProtocol {
    private static volatile Codec SM4;

    public StaticProtocol(AuthClientProperties authClientProperties) {
        super(authClientProperties);
    }

    @Override
    protected AuthenticationInformation upgradeInformation(Cookie cookie, String token, String subProtocol) {
        initial();
        return newAuthenticationInformation(token, new Cookie[]{cookie});
    }

    @Override
    protected AuthenticationInformation upgradeInformation(Cookie cookie, String token, UpgradeType upgradeType, String refreshToken) {
        initial();
        return newAuthenticationInformation(token, new Cookie[]{cookie});
    }

    private void initial() {
        if (null == SM4) {
            synchronized (this) {
                if (null == SM4) {
                    SM4 = Codec.build("SM4", DEFAULT_KEY);
                }
            }
        }
    }


    /**
     * 新身份验证信息
     *
     * @return {@link AuthenticationInformation}
     */
    private AuthenticationInformation newAuthenticationInformation(String token, Cookie[] cookie) {
        UserResume userResume = new UserResume();
        UserResult userResult = null;
        if (StringUtils.isNotBlank(token) && !NULL.equals(token)) {
            userResult = Json.fromJson(SM4.decodeHex(token), UserResult.class);
        }

        if (null == userResult && null != cookie) {
            for (Cookie cookie1 : cookie) {
                if (!"JSESSIONID".equals(cookie1.getName())) {
                    String source;
                    try {
                        source = SM4.decodeHex(cookie1.getValue());
                        userResult = Json.fromJson(source, UserResult.class);
                    } catch (Exception e) {
                    }
                    if (null != userResult) {
                        break;
                    }
                }
            }
        }

        if (null != userResult) {
            userResume.setUsername(userResult.getUsername());
            RequestUtils.setUsername(userResume.getUsername());
        }

        if (ADMIN.equalsIgnoreCase(userResume.getUsername())) {
            userResume.setRoles(Sets.newHashSet(ADMIN));
        } else {
            userResume.setRoles(Sets.newHashSet(OPS));
        }
        userResume.setPermission(Collections.emptySet());
        RequestUtils.setUserInfo(userResume);
        return new AuthenticationInformation(Information.OK, userResume);
    }

    @Override
    public LoginResult upgrade(Cookie[] cookie, String token, UpgradeType upgradeType, String refreshToken) {
        return new LoginAuthResult(0, null);
    }

    @Override
    public LoginAuthResult getAccessToken(String username, String password, AuthType authType, Map<String, Object> ext) {
        return null;
    }

    @Override
    public LoginAuthResult logout(String uid, LogoutType logoutType, UserResult userResult) {
        return null;
    }
}

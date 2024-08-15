package com.chua.starter.oauth.server.support.parser;

import com.chua.common.support.json.Json;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.code.ReturnResultBuilder;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.common.support.utils.CookieUtil;
import com.chua.starter.oauth.client.support.enums.UpgradeType;
import com.chua.starter.oauth.client.support.user.LoginResult;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.chua.starter.oauth.server.support.check.LoginCheck;
import com.chua.starter.oauth.server.support.information.AuthInformation;
import com.chua.starter.oauth.server.support.token.TokenResolver;
import com.google.common.base.Strings;
import jakarta.servlet.http.Cookie;
import org.springframework.context.ApplicationContext;

import static com.chua.common.support.lang.code.ReturnCode.OK;

/**
 * 鉴权信息
 *
 * @author CH
 */
public final class RequestAuthorization implements Authorization {
    private final AuthInformation authInformation;
    private final String token;
    private final Cookie[] cookies;
    private final String accessKey;
    private final String secretKey;
    private final UpgradeType upgradeType;

    final ApplicationContext applicationContext;
    final LoginCheck loginCheck;

    public RequestAuthorization(AuthInformation authInformation,
                                String token,
                                Cookie[] cookies,
                                String accessKey,
                                String secretKey,
                                UpgradeType upgradeType,
                                ApplicationContext applicationContext,
                                LoginCheck loginCheck) {
        this.authInformation = authInformation;
        this.token = token;
        this.cookies = cookies;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.upgradeType = upgradeType;
        this.applicationContext = applicationContext;
        this.loginCheck = loginCheck;
    }

    @Override
    public boolean hasCookie() {
        return cookies.length != 0;
    }

    @Override
    public boolean hasToken() {
        return !Strings.isNullOrEmpty(token);
    }

    @Override
    public boolean hasKey() {
        return !Strings.isNullOrEmpty(accessKey) && !Strings.isNullOrEmpty(secretKey);
    }

    @Override
    public boolean check() {
        return true;
    }

    @Override
    public ReturnResult<String> authentication() {
        String tokenManagement = authInformation.getAuthServerProperties().getTokenManagement();
        TokenResolver tokenResolver = ServiceProvider.of(TokenResolver.class).getExtension(tokenManagement);
        if (null == tokenResolver) {
            return ReturnResult.error(null, "认证服务器无法认证");
        }
        ReturnResult<UserResult> resolve = tokenResolver.resolve(cookies, token);
        ReturnResultBuilder<String> result = ReturnResult.<String>newBuilder().code(resolve.getCode()).msg(resolve.getMsg());
        if (OK.getCode().equals(resolve.getCode())) {
            result.setData(authInformation.getCodec().encodeHex(Json.toJson(resolve.getData())));
        }
        return result.build();
    }

    @Override
    public ReturnResult<String> upgrade(String address, String cookieName) {
        String tokenManagement = authInformation.getAuthServerProperties().getTokenManagement();
        TokenResolver tokenResolver = ServiceProvider.of(TokenResolver.class).getExtension(tokenManagement);
        if (null == tokenResolver) {
            return ReturnResult.error(null, "认证服务器无法认证");
        }

        LoginResult loginResult = new LoginResult();
        ReturnResult<UserResult> resolve = null;
        if(upgradeType == UpgradeType.TIMESTAMP) {
            resolve = tokenResolver.upgradeForTimestamp(cookies, token);
            loginResult.setToken(getValidToken(cookieName));
            loginResult.setUserResult(resolve.getData());
        }

         else if(upgradeType == UpgradeType.VERSION) {
            resolve = tokenResolver.upgradeForVersion(cookies, token);
            loginResult.setToken(getValidToken(cookieName));
            loginResult.setUserResult(resolve.getData());
        }

         else if(upgradeType == UpgradeType.REFRESH) {
            resolve = tokenResolver.upgradeForVersion(cookies, token);

            ReturnResult<LoginResult> token = tokenResolver.createToken(address, resolve.getData(), null);
            String code = token.getCode();
            if (OK.getCode().equals(code)) {
                tokenResolver.logout(cookies, this.token, cookieName);
                loginResult = token.getData();
                loginResult.setUserResult(resolve.getData());
            }
        }

        ReturnResultBuilder<String> result = ReturnResult.<String>newBuilder().code(resolve.getCode()).msg(resolve.getMsg());
        if (OK.getCode().equals(resolve.getCode())) {
            result.setData(authInformation.getCodec().encodeHex(Json.toJson(loginResult)));
        }
        return result.build();
    }

    /**
     * 获取有效的token
     *
     * @param cookieName cookieName
     * @return token
     */
    private String getValidToken(String cookieName) {
        Cookie cookie = CookieUtil.getCookie(cookies, cookieName);
        if(null != cookie) {
            return cookie.getValue();
        }

        return token;
    }
}

package com.chua.starter.oauth.server.support.parser;

import com.chua.common.support.json.Json;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.common.support.result.ReturnResultBuilder;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.chua.starter.oauth.server.support.information.AuthInformation;
import com.chua.starter.oauth.server.support.token.TokenResolver;
import com.google.common.base.Strings;
import org.springframework.context.ApplicationContext;

import javax.annotation.Resource;
import javax.servlet.http.Cookie;

/**
 * 鉴权信息
 *
 * @author CH
 */
public final class RequestAuthorization implements Authorization {
    private final AuthInformation authInformation;
    private final String token;
    private final Cookie[] cookie;
    private final String accessKey;
    private final String secretKey;

    @Resource
    private ApplicationContext applicationContext;

    public RequestAuthorization(AuthInformation authInformation, String token, Cookie[] cookie, String accessKey, String secretKey) {
        this.authInformation = authInformation;
        this.token = token;
        this.cookie = cookie;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
    }

    @Override
    public boolean hasCookie() {
        return cookie.length != 0;
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
        ReturnResult<UserResult> resolve = tokenResolver.resolve(cookie, token);
        ReturnResultBuilder<String> result = ReturnResult.<String>newBuilder().code(resolve.getCode()).msg(resolve.getMsg());
        if (resolve.getCode() == 200) {
            result.setData(authInformation.getEncode().encodeHex(Json.toJson(resolve.getData()), authInformation.getOauthKey()));
        }
        return result.build();
    }
}

package com.chua.starter.oauth.server.support.parser;

import com.chua.common.support.json.Json;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.code.ReturnResultBuilder;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.oauth.client.support.enums.UpgradeType;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.chua.starter.oauth.server.support.check.LoginCheck;
import com.chua.starter.oauth.server.support.information.AuthInformation;
import com.chua.starter.oauth.server.support.token.TokenResolver;
import com.google.common.base.Strings;
import jakarta.annotation.Resource;
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
    private final Cookie[] cookie;
    private final String accessKey;
    private final String secretKey;
    private final UpgradeType upgradeType;

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private LoginCheck loginCheck;

    public RequestAuthorization(AuthInformation authInformation,
                                String token,
                                Cookie[] cookie,
                                String accessKey,
                                String secretKey,
                                UpgradeType upgradeType) {
        this.authInformation = authInformation;
        this.token = token;
        this.cookie = cookie;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.upgradeType = upgradeType;
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
        if (OK.getCode().equals(resolve.getCode())) {
            result.setData(authInformation.getCodec().encodeHex(Json.toJson(resolve.getData())));
        }
        return result.build();
    }

    @Override
    public ReturnResult<String> upgrade() {
        String tokenManagement = authInformation.getAuthServerProperties().getTokenManagement();
        TokenResolver tokenResolver = ServiceProvider.of(TokenResolver.class).getExtension(tokenManagement);
        if (null == tokenResolver) {
            return ReturnResult.error(null, "认证服务器无法认证");
        }

        ReturnResult<UserResult> resolve = null;
        if(upgradeType == UpgradeType.TIMESTAMP) {
            resolve = tokenResolver.upgradeForTimestamp(cookie, token);
        }

         else if(upgradeType == UpgradeType.VERSION) {
            resolve = tokenResolver.upgradeForVersion(cookie, token);
        }

        ReturnResultBuilder<String> result = ReturnResult.<String>newBuilder().code(resolve.getCode()).msg(resolve.getMsg());
        if (OK.getCode().equals(resolve.getCode())) {
            result.setData(authInformation.getCodec().encodeHex(Json.toJson(resolve.getData())));
        }
        return result.build();
    }
}

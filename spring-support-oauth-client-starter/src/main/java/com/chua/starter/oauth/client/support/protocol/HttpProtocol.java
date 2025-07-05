package com.chua.starter.oauth.client.support.protocol;

import com.chua.common.support.annotations.Extension;
import com.chua.common.support.annotations.SpiDefault;
import com.chua.common.support.json.Json;
import com.chua.common.support.json.JsonObject;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.IdUtils;
import com.chua.common.support.utils.SignUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.oauth.client.support.enums.AuthType;
import com.chua.starter.oauth.client.support.enums.LogoutType;
import com.chua.starter.oauth.client.support.enums.UpgradeType;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.infomation.Information;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.google.common.base.Strings;
import jakarta.servlet.http.Cookie;
import kong.unirest.HttpRequestWithBody;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import lombok.extern.slf4j.Slf4j;
import me.zhyd.oauth.exception.AuthException;
import me.zhyd.oauth.log.Log;

import java.util.Map;
import java.util.UUID;

import static com.chua.common.support.http.HttpClientUtils.APPLICATION_JSON;

/**
 * http
 *
 * @author CH
 */
@SpiDefault
@Extension("http")
@Slf4j
public class HttpProtocol extends AbstractProtocol {


    public HttpProtocol(AuthClientProperties authClientProperties) {
        super(authClientProperties);
    }

    @Override
    protected AuthenticationInformation upgradeInformation(Cookie cookie, String token, String subProtocol) {
        JsonObject jsonObject = new JsonObject();
        // 构建认证数据
        jsonObject.put("x-oauth-cookie", cookie.getValue());
        jsonObject.put("x-oauth-token", token);
        jsonObject.put("x-oauth-access-key", authClientProperties.getKey().getAccessKey());
        jsonObject.put("x-oauth-secret-key", authClientProperties.getKey().getSecretKey());
        jsonObject.put("x-oauth-sub-protocol", StringUtils.defaultString(subProtocol, "DEFAULT").toUpperCase());
        return createAuthenticationInformation(jsonObject, null, authClientProperties.getOauthUrl());
    }

    @Override
    protected AuthenticationInformation upgradeInformation(Cookie cookie, String token, UpgradeType upgradeType, String refreshToken) {
        JsonObject jsonObject = new JsonObject();
        // 构建认证数据
        jsonObject.put("x-oauth-cookie", cookie.getValue());
        jsonObject.put("x-oauth-token", token);
        jsonObject.put("x-oauth-access-key", authClientProperties.getKey().getAccessKey());
        jsonObject.put("x-oauth-secret-key", authClientProperties.getKey().getSecretKey());
        jsonObject.put("x-oauth-refresh-token", refreshToken);
        return createAuthenticationInformation(jsonObject, upgradeType, "upgrade");
    }

    @Override
    public LoginAuthResult getAccessToken(String username, String password, AuthType authType, Map<String, Object> ext) {
        JsonObject jsonObject = new JsonObject();
        // 构建认证数据
        jsonObject.put("x-oauth-access-key", authClientProperties.getKey().getAccessKey());
        jsonObject.put("x-oauth-secret-key", authClientProperties.getKey().getSecretKey());
        jsonObject.put("x-oauth-username", username);
        jsonObject.put("x-oauth-password", password);
        jsonObject.put("x-oauth-auth-type", authType);
        jsonObject.put("x-oauth-ext", ext);
        AuthenticationInformation information = createAuthenticationInformation(jsonObject, null, authClientProperties.getLoginPage());
        LoginAuthResult loginAuthResult = new LoginAuthResult();
        loginAuthResult.setCode(information.getInformation().getCode());
        loginAuthResult.setMessage(information.getInformation().getMessage());
        loginAuthResult.setUserResume(information.getReturnResult());
        loginAuthResult.setToken(information.getToken());
        loginAuthResult.setRefreshToken(information.getRefreshToken());
        return loginAuthResult;
    }

    @Override
    public LoginAuthResult logout(String uid, LogoutType logoutType, UserResult userResult) {
        JsonObject jsonObject = new JsonObject();
        // 构建认证数据
        jsonObject.put("x-oauth-access-key", authClientProperties.getKey().getAccessKey());
        jsonObject.put("x-oauth-secret-key", authClientProperties.getKey().getSecretKey());
        jsonObject.put("x-oauth-uid", uid);
        jsonObject.put("x-oauth-logout-type", logoutType);
        AuthenticationInformation information = createAuthenticationInformation(jsonObject, null, authClientProperties.getLogoutPage());
        if(information.getInformation() == Information.OK) {
            return LoginAuthResult.OK;
        }
        throw new AuthException(information.getInformation().getMessage());
    }


    /**
     * 创建认证信息
     * 向远程认证服务器发送HTTP请求以验证用户凭据或升级令牌
     *
     * @param jsonObject  包含认证所需参数的JSON对象
     * @param upgradeType 升级类型，用于指定升级策略（如基于版本号或时间戳）
     * @return AuthenticationInformation 返回认证结果，包含认证状态和用户信息
     */
    protected AuthenticationInformation createAuthenticationInformation(JsonObject jsonObject, UpgradeType upgradeType, String path) {
        // 获取认证服务器地址
        String selectedUrl = selectUrl();
        if (null == selectedUrl) {
            return AuthenticationInformation.authServerError();
        }

        // 生成随机密钥和请求头参数
        String key =  IdUtils.simpleUuid();
        jsonObject.put("x-ext-request-key", UUID.randomUUID().toString());
        jsonObject.put("x-ext-timestamp", System.currentTimeMillis());

        String timestamp = System.nanoTime() + "";
        String key1 = IdUtils.simpleUuid();

        HttpResponse<String> httpResponse = null;
        try {
            // 构建POST请求
            HttpRequestWithBody withBody = Unirest.post(
                    StringUtils.endWithAppend(StringUtils.startWithAppend(selectedUrl, "http://"), "/") +
                            StringUtils.startWithMove(path, "/"));

            // 设置基础请求头
            HttpRequestWithBody requestWithBody = withBody
                    .header("x-oauth-timestamp", timestamp)
                    .header("x-oauth-uuid", key1)
                    .header("x-oauth-encode", String.valueOf(isEncode()))
                    .header("x-oauth-serial", createData(key, key1))
                    .header("x-oauth-sign", SignUtils.generateSignFromMap(jsonObject));

            // 如果有升级类型，则添加相应请求头
            if (null != upgradeType) {
                requestWithBody = requestWithBody.header("x-oauth-upgrade-type", upgradeType.name());
            }

            // 发送请求并获取响应
            httpResponse = requestWithBody.contentType(APPLICATION_JSON)
                    .body(JsonObject.create()
                            .fluentPut("data", createData(jsonObject, key)).toJSONString())
                    .asString();

        } catch (UnirestException ignored) {
            // 请求异常处理
            log.error("Unirest请求异常：{}", ignored.getMessage());
        }

        // 检查响应是否为空
        if (null == httpResponse) {
            return AuthenticationInformation.authServerError();
        }

        // 获取响应状态码和内容
        int status = httpResponse.getStatus();
        String body = httpResponse.getBody();

        // 判断响应是否为服务器错误或空数据
        if (status > 400 && status < 600 || Strings.isNullOrEmpty(body)) {
            return AuthenticationInformation.authServerNotFound();
        }

        // 成功响应时解析返回结果
        if (status == 200) {
            return createAuthenticationInformation(Json.fromJson(body, ReturnResult.class),
                    httpResponse.getHeaders().getFirst("x-oauth-response-serial"),
                    path);
        }

        // 默认返回服务器错误
        return AuthenticationInformation.authServerError();
    }

}

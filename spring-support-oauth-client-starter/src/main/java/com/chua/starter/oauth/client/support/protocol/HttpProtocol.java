package com.chua.starter.oauth.client.support.protocol;

import com.chua.common.support.annotations.Extension;
import com.chua.common.support.annotations.SpiDefault;
import com.chua.common.support.json.Json;
import com.chua.common.support.json.JsonObject;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.oauth.client.support.enums.UpgradeType;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.user.LoginResult;
import com.google.common.base.Strings;
import jakarta.servlet.http.Cookie;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import lombok.extern.slf4j.Slf4j;

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
    protected AuthenticationInformation getAuthenticationInformation(String cacheKey, Cookie[] cookies, String token) {
        String selectedUrl = selectUrl();
        if (null == selectedUrl) {
            return AuthenticationInformation.authServerError();
        }

        JsonObject jsonObject = new JsonObject();
        // 构建认证数据
        jsonObject.put("x-oauth-cookie", cookies);
        jsonObject.put("x-oauth-token", token);
        jsonObject.put("x-oauth-access-key", authClientProperties.getAccessKey());
        jsonObject.put("x-oauth-secret-key", authClientProperties.getSecretKey());
        jsonObject.put("x-oauth-client-ip", authClientProperties.getClientIp());

        String key = UUID.randomUUID().toString();
        jsonObject.put("x-ext-service-serial", authClientProperties.getServiceKey());
        jsonObject.put("x-ext-request-key", UUID.randomUUID().toString());
        jsonObject.put("x-ext-timestamp", System.currentTimeMillis());

        HttpResponse<String> httpResponse = null;
        try {
            httpResponse = Unirest.post(
                            StringUtils.endWithAppend(StringUtils.startWithAppend(selectedUrl, "http://"), "/") + StringUtils.startWithMove(authClientProperties.getOauthUrl(), "/"))
                    .header("x-oauth-timestamp", System.currentTimeMillis() + "")
                    .header("x-oauth-serial", key)
                    .contentType(APPLICATION_JSON)
                    .body(JsonObject.create()
                            .fluentPut("data", createData(jsonObject, key)).toJSONString())
                    .asString();

        } catch (UnirestException ignored) {
        }

        if (null == httpResponse) {
            return AuthenticationInformation.authServerError();
        }
        int status = httpResponse.getStatus();
        String body = httpResponse.getBody();
        if (status > 400 && status < 600 || Strings.isNullOrEmpty(body)) {
            return AuthenticationInformation.authServerNotFound();
        }

        if (status == 200) {
            return createAuthenticationInformation(Json.fromJson(body, ReturnResult.class),
                    httpResponse.getHeaders().getFirst("x-oauth-response-serial")
            );
        }
        return AuthenticationInformation.authServerError();
    }



    @Override
    public LoginResult upgrade(Cookie[] cookies, String token, UpgradeType upgradeType, String refreshToken) {
        String selectedUrl = selectUrl();
        if (null == selectedUrl) {
            throw new IllegalArgumentException("OSS服务器不存在");
        }

        JsonObject jsonObject = new JsonObject();
        // 构建认证数据
        jsonObject.put("x-oauth-cookie", cookies);
        jsonObject.put("x-oauth-token", token);
        jsonObject.put("x-oauth-access-key", authClientProperties.getAccessKey());
        jsonObject.put("x-oauth-secret-key", authClientProperties.getSecretKey());
        jsonObject.put("x-oauth-client-ip", authClientProperties.getClientIp());

        String key = UUID.randomUUID().toString();
        jsonObject.put("x-ext-service-serial", authClientProperties.getServiceKey());
        jsonObject.put("x-ext-request-key", UUID.randomUUID().toString());
        jsonObject.put("x-ext-timestamp", System.currentTimeMillis());

        HttpResponse<String> httpResponse = null;
        try {
            httpResponse = Unirest.post(
                            StringUtils.endWithAppend(StringUtils.startWithAppend(selectedUrl, "http://"), "/") + "upgrade")
                    .header("x-oauth-timestamp", System.currentTimeMillis() + "")
                    .header("x-oauth-serial", key)
                    .header("x-oauth-upgrade-type", upgradeType.name())
                    .contentType(APPLICATION_JSON)
                    .body(JsonObject.create()
                            .fluentPut("data", createData(jsonObject, key)).toJSONString())
                    .asString();

        } catch (UnirestException ignored) {
        }

        if (null == httpResponse) {
            throw new IllegalArgumentException("OSS服务器不存在");
        }
        int status = httpResponse.getStatus();
        String body = httpResponse.getBody();
        if (status > 400 && status < 600 || Strings.isNullOrEmpty(body)) {
            throw new IllegalArgumentException("OSS服务器不存在");
        }

        if (status == 200) {
            return createUpgradeResponse(Json.fromJson(body, ReturnResult.class),
                    httpResponse.getHeaders().getFirst("x-oauth-response-serial")
            );
        }
        throw new IllegalArgumentException("OSS服务器不存在");
    }


}

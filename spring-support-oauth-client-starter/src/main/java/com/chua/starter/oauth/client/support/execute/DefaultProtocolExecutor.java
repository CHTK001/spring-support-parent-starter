package com.chua.starter.oauth.client.support.execute;

import com.chua.common.support.annotations.SpiDefault;
import com.chua.common.support.crypto.Codec;
import com.chua.common.support.json.Json;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.robin.Node;
import com.chua.common.support.lang.robin.Robin;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.DigestUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.application.Binder;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.oauth.client.support.advice.def.DefSecret;
import com.chua.starter.oauth.client.support.contants.AuthConstant;
import com.chua.starter.oauth.client.support.entity.AuthRequest;
import com.chua.starter.oauth.client.support.enums.AuthType;
import com.chua.starter.oauth.client.support.enums.LogoutType;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.google.common.base.Strings;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static com.chua.common.support.constant.NumberConstant.NUM_200;
import static com.chua.common.support.http.HttpClientUtils.APPLICATION_JSON;
import static com.chua.common.support.lang.code.ReturnCode.OK;
import static com.chua.starter.oauth.client.support.contants.AuthConstant.ACCESS_KEY;
import static com.chua.starter.oauth.client.support.contants.AuthConstant.SECRET_KEY;

/**
 * 实现了ProtocolExecutor接口的StaticProtocolExecutor类。
 * <p>
 * 该类旨在处理特定的协议执行逻辑，通过静态方法提供协议处理能力。
 * 作为协议执行器，它负责解析和执行特定格式的协议数据。
 * </p>
 * @author CH
 * @since 2024/6/12
 */
@SpiDefault
public class DefaultProtocolExecutor implements ProtocolExecutor{

    private final AuthClientProperties authClientProperties;
    private final String encryption;

    public DefaultProtocolExecutor() {
        this.authClientProperties = Binder.binder(AuthClientProperties.PRE, AuthClientProperties.class);
        this.encryption = authClientProperties.getEncryption();
    }

    @Override
    public LoginAuthResult getAccessToken(String username, String password, AuthType authType, Map<String, Object> ext) {
        String accessKey = authClientProperties.getAccessKey();
        String secretKey = authClientProperties.getSecretKey();
        String serviceKey = authClientProperties.getServiceKey();
        String key = UUID.randomUUID().toString();

        if (Strings.isNullOrEmpty(accessKey) || Strings.isNullOrEmpty(secretKey)) {
            accessKey = DefSecret.ACCESS_KEY;
            secretKey = DefSecret.SECRET_KEY;
        }

        Map<String, Object> jsonObject = new HashMap<>(2);
        jsonObject.put(ACCESS_KEY, accessKey);
        jsonObject.put(SECRET_KEY, secretKey);

        String asString = Json.toJson(jsonObject);
        String request = Codec.build(encryption, DigestUtils.md5Hex(key)).encodeHex(asString);

        String uid = DigestUtils.md5Hex(UUID.randomUUID().toString());
        Map<String, Object> item2 = new LinkedHashMap<>();
        item2.put("ext", ext);
        item2.put(AuthConstant.OAUTH_VALUE, request);
        item2.put(AuthConstant.OAUTH_KEY, key);
        item2.put("x-oauth-uid", uid);
        item2.put("password", password);
        request = Codec.build(encryption, serviceKey).encodeHex(Json.toJson(item2));
        Robin robin1 = ServiceProvider.of(Robin.class).getExtension(authClientProperties.getBalance());
        Robin balance = robin1.create();
        String[] split = SpringBeanUtils.getApplicationContext().getEnvironment().resolvePlaceholders(authClientProperties.getAddress()).split(",");
        balance.addNode((Object[]) split);
        Node robin = balance.selectNode();
        HttpResponse<String> httpResponse = null;
        try {
            String url = robin.getString();
            if (null == url) {
                return null;
            }

            AuthRequest request1 = new AuthRequest();
            request1.setData(request);
            request1.setUsername(username);
            request1.setType(authType.name());
            httpResponse = Unirest.post(
                            StringUtils.endWithAppend(StringUtils.startWithAppend(url, "http://"), "/")
                                    + "doLogin")
                    .contentType(APPLICATION_JSON)
                    .header("accept", "application/json")
                    .header("x-oauth-timestamp", System.nanoTime() + "")
                    .body(Json.toJson(request1))
                    .asString();

        } catch (UnirestException ignored) {
        }

        if (null == httpResponse) {
            return null;
        }

        int status = httpResponse.getStatus();
        String body = httpResponse.getBody();
        if (status == NUM_200) {
            @SuppressWarnings("unchecked")
            ReturnResult<Object> returnResult = Json.fromJson(body, ReturnResult.class);
            String code = returnResult.getCode();
            Object data = returnResult.getData();
            if (code.equals(OK.getCode())) {
                LoginAuthResult loginAuthResult = null;
                try {
                    loginAuthResult = Json.fromJson(Codec.build(encryption, uid).decodeHex(data.toString()), LoginAuthResult.class);
                } catch (Exception ignore) {
                }

                if (null == loginAuthResult) {
                    loginAuthResult = Json.fromJson(Json.toJson(data), LoginAuthResult.class);
                }

                if (null == loginAuthResult) {
                    return null;
                }

                loginAuthResult.setCode(status);
                return loginAuthResult;
            }
            LoginAuthResult loginAuthResult = new LoginAuthResult();
            loginAuthResult.setCode(403);
            loginAuthResult.setMessage(returnResult.getMsg());

            return loginAuthResult;
        }
        LoginAuthResult loginAuthResult = new LoginAuthResult();
        loginAuthResult.setCode(status);
        loginAuthResult.setMessage("认证服务器异常");
        return loginAuthResult;
    }

    @Override
    public LoginAuthResult logout(String uid, LogoutType logoutType, UserResult userResult ) {
        if (Strings.isNullOrEmpty(uid) && logoutType == LogoutType.UN_REGISTER) {
            return new LoginAuthResult(400, "uid不能为空");
        }

        String resultUid = userResult.getUid();

        String accessKey = authClientProperties.getAccessKey();
        String secretKey = authClientProperties.getSecretKey();
        String serviceKey = authClientProperties.getServiceKey();
        String key = UUID.randomUUID().toString();

        if (Strings.isNullOrEmpty(accessKey) || Strings.isNullOrEmpty(secretKey)) {
            accessKey = DefSecret.ACCESS_KEY;
            secretKey = DefSecret.SECRET_KEY;
        }

        Map<String, Object> jsonObject = new HashMap<>(2);
        jsonObject.put(ACCESS_KEY, accessKey);
        jsonObject.put("uid", Strings.isNullOrEmpty(uid) ? resultUid : uid);
        jsonObject.put(SECRET_KEY, secretKey);

        String asString = Json.toJson(jsonObject);
        String md5Hex = DigestUtils.md5Hex(key);
        String request = Codec.build(encryption, md5Hex).encodeHex(asString);

        String uidKey = UUID.randomUUID().toString();
        Map<String, Object> item2 = new HashMap<>(3);
        item2.put(AuthConstant.OAUTH_VALUE, request);
        item2.put(AuthConstant.OAUTH_KEY, key);
        item2.put("x-oauth-uid", uidKey);
        request = Codec.build(encryption, serviceKey).encodeHex(Json.toJson(item2));
        Robin robin = ServiceProvider.of(Robin.class).getExtension(authClientProperties.getBalance());
        Robin robin1 = robin.create();
        String[] split = SpringBeanUtils.getApplicationContext().getEnvironment().resolvePlaceholders(authClientProperties.getAddress()).split(",");
        robin1.addNode((Object[]) split);
        Node node = robin1.selectNode();
        HttpResponse<String> httpResponse = null;
        try {
            String url = node.getString();
            if (null == url) {
                return null;
            }

            AuthRequest request1 = new AuthRequest();
            request1.setData(request);
            request1.setType(logoutType.name());
            httpResponse = Unirest.post(
                            StringUtils.endWithAppend(StringUtils.startWithAppend(url, "http://"), "/")
                                    + "logout")
                    .header("accept", "application/json")
                    .header("x-oauth-timestamp", System.nanoTime() + "")
                    .contentType(APPLICATION_JSON)
                    .body(Json.toJson(request1))
                    .asString();

        } catch (UnirestException ignored) {
        }

        if (null == httpResponse) {
            return null;
        }

        int status = httpResponse.getStatus();
        if (status == NUM_200) {
            LoginAuthResult loginAuthResult = new LoginAuthResult();
            loginAuthResult.setCode(status);
            return loginAuthResult;
        }
        LoginAuthResult loginAuthResult = new LoginAuthResult();
        loginAuthResult.setCode(status);
        loginAuthResult.setMessage("认证服务器异常");
        return loginAuthResult;
    }

    @Override
    public String getLoginCode(String loginCodeType, String type, String token, String callback) {
        Robin robin1 = ServiceProvider.of(Robin.class).getExtension(authClientProperties.getBalance());
        Robin balance = robin1.create();
        String[] split = SpringBeanUtils.getApplicationContext().getEnvironment().resolvePlaceholders(authClientProperties.getAddress()).split(",");
        balance.addNode((Object[]) split);
        Node robin = balance.selectNode();
        HttpResponse<String> httpResponse = null;
        try {
            String url = robin.getString();
            if (null == url) {
                return null;
            }


            httpResponse = Unirest.get(
                            StringUtils.endWithAppend(StringUtils.startWithAppend(url, "http://"), "/")
                                    + loginCodeType + "/" + type+"/loginCodeType?loginCode=" + token + "&callback=" + callback)
                    .asString();

        } catch (UnirestException ignored) {
        }

        if (null == httpResponse) {
            return null;
        }

        if (httpResponse.getStatus() != 200) {
            return null;
        }

        return httpResponse.getBody();
    }

}


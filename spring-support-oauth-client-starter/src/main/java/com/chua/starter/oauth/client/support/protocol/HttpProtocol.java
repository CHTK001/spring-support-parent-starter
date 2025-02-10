package com.chua.starter.oauth.client.support.protocol;

import com.chua.common.support.annotations.Extension;
import com.chua.common.support.annotations.SpiDefault;
import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.crypto.Codec;
import com.chua.common.support.json.Json;
import com.chua.common.support.lang.code.ReturnCode;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.robin.Node;
import com.chua.common.support.lang.robin.Robin;
import com.chua.common.support.objects.annotation.AutoInject;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.task.cache.Cacheable;
import com.chua.common.support.task.cache.GuavaCacheable;
import com.chua.common.support.utils.DigestUtils;
import com.chua.common.support.utils.Md5Utils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.common.support.utils.CookieUtil;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.common.support.utils.ResponseUtils;
import com.chua.starter.oauth.client.support.advice.def.DefSecret;
import com.chua.starter.oauth.client.support.contants.AuthConstant;
import com.chua.starter.oauth.client.support.entity.AuthRequest;
import com.chua.starter.oauth.client.support.enums.UpgradeType;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.infomation.Information;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.user.LoginResult;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.google.common.base.Strings;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;

import java.util.*;

import static com.chua.common.support.http.HttpClientUtils.APPLICATION_JSON;
import static com.chua.common.support.lang.code.ReturnCode.RESOURCE_OAUTH_ERROR;
import static com.chua.common.support.lang.code.ReturnCode.RESULT_ACCESS_UNAUTHORIZED;
import static com.chua.starter.oauth.client.support.contants.AuthConstant.OAUTH_UPGRADE_KEY;
import static com.chua.starter.oauth.client.support.contants.AuthConstant.OAUTH_UPGRADE_KEY_TOKEN;
import static com.chua.starter.oauth.client.support.infomation.Information.*;

/**
 * http
 *
 * @author CH
 */
@SpiDefault
@Extension("http")
@Slf4j
public class HttpProtocol extends AbstractProtocol implements InitializingBean {

    @AutoInject
    private AuthClientProperties authClientProperties;
    protected static Cacheable CACHEABLE;
    private String encryption;

    @Override
    @SuppressWarnings("ALL")
    public AuthenticationInformation approve(Cookie[] cookie, String token) {
        checkCache();
        String key = DigestUtils.md5Hex(UUID.randomUUID().toString());
        Map<String, Object> jsonObject = new HashMap<>(2);
        Cookie[] cookies = Optional.ofNullable(cookie).orElse(new Cookie[0]);
        String cacheKey = getCacheKey(cookies, token);
        if (null != cacheKey) {
            check();
            Object value = CACHEABLE.get(cacheKey);
            if (null != value) {
                AuthenticationInformation authenticationInformation = (AuthenticationInformation)value;
                if (null != authenticationInformation && authenticationInformation.getInformation().getCode() == 200) {
                    UserResume userResume = authenticationInformation.getReturnResult();
                    RequestUtils.setUsername(userResume.getUsername());
                    RequestUtils.setUserInfo(userResume);
                    RequestUtils.setUserId(userResume.getUserId());
                    RequestUtils.setTenantId(userResume.getTenantId());
                    return authenticationInformation;
                } else {
                    CACHEABLE.remove(cacheKey);
                }
            }
        }
        jsonObject.put("x-oauth-cookie", cookies);
        jsonObject.put("x-oauth-token", token);
        String accessKey = authClientProperties.getAccessKey();
        String secretKey = authClientProperties.getSecretKey();
        String serviceKey = authClientProperties.getServiceKey();
        if (Strings.isNullOrEmpty(accessKey) || Strings.isNullOrEmpty(secretKey)) {
            accessKey = DefSecret.ACCESS_KEY;
            secretKey = DefSecret.SECRET_KEY;
        }

        String asString = Json.toJson(jsonObject);
        String request = Codec.build(encryption, Md5Utils.getInstance()
                .getMd5String(accessKey + DigestUtils.md5Hex(secretKey + key))).encodeHex(asString);
        Map<String, Object> item2 = new HashMap<>(3);
        item2.put(AuthConstant.ACCESS_KEY, accessKey);
        item2.put(AuthConstant.SECRET_KEY, secretKey);
        item2.put(AuthConstant.OAUTH_VALUE, request);
        item2.put(AuthConstant.OAUTH_KEY, key);
        request = Codec.build(encryption,serviceKey).encodeHex(Json.toJson(item2));


        Robin balance = ServiceProvider.of(Robin.class).getExtension(authClientProperties.getBalance());
        Robin stringRobin = balance.create();
        String address = authClientProperties.getAddress();
        if(null == address) {
            log.warn("鉴权地址不存在");
            return AuthenticationInformation.authServerError();
        }
        String[] split = SpringBeanUtils.getApplicationContext().getEnvironment().resolvePlaceholders(address).split(",");
        stringRobin.addNode(split);
        Node robin = stringRobin.selectNode();
        HttpResponse<String> httpResponse = null;
        try {
            String url = robin.getString();
            if (null == url) {
                return inCache(cacheKey, AuthenticationInformation.authServerError());
            }
            AuthRequest request1 = new AuthRequest();
            request1.setData(request);
            httpResponse = Unirest.post(
                            StringUtils.endWithAppend(StringUtils.startWithAppend(url, "http://"), "/") + StringUtils.startWithMove(authClientProperties.getOauthUrl(), "/"))
                    .header("x-oauth-timestamp", System.nanoTime() + "")
                    .contentType(APPLICATION_JSON)
                    .body(Json.toJson(request1))
                    .asString();

        } catch (UnirestException ignored) {
        }

        if (null == httpResponse) {
            return inCache(cacheKey, AuthenticationInformation.authServerError());
        }


        int status = httpResponse.getStatus();
        String body = httpResponse.getBody();
        if (status > 400 && status < 600 || Strings.isNullOrEmpty(body)) {
            return inCache(cacheKey, AuthenticationInformation.authServerNotFound());
        }

        if (status == 200) {
            ReturnResult returnResult = Json.fromJson(body, ReturnResult.class);
            String code = returnResult.getCode();
            if (RESOURCE_OAUTH_ERROR.getCode().equals(code) || RESULT_ACCESS_UNAUTHORIZED.getCode().equals(code)) {
                HttpServletRequest servletRequest = RequestUtils.getRequest();
                if (null != servletRequest) {
                    CookieUtil.remove(servletRequest, ResponseUtils.getResponse(), "x-oauth-cookie");
                }

                return inCache(cacheKey, new AuthenticationInformation(AUTHENTICATION_FAILURE, null));
            }

            Object data = returnResult.getData();
            if (Objects.isNull(data)) {
                return inCache(cacheKey, new AuthenticationInformation(AUTHENTICATION_SERVER_EXCEPTION, null));
            }

            if (ReturnCode.OK.getCode().equals(code)) {
                body = Codec.build(encryption, key).decodeHex(data.toString());

                UserResume userResume = Json.fromJson(body, UserResume.class);
                RequestUtils.setUsername(userResume.getUsername());
                RequestUtils.setUserInfo(userResume);
                return inCache(cacheKey, new AuthenticationInformation(Information.OK, userResume));
            }


            return inCache(cacheKey, new AuthenticationInformation(OTHER, null));
        }
        return inCache(cacheKey, AuthenticationInformation.authServerNotFound());
    }

    void checkCache() {
        check();
    }

    void check() {
        if (null != CACHEABLE) {
            return;
        }
        try {
            afterPropertiesSet();
        } catch (Exception ignored) {
        }
    }

    @Override
    public LoginResult upgrade(Cookie[] cookie, String token, UpgradeType upgradeType, String refreshToken) {
        String key = DigestUtils.md5Hex(UUID.randomUUID().toString());
        Map<String, Object> jsonObject = new HashMap<>(2);
        Cookie[] cookies = Optional.ofNullable(cookie).orElse(new Cookie[0]);
        String cacheKey = getCacheKey(cookies, token);
        jsonObject.put("x-oauth-cookie", cookies);
        jsonObject.put("x-oauth-token", token);
        jsonObject.put(OAUTH_UPGRADE_KEY, upgradeType);
        jsonObject.put(OAUTH_UPGRADE_KEY_TOKEN, refreshToken);
        String accessKey = authClientProperties.getAccessKey();
        String secretKey = authClientProperties.getSecretKey();
        String serviceKey = authClientProperties.getServiceKey();
        if (Strings.isNullOrEmpty(accessKey) || Strings.isNullOrEmpty(secretKey)) {
            accessKey = DefSecret.ACCESS_KEY;
            secretKey = DefSecret.SECRET_KEY;
        }

        String asString = Json.toJson(jsonObject);

        String request = Codec.build(encryption, Md5Utils.getInstance()
                .getMd5String(accessKey + DigestUtils.md5Hex(secretKey + key))).encodeHex(asString);
        Map<String, Object> item2 = new HashMap<>(3);
        item2.put(AuthConstant.ACCESS_KEY, accessKey);
        item2.put(AuthConstant.SECRET_KEY, secretKey);
        item2.put(AuthConstant.OAUTH_VALUE, request);
        item2.put(AuthConstant.OAUTH_KEY, key);
        request = Codec.build(encryption, serviceKey).encodeHex(Json.toJson(item2));


        Robin balance = ServiceProvider.of(Robin.class).getExtension(authClientProperties.getBalance());
        Robin stringRobin = balance.create();
        String[] split = SpringBeanUtils.getApplicationContext().getEnvironment().resolvePlaceholders(authClientProperties.getAddress()).split(",");
        stringRobin.addNode(split);
        Node robin = stringRobin.selectNode();
        HttpResponse<String> httpResponse = null;
        try {
            String url = robin.getString();
            if (null == url) {
                throw new IllegalArgumentException("OSS服务器不存在");
            }

            httpResponse = Unirest.post(
                            StringUtils.endWithAppend(StringUtils.startWithAppend(url, "http://"), "/") + "upgrade")
                    .header("x-oauth-timestamp", System.nanoTime() + "")
                    .field("data", request)
                    .asString();

        } catch (UnirestException ignored) {
        }

        if (null == httpResponse) {
            throw new IllegalArgumentException("OSS服务器不存在");
        }
        int status = httpResponse.getStatus();
        String body = httpResponse.getBody();
        if (status > 400 && status < 600 || Strings.isNullOrEmpty(body)) {
            inCache(cacheKey, AuthenticationInformation.authServerNotFound());
            throw new IllegalArgumentException("OSS服务器不存在");
        }

        if (status == 200) {
            ReturnResult returnResult = Json.fromJson(body, ReturnResult.class);
            String code = returnResult.getCode();
            if (RESOURCE_OAUTH_ERROR.getCode().equals(code)) {
                HttpServletRequest servletRequest = RequestUtils.getRequest();
                if (null != servletRequest) {
                    CookieUtil.remove(servletRequest, ResponseUtils.getResponse(), "x-oauth-cookie");
                }

                inCache(cacheKey, new AuthenticationInformation(AUTHENTICATION_FAILURE, null));
                throw new IllegalArgumentException("OSS服务器不存在");

            }

            Object data = returnResult.getData();
            if (Objects.isNull(data)) {
                inCache(cacheKey, new AuthenticationInformation(AUTHENTICATION_SERVER_EXCEPTION, null));
                throw new IllegalArgumentException("OSS服务器不存在");

            }

            if (ReturnCode.OK.getCode().equals(code)) {
                body = Codec.build(encryption, key).decodeHex(data.toString());
                LoginResult loginResult = Json.fromJson(body, LoginResult.class);
                UserResume userResume = BeanUtils.copyProperties(loginResult.getUserResult(), UserResume.class);
                inCache(cacheKey, new AuthenticationInformation(Information.OK, userResume));
                return loginResult;
            }


            inCache(cacheKey, new AuthenticationInformation(OTHER, null));
            throw new IllegalArgumentException("OSS服务器不存在");

        }
        inCache(cacheKey, AuthenticationInformation.authServerNotFound());
        throw new IllegalArgumentException("OSS服务器不存在");

    }

    AuthenticationInformation inCache(String cacheKey, AuthenticationInformation authenticationInformation) {
        if (null == cacheKey) {
            return authenticationInformation;
        }

        UserResume userResume = authenticationInformation.getReturnResult();
        if(null != userResume) {
            RequestUtils.setUsername(userResume.getUsername());
            RequestUtils.setUserInfo(userResume);
            RequestUtils.setUserId(userResume.getUserId());
            RequestUtils.setTenantId(userResume.getTenantId());
        }
        return CACHEABLE.put(cacheKey, authenticationInformation);
    }

    String getCacheKey(Cookie[] cookies, String token) {
        if (StringUtils.isNotEmpty(StringUtils.ifValid(token, ""))) {
            return token;
        }

        for (Cookie cookie : cookies) {
            if ("x-oauth-cookie".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.encryption = authClientProperties.getEncryption();
        CACHEABLE = new GuavaCacheable((int) authClientProperties.getCacheTimeout() / 3600);
        CACHEABLE.afterPropertiesSet();
        CACHEABLE = CACHEABLE.cacheHotColdBackup(authClientProperties.isCacheHotColdBackup());
    }
}

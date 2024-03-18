package com.chua.starter.oauth.client.support.protocol;

import com.chua.common.support.annotations.Extension;
import com.chua.common.support.annotations.SpiDefault;
import com.chua.common.support.crypto.Codec;
import com.chua.common.support.json.Json;
import com.chua.common.support.lang.code.ReturnCode;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.robin.Node;
import com.chua.common.support.lang.robin.Robin;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.task.cache.CacheConfiguration;
import com.chua.common.support.task.cache.Cacheable;
import com.chua.common.support.task.cache.GuavaCacheable;
import com.chua.common.support.utils.DigestUtils;
import com.chua.common.support.utils.Md5Utils;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.value.Value;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.common.support.utils.CookieUtil;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.common.support.utils.ResponseUtils;
import com.chua.starter.oauth.client.support.advice.def.DefSecret;
import com.chua.starter.oauth.client.support.contants.AuthConstant;
import com.chua.starter.oauth.client.support.entity.AuthRequest;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.google.common.base.Strings;
import jakarta.annotation.Resource;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import org.springframework.beans.factory.InitializingBean;

import java.util.*;

import static com.chua.common.support.http.HttpClientUtils.APPLICATION_JSON;
import static com.chua.common.support.lang.code.ReturnCode.RESOURCE_OAUTH_ERROR;
import static com.chua.starter.oauth.client.support.infomation.Information.*;

/**
 * http
 *
 * @author CH
 */
@SpiDefault
@Extension("http")
public class HttpProtocol extends AbstractProtocol implements InitializingBean {

    @Resource
    private AuthClientProperties authClientProperties;
    private static Cacheable CACHEABLE;
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
            Value o = CACHEABLE.get(cacheKey);
            if (null != o) {
                AuthenticationInformation authenticationInformation = (AuthenticationInformation) o.getValue();
                if (null != authenticationInformation && authenticationInformation.getInformation().getCode() == 200) {
                    UserResume userResume = authenticationInformation.getReturnResult();
                    RequestUtils.setUsername(userResume.getUsername());
                    RequestUtils.setUserInfo(userResume);
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
        String[] split = SpringBeanUtils.getApplicationContext().getEnvironment().resolvePlaceholders(authClientProperties.getAddress()).split(",");
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
            if (RESOURCE_OAUTH_ERROR.getCode().equals(code)) {
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
                return inCache(cacheKey, new AuthenticationInformation(OK, userResume));
            }


            return inCache(cacheKey, new AuthenticationInformation(OTHER, null));
        }
        return inCache(cacheKey, AuthenticationInformation.authServerNotFound());
    }

    private void checkCache() {
        check();
    }

    private void check() {
        if (null != CACHEABLE) {
            return;
        }
        try {
            afterPropertiesSet();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void refreshToken(Cookie[] cookie, String token) {
        String key = DigestUtils.md5Hex(UUID.randomUUID().toString());
        Map<String, Object> jsonObject = new HashMap<>(2);
        Cookie[] cookies = Optional.ofNullable(cookie).orElse(new Cookie[0]);
        String cacheKey = getCacheKey(cookies, token);
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
                            StringUtils.endWithAppend(StringUtils.startWithAppend(url, "http://"), "/") + "refresh")
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

                UserResume userResume = Json.fromJson(body, UserResume.class);
                inCache(cacheKey, new AuthenticationInformation(OK, userResume));
                return;
            }


            inCache(cacheKey, new AuthenticationInformation(OTHER, null));
            throw new IllegalArgumentException("OSS服务器不存在");

        }
        inCache(cacheKey, AuthenticationInformation.authServerNotFound());
        throw new IllegalArgumentException("OSS服务器不存在");

    }

    private AuthenticationInformation inCache(String cacheKey, AuthenticationInformation authenticationInformation) {
        if (null == cacheKey) {
            return authenticationInformation;
        }

        return (AuthenticationInformation) CACHEABLE.put(cacheKey, authenticationInformation).getValue();
    }

    private String getCacheKey(Cookie[] cookies, String token) {
        if (StringUtils.isNotEmpty(StringUtils.ifValid(token, ""))) {
            return token;
        }

        for (Cookie cookie : cookies) {
            if (cookie.getName().equals("x-oauth-cookie")) {
                return cookie.getValue();
            }
        }

        return null;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.encryption = authClientProperties.getEncryption();
        CACHEABLE = new GuavaCacheable(CacheConfiguration.builder()
                .expireAfterWrite((int) authClientProperties.getCacheTimeout())
                .hotColdBackup(authClientProperties.isCacheHotColdBackup())
                .build());
    }
}

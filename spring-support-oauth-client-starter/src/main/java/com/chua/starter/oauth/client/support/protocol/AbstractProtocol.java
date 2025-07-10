package com.chua.starter.oauth.client.support.protocol;

import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.crypto.Codec;
import com.chua.common.support.json.Json;
import com.chua.common.support.json.JsonObject;
import com.chua.common.support.lang.code.ReturnCode;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.robin.Node;
import com.chua.common.support.lang.robin.Robin;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.task.cache.Cacheable;
import com.chua.common.support.task.cache.GuavaCacheable;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.common.support.utils.CookieUtil;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.common.support.utils.ResponseUtils;
import com.chua.starter.oauth.client.support.enums.UpgradeType;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.infomation.Information;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.LoginResult;
import com.chua.starter.oauth.client.support.user.UserResume;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

import static com.chua.common.support.lang.code.ReturnCode.RESOURCE_OAUTH_ERROR;
import static com.chua.common.support.lang.code.ReturnCode.RESULT_ACCESS_UNAUTHORIZED;
import static com.chua.starter.oauth.client.support.infomation.Information.*;

/**
 * 协议
 *
 * @author CH
 */
@Slf4j
public abstract class AbstractProtocol implements Protocol {

    protected static Cacheable CACHEABLE;
    private final boolean enableEncryption;
    protected AuthClientProperties authClientProperties;
    private final String encryption;

    public AbstractProtocol(AuthClientProperties authClientProperties) {
        this.authClientProperties = authClientProperties;
        this.encryption = authClientProperties.getEncryption();
        this.enableEncryption = authClientProperties.isEnableEncryption();
        if(null == CACHEABLE) {
            CACHEABLE = new GuavaCacheable((int) authClientProperties.getCacheTimeout() / 3600);
            CACHEABLE.afterPropertiesSet();
            CACHEABLE = CACHEABLE.cacheHotColdBackup(authClientProperties.isCacheHotColdBackup());
        }
    }


    /**
     * 获取认证信息
     *
     * @param cookies     cookies
     * @param token       令牌
     * @param subProtocol 认证协议
     * @return 认证信息
     */
    protected abstract AuthenticationInformation approve(Cookie cookies, String token, String subProtocol);

    /**
     * 获取认证信息（升级用）
     *
     * @param cookie       cookies
     * @param token        令牌
     * @param upgradeType  升级类型
     * @param refreshToken 刷新令牌
     * @return 认证信息
     */
    protected abstract AuthenticationInformation upgradeInformation(Cookie cookie, String token, UpgradeType upgradeType, String refreshToken);


    /**
     * 是否加密
     *
     * @return 是否加密
     */
    protected boolean isEncode() {
        return enableEncryption;
    }
    /**
     * 创建数据
     *
     * @param value 值
     * @param key   密钥
     * @return 数据
     */
    protected String createData(String value, String key) {
        if (!enableEncryption) {
            return value;
        }
        return Codec.build(encryption, key).encodeHex(value);
    }

    /**
     * 创建数据
     *
     * @param jsonObject jsonObject
     * @param key        密钥
     * @return 数据
     */
    protected String createData(JsonObject jsonObject, String key) {
        String jsonObjectJSONString = jsonObject.toJSONString();
        if (!enableEncryption) {
            return jsonObjectJSONString;
        }
        return Codec.build(encryption, key).encodeHex(jsonObjectJSONString);
    }

    /**
     * 选择地址
     *
     * @return 地址
     */
    protected String selectUrl() {
        Robin balance = ServiceProvider.of(Robin.class).getExtension(authClientProperties.getBalance());
        Robin stringRobin = balance.create();
        String address = authClientProperties.getAddress();
        if (null == address) {
            log.warn("鉴权地址不存在");
            return null;
        }
        String[] split = SpringBeanUtils.getApplicationContext().getEnvironment().resolvePlaceholders(address).split(",");
        stringRobin.addNode((Object[]) split);
        Node robin = stringRobin.selectNode();
        return robin.getString();
    }


    @Override
    public AuthenticationInformation approve(Cookie[] cookies, String token, String subProtocol) {
        Cookie cookie = CookieUtil.get(cookies, "x-oauth-cookie");
        String cacheKey = getCacheKey(cookies, token);
        if (hasCache(cacheKey)) {
            AuthenticationInformation authenticationInformation = cacheAuthenticationInformation(cacheKey);
            if (null != authenticationInformation) {
                return authenticationInformation;
            }
        }

        if (!hasValid(cookie, token)) {
            return AuthenticationInformation.noAuth();
        }
        AuthenticationInformation authenticationInformation = approve(cookie, token, subProtocol);

        CACHEABLE.put(cacheKey, authenticationInformation);
        return authenticationInformation;
    }

    /**
     * 是否有效
     *
     * @param cookie cookie
     * @param token  令牌
     * @return 是否有效
     */
    private boolean hasValid(Cookie cookie, String token) {
        return null != cookie || StringUtils.isNotBlank(token);
    }

    @Override
    public LoginResult upgrade(Cookie[] cookies, String token, UpgradeType upgradeType, String refreshToken) {
        Cookie cookie = CookieUtil.get(cookies, "x-oauth-cookie");
        if (!hasValid(cookie, token)) {
            throw new IllegalArgumentException("认证失败");
        }
        AuthenticationInformation authenticationInformation = upgradeInformation(cookie, token, upgradeType, refreshToken);
        if(authenticationInformation.getInformation() != OK) {
            throw new IllegalArgumentException("升级失败");
        }
        return new LoginResult(authenticationInformation.getToken(), authenticationInformation.getRefreshToken(), authenticationInformation.getReturnResult());
    }

    /**
     * 是否有缓存
     *
     * @return 是否有缓存
     */
    protected boolean hasCache(String cacheKey) {
        if (null == cacheKey) {
            return false;
        }
        Object value = CACHEABLE.get(cacheKey);
        return null != value;
    }


    /**
     * 缓存认证信息
     *
     * @param key 缓存key
     * @return 缓存信息
     */
    protected LoginResult createUpgradeResponse(ReturnResult returnResult, String key) {
        String code = returnResult.getCode();
        if (RESOURCE_OAUTH_ERROR.getCode().equals(code)) {
            HttpServletRequest servletRequest = RequestUtils.getRequest();
            if (null != servletRequest) {
                CookieUtil.remove(servletRequest, ResponseUtils.getResponse(), "x-oauth-cookie");
            }

            throw new IllegalArgumentException("OSS服务器不存在");
        }
        Object data = returnResult.getData();
        if (Objects.isNull(data)) {
            throw new IllegalArgumentException("OSS服务器不存在");

        }

        String body;
        if (ReturnCode.OK.getCode().equals(code)) {
            // 根据加密配置决定是否解密
            if (authClientProperties.isEnableEncryption()) {
                body = Codec.build(encryption, key).decodeHex(data.toString());
            } else {
                body = data.toString();
            }
            LoginResult loginResult = Json.fromJson(body, LoginResult.class);
            UserResume userResume = BeanUtils.copyProperties(loginResult.getUserResume(), UserResume.class);
            return loginResult;
        }

        throw new IllegalArgumentException("OSS服务器不存在");

    }

    /**
     * 创建认证信息
     *
     * @param returnResult 返回结果
     * @param key          密钥
     * @return 认证信息
     */
    protected AuthenticationInformation createAuthenticationInformation(ReturnResult returnResult, String key, String path) {
        String code = returnResult.getCode();
        HttpServletRequest servletRequest = RequestUtils.getRequest();
        if (RESOURCE_OAUTH_ERROR.getCode().equals(code) || RESULT_ACCESS_UNAUTHORIZED.getCode().equals(code)) {
            if (null != servletRequest) {
                unregisterFromRequest(servletRequest);
            }

            return new AuthenticationInformation(AUTHENTICATION_FAILURE, null);
        }

        Object data = returnResult.getData();
        if (Objects.isNull(data)) {
            unregisterFromRequest(servletRequest);
            return new AuthenticationInformation(AUTHENTICATION_SERVER_EXCEPTION, null);
        }

        String body;
        if (ReturnCode.OK.getCode().equals(code)) {
            // 根据加密配置决定是否解密
            if (authClientProperties.isEnableEncryption()) {
                body = Codec.build(encryption, key).decodeHex(data.toString());
            } else {
                body = data.toString();
            }

            if(path.endsWith("login")) {
                LoginResult loginResult = Json.fromJson(body, LoginResult.class);
                UserResume userResume = loginResult.getUserResume();
                registerToRequest(userResume);
                AuthenticationInformation authenticationInformation = new AuthenticationInformation(OK, userResume);
                authenticationInformation.setRefreshToken(loginResult.getRefreshToken());
                authenticationInformation.setToken(loginResult.getToken());
                return authenticationInformation;
            }

            if(path.endsWith("logout")) {
                unregisterFromRequest(servletRequest);
                return new AuthenticationInformation(Information.OK, null);
            }
            UserResume userResume = Json.fromJson(body, UserResume.class);
            return new AuthenticationInformation(Information.OK, userResume);
        }


        return new AuthenticationInformation(OTHER, null);
    }

    /**
     * 删除hook
     *
     */
    private void unregisterFromRequest(HttpServletRequest servletRequest) {
        RequestUtils.removeUsername();
        RequestUtils.removeUserInfo();
        RequestUtils.removeUserId();
        RequestUtils.removeTenantId();
        if(null != servletRequest) {
            CookieUtil.remove(servletRequest, ResponseUtils.getResponse(), "x-oauth-cookie");
        }
    }
    /**
     * 注册
     *
     * @return {@link LoginAuthResult}
     */
    public static void registerToRequest(UserResume userResume) {
        RequestUtils.setUsername(userResume.getUsername());
        RequestUtils.setUserId(userResume.getUserId());
        RequestUtils.setTenantId(userResume.getTenantId());
        RequestUtils.setUserInfo(userResume);
    }

    /**
     * 缓存认证信息
     *
     * @param cacheKey 缓存key
     */
    protected AuthenticationInformation cacheAuthenticationInformation(String cacheKey) {
        Object value = CACHEABLE.get(cacheKey);
        AuthenticationInformation authenticationInformation = (AuthenticationInformation) value;
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
        return null;
    }

    /**
     * 获取缓存key
     *
     * @param cookies cookies
     * @param token   token
     * @return 缓存key
     */
    protected String getCacheKey(Cookie[] cookies, String token) {
        if (StringUtils.isNotEmpty(StringUtils.ifValid(token, ""))) {
            return token;
        }

        if (null == cookies) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if ("x-oauth-cookie".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }

}

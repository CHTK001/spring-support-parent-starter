package com.chua.starter.oauth.server.support.information;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.chua.common.support.crypto.decode.KeyDecode;
import com.chua.common.support.crypto.encode.KeyEncode;
import com.chua.common.support.crypto.utils.DigestUtils;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.Md5Utils;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.oauth.server.support.parser.Authorization;
import com.chua.starter.oauth.server.support.parser.InvalidAuthorization;
import com.chua.starter.oauth.server.support.parser.RequestAuthorization;
import com.chua.starter.oauth.server.support.properties.AuthServerProperties;
import lombok.Getter;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import static com.chua.starter.oauth.client.support.contants.AuthConstant.*;

/**
 * 鉴权信息
 *
 * @author CH
 */
public class AuthInformation {
    private final String data;
    private final HttpServletRequest request;
    @Getter
    private final AuthServerProperties authServerProperties;
    private final KeyDecode decode;
    @Getter
    private final String address;
    @Getter
    private final KeyEncode encode;
    private String accessKey;
    private String secretKey;
    @Getter
    private String oauthKey;
    private String token;
    private Cookie[] cookie;

    public AuthInformation(String data, HttpServletRequest request, AuthServerProperties authServerProperties) {
        this.data = data;
        this.request = request;
        this.authServerProperties = authServerProperties;
        this.address = RequestUtils.getIpAddress(request);
        this.decode = ServiceProvider.of(KeyDecode.class).getExtension(authServerProperties.getEncryption());
        this.encode = ServiceProvider.of(KeyEncode.class).getExtension(authServerProperties.getEncryption());
    }

    /**
     * 解析
     */
    public Authorization resolve() {
        try {
            return analysisRequest();
        } catch (Exception e) {
            return InvalidAuthorization.INSTANCE;
        }
    }

    /**
     * 解析请求
     */
    private Authorization analysisRequest() {
        String requestData = decode.decodeHex(data, authServerProperties.getServiceKey());
        JSONObject jsonObject = JSON.parseObject(requestData);
        String oauthValue = jsonObject.getString(OAUTH_VALUE);
        this.accessKey = jsonObject.getString(ACCESS_KEY);
        this.secretKey = jsonObject.getString(SECRET_KEY);
        this.oauthKey = jsonObject.getString(OAUTH_KEY);

        String tokenCookie = decode.decodeHex(oauthValue, Md5Utils.getInstance()
                .getMd5String(accessKey + DigestUtils.md5Hex(secretKey + oauthKey)));

        JSONObject parseObject = JSON.parseObject(tokenCookie);
        this.token = parseObject.getString(authServerProperties.getTokenName());
        JSONArray jsonArray = parseObject.getJSONArray(authServerProperties.getCookieName());
        int size = jsonArray.size();
        Cookie[] cookies = new Cookie[size];
        for (int i = 0; i < size; i++) {
            JSONObject jsonObject1 = jsonArray.getJSONObject(i);
            cookies[i] = new Cookie(jsonObject1.getString("name"), jsonObject1.getString("value"));
        }
        this.cookie = cookies;

        RequestAuthorization authorization = new RequestAuthorization(this, token, cookie, accessKey, secretKey);
        SpringBeanUtils.getApplicationContext().getAutowireCapableBeanFactory().autowireBean(authorization);
        return authorization;
    }
}

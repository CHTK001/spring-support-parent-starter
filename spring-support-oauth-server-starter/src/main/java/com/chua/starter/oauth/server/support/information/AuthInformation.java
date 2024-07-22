package com.chua.starter.oauth.server.support.information;

import com.chua.common.support.crypto.Codec;
import com.chua.common.support.json.Json;
import com.chua.common.support.json.JsonArray;
import com.chua.common.support.json.JsonObject;
import com.chua.common.support.utils.DigestUtils;
import com.chua.common.support.utils.Md5Utils;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.oauth.client.support.enums.UpgradeType;
import com.chua.starter.oauth.server.support.check.LoginCheck;
import com.chua.starter.oauth.server.support.parser.Authorization;
import com.chua.starter.oauth.server.support.parser.InvalidAuthorization;
import com.chua.starter.oauth.server.support.parser.RequestAuthorization;
import com.chua.starter.oauth.server.support.properties.AuthServerProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import org.springframework.context.ApplicationContext;

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
    @Getter
    private final String address;
    private final String encryption;
    private final ApplicationContext applicationContext;
    private final LoginCheck loginCheck;
    @Getter
    private String accessKey;
    private String secretKey;
    @Getter
    private String oauthKey;
    private String token;
    private Cookie[] cookie;
    @Getter
    private UpgradeType upgradeType;

    public AuthInformation(String data,
                           HttpServletRequest request,
                           AuthServerProperties authServerProperties,
                           ApplicationContext applicationContext,
                           LoginCheck loginCheck) {
        this.data = data;
        this.request = request;
        this.authServerProperties = authServerProperties;
        this.address = RequestUtils.getIpAddress(request);
        this.encryption = authServerProperties.getEncryption();
        this.applicationContext = applicationContext;
        this.loginCheck = loginCheck;
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
        String requestData = Codec.build(encryption, authServerProperties.getServiceKey()).decodeHex(data);
        JsonObject jsonObject = Json.getJsonObject(requestData);
        String oauthValue = jsonObject.getString(OAUTH_VALUE);
        this.accessKey = jsonObject.getString(ACCESS_KEY);
        this.secretKey = jsonObject.getString(SECRET_KEY);
        this.oauthKey = jsonObject.getString(OAUTH_KEY);

        String tokenCookie = Codec.build(encryption, Md5Utils.getInstance()
                .getMd5String(accessKey + DigestUtils.md5Hex(secretKey + oauthKey))).decodeHex(oauthValue);

        JsonObject parseObject = Json.getJsonObject(tokenCookie);
        this.token = parseObject.getString(authServerProperties.getTokenName());
        this.upgradeType = UpgradeType.getUpgradeType(parseObject.getString(OAUTH_UPGRADE_KEY));
        JsonArray jsonArray = parseObject.getJsonArray(authServerProperties.getCookieName());
        int size = jsonArray.size();
        Cookie[] cookies = new Cookie[size];
        for (int i = 0; i < size; i++) {
            JsonObject jsonObject1 = jsonArray.getJsonObject(i);
            cookies[i] = new Cookie(jsonObject1.getString("name"), jsonObject1.getString("value"));
        }
        this.cookie = cookies;

        RequestAuthorization authorization = new RequestAuthorization(this,
                token,
                cookie,
                accessKey,
                secretKey,
                upgradeType,
                applicationContext,
                loginCheck
                );
        SpringBeanUtils.getApplicationContext().getAutowireCapableBeanFactory().autowireBean(authorization);
        return authorization;
    }

    public Codec getCodec() {
        return Codec.build(encryption, oauthKey);
    }
}

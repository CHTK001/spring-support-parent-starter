package com.chua.starter.oauth.client.support.executor;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.crypto.digest.HMac;
import com.chua.common.support.json.JsonObject;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.objects.annotation.AutoInject;
import com.chua.common.support.utils.DigestUtils;
import com.chua.common.support.utils.HexUtils;
import com.chua.common.support.utils.RandomUtils;
import com.chua.starter.oauth.client.support.entity.AppKeySecret;
import com.chua.starter.oauth.client.support.entity.ManufacturerUser;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.key.AppKey;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.web.WebRequest;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;

/**
 * 浪潮服务器
 *
 * @author CH
 * @since 2025/10/23 20:49
 */
@Slf4j
@Spi("inspur")
public final class AuthAppKeyClientExecute extends AbstractAppKeyClientExecute {

    private static final String USER_INFO_URL = "http://10.136.0.8:8083/cpn/api/extral/applicationCode/getUserInfoByToken";

    private final String userInfoUrl;

    @AutoInject
    private AuthClientProperties authClientProperties;

    public AuthAppKeyClientExecute(AppKey appKey) {
        super(appKey);
        this.userInfoUrl = USER_INFO_URL;
    }

    public AuthAppKeyClientExecute(AppKey appKey, String userInfoUrl) {
        super(appKey);
        this.userInfoUrl = userInfoUrl;
    }

    @Override
    ReturnResult<ManufacturerUser> parseUserInfo(String userCode) {
        WebRequest webRequest1 = new WebRequest(
                authClientProperties,
                null, null);
        String xTime = String.valueOf(System.currentTimeMillis());
        String xRandom = RandomUtils.randomString(7) + RandomUtils.randomLong(9)
                + RandomUtils.randomString(7) + RandomUtils.randomLong(9);
        String body = "{}";
        String xBody = HexUtils.encodeHexString(DigestUtils.md5(body));
        String str = String.format("x-body=%s&x-random=%s&x-time=%s", xBody, xRandom, xTime);
        log.info("待签名字符串: {}", str);
        log.info("时间戳: {}", xTime);
        log.info("随机数: {}", xRandom);
        log.info("请求体摘要: {}", xBody);
        HMac hmac = new HMac("HmacSHA256", appKey.getAppSecret().getBytes(StandardCharsets.UTF_8));
        String xSign = hmac.digestHex(str);
        log.info("签名结果: {}", xSign);
        AuthenticationInformation authentication = webRequest1.authentication(
                new AppKeySecret()
                        .setAppId(appKey.getAppId())
                        .setUserCode(userCode)
                        .setXSign(xSign)
                        .setXTime(xTime)
                        .setXRandom(xRandom)
                        .setBody(body)
        );
        return null;
    }


}

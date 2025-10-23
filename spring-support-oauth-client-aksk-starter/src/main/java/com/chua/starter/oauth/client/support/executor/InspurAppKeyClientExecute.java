package com.chua.starter.oauth.client.support.executor;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.crypto.digest.HMac;
import com.chua.common.support.json.Json;
import com.chua.common.support.json.JsonObject;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.DigestUtils;
import com.chua.common.support.utils.HexUtils;
import com.chua.common.support.utils.RandomUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.oauth.client.support.entity.ManufacturerUser;
import com.chua.starter.oauth.client.support.entity.WaveUserInfoV2Response;
import com.chua.starter.oauth.client.support.key.AppKey;
import kong.unirest.HttpRequestWithBody;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 浪潮服务器
 *
 * @author CH
 * @since 2025/10/23 20:49
 */
@Slf4j
@Spi("inspur")
public final class InspurAppKeyClientExecute extends AbstractAppKeyClientExecute {

    private static final String TOKEN_URL = "http://10.136.0.8:8083/cpn/extral/applicationCode/appAuthCheck";
    private static final String USER_INFO_URL = "http://10.136.0.8:8083/cpn/api/extral/applicationCode/getUserInfoByToken";

    private final String tokenUrl;
    private final String userInfoUrl;

    public InspurAppKeyClientExecute(AppKey appKey) {
        super(appKey);
        this.tokenUrl = TOKEN_URL;
        this.userInfoUrl = USER_INFO_URL;
    }

    public InspurAppKeyClientExecute(AppKey appKey, String tokenUrl, String userInfoUrl) {
        super(appKey);
        this.tokenUrl = tokenUrl;
        this.userInfoUrl = userInfoUrl;
    }

    @Override
    ReturnResult<ManufacturerUser> parseUserInfo(String userCode) {
        String authorization = getAuthorization(userCode);
        log.info("Authorization: {}", authorization);
        Map<String, Object> userRequest = new HashMap<>();
        userRequest.put("appID", appKey.getAppId());
        String userBody = Json.toJSONString(userRequest);
        WaveHeader waveHeader = new WaveHeader(userBody, appKey);
        HttpResponse<String> response = Unirest.post(userInfoUrl)
                .header("x-time", waveHeader.getXTime())
                .header("x-random", waveHeader.getXRandom())
                .header("x-sign", waveHeader.getXSign())
                .header("appKey", appKey.getAppId())
                .body(userBody)
                .asString();
        String responseBody = response.getBody();
        int status = response.getStatus();
        log.info("返回数据状态码: {}", status);
        log.info("返回数据: {}", responseBody);
        if (status != 200) {
            return ReturnResult.fail(responseBody);
        }
        WaveUserInfoV2Response waveUserInfoV2Response = Json.fromJson(responseBody, WaveUserInfoV2Response.class);
        WaveUserInfoV2Response.User user = waveUserInfoV2Response.getUser();
        return ReturnResult.ok(ManufacturerUser.builder()
                .userCode(userCode)
                .userId(user.getLoginAccountId())
                .clientIp(user.getClientIp())
                .username(waveUserInfoV2Response.getUser().getLoginAccountName())
                .build());
    }

    /**
     * 获取授权信息
     *
     * @param userCode 用户code
     * @return 授权信息
     */
    private String getAuthorization(String userCode) {
        Map<String, Object> params = new HashMap<>();
        params.put("appID", appKey.getAppId());
        params.put("appSecret", appKey.getAppSecret());
        params.put("code", userCode);
        String body = Json.toJSONString(params);
        log.info("下发code到浪潮授平台: {}", body);
        WaveHeader waveHeader = new WaveHeader(body, appKey);
        HttpResponse<String> response = Unirest.post(tokenUrl)
                .header("x-time", waveHeader.getXTime())
                .header("x-random", waveHeader.getXRandom())
                .header("x-sign", waveHeader.getXSign())
                .header("appKey", appKey.getAppId())
                .body(body)
                .asString();
        String responseBody = response.getBody();
        log.info("返回数据状态码: {}", response.getStatus());
        log.info("返回数据: {}", responseBody);

        JsonObject tokenJsonObject = Json.getJsonObject(responseBody);
        return tokenJsonObject.getString("data");
    }


    /**
     * @author CH
     * @since 2025/10/22 13:29
     */
    @Slf4j
    @Data
    public static class WaveHeader {

        /**
         * 时间戳
         */
        private String xTime;

        /**
         * 随机数
         */
        private String xRandom;

        /**
         * 签名
         */
        private String xSign;

        /**
         * 请求体
         */
        private String xBody;

        /**
         * 构造浪潮请求头信息
         *
         * @param body   请求体内容，用于生成签名摘要
         * @param appKey 应用密钥信息，包含appId和appSecret，用于签名计算
         * @author CH
         * @example body: {"appID":"test123","appSecret":"secret123","code":"abc123"}
         * @example appKey: AppKey{appId='test123', appSecret='secret123'}
         * @since 2025/10/23 20:49
         */
        public WaveHeader(String body, AppKey appKey) {
            this.xTime = String.valueOf(System.currentTimeMillis());
            this.xRandom = RandomUtils.randomString(7) + RandomUtils.randomLong(9)
                    + RandomUtils.randomString(7) + RandomUtils.randomLong(9);
            this.xBody = HexUtils.encodeHexString(DigestUtils.md5(body));
            String str = String.format("x-body=%s&x-random=%s&x-time=%s", xBody, xRandom, xTime);
            log.info("待签名字符串: {}", str);
            log.info("时间戳: {}", xTime);
            log.info("随机数: {}", xRandom);
            log.info("请求体摘要: {}", xBody);
            HMac hmac = new HMac("HmacSHA256", appKey.getAppSecret().getBytes(StandardCharsets.UTF_8));
            this.xSign = hmac.digestHex(str);
            log.info("签名结果: {}", xSign);
        }
    }

}

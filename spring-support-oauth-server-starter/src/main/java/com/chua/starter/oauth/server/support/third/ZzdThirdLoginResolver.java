package com.chua.starter.oauth.server.support.third;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chua.common.support.annotations.Extension;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.chua.starter.oauth.server.support.third.properties.ThirdProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;

/**
 * 浙政钉用户登录
 *
 * @author CH
 */
@Slf4j
@Extension("zzd")
public class ZzdThirdLoginResolver implements ThirdLoginResolver {


    @Resource
    private ThirdProperties thirdProperties;

    @Resource
    private RestTemplate restTemplate;


    @Override
    public UserResult login(String ticket, String appId) {
        log.info("浙政钉用户单点登录: \r\n--> ticket: {}\r\n--> appId: {}", ticket, appId);
        String accessToken = getAccessToken();
        String userInfo = getUserInfo(accessToken, ticket);
        JSONObject jsonObject = JSON.parseObject(userInfo);
        return new UserResult().setUsername(jsonObject.getString("account")).setExt(jsonObject);
    }

    /**
     * 用户信息
     *
     * @param accessToken token
     * @param authCode    唯一码
     * @return
     */
    public String getUserInfo(String accessToken, String authCode) {
//        PostClient postClient = executableClient.newPostClient(USER_INFO.getUri());
//        postClient.addParameter("access_token", accessToken);
//        postClient.addParameter("auth_code", authCode);
//        return postClient.post();

        return null;
    }

    /**
     * token
     *
     * @return token
     */
    public String getAccessToken() {
//        GetClient getClient = executableClient.newGetClient(ACCESS_TOKEN.getUri());
//        getClient.addParameter("appkey", thirdProperties.getAccessKey());
//        getClient.addParameter("appsecret", thirdProperties.getSecretKey());
//        return getClient.get();
        return null;

    }

    /**
     *
     */
    @AllArgsConstructor
    @Getter
    public static enum DingDingApi {
        /**
         * token
         */
        ACCESS_TOKEN("/gettoken.json", "GET", "获取应用 access_token"),
        /**
         * userInfo
         */
        USER_INFO("/rpc/oauth2/dingtalk_app_user.json", "POST", "获取用户详情"),
        /**
         * userInfo
         */
        USER_INFO_BY_CODE("/rpc/oauth2/getuserinfo_bycode.json", "POST", "通过code获取用户详情"),
        /**
         * Ticket
         */
        TICKET("/get_jsapi_token.json", "POST", "获取应用 ticket");

        private final String uri;
        private final String method;
        private final String dict;

    }

}

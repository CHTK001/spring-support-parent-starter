package com.chua.starter.oauth.server.support.third;

import com.chua.common.support.annotations.Extension;
import com.chua.common.support.json.Json;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.chua.starter.oauth.server.support.third.properties.ThirdProperties;
import com.chua.starter.oauth.server.support.third.result.ZlbWeChatAccessTokenResult;
import com.chua.starter.oauth.server.support.third.result.ZlbWeChatBaseResult;
import com.chua.starter.oauth.server.support.third.result.ZlbWeChatUserInfoResult;
import com.chua.starter.oauth.server.support.third.utils.HmacAuthUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cglib.beans.BeanMap;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 浙里办微信小程序单点登录
 *
 * @author CH
 */
@Slf4j
@Extension("zlb-wechat")
public class WechatThirdLoginResolver implements ThirdLoginResolver {

    @Resource
    private ThirdProperties thirdProperties;

    @Resource
    private RestTemplate restTemplate;

    @Override
    public UserResult login(String ticket, String appId) {
        log.info("浙里办微信小程序单点登录: \r\n--> ticket: {}\r\n--> appId: {}", ticket, appId);
        UserResult weChatLogin = zlbWeChatLogin(ticket, appId);
        log.info("浙里办微信小程序单点登录结果返回:{}", weChatLogin);
        return weChatLogin;
    }

    /**
     * 微信登录
     *
     * @param ticket 票据
     * @param appId  appId
     * @return 结果
     */
    private UserResult zlbWeChatLogin(String ticket, String appId) {
        Map<String, String> param = new LinkedHashMap<>(2);
        param.put("ticketId", ticket);
        param.put("appId", StringUtils.defaultString(appId, thirdProperties.getAppId()));

        String params = Json.toJson(param);

        HttpHeaders headers = HmacAuthUtil.generateHeader(thirdProperties.getAccessTokenPath(), "POST", thirdProperties.getAccessKey(), thirdProperties.getSecretKey());
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<String>(params, headers);
        ResponseEntity<ZlbWeChatBaseResult<ZlbWeChatAccessTokenResult>> responseEntity = restTemplate.exchange(thirdProperties.getAccessTokenPath(), HttpMethod.POST, request, new ParameterizedTypeReference<ZlbWeChatBaseResult<ZlbWeChatAccessTokenResult>>() {
        });
        ZlbWeChatBaseResult<ZlbWeChatAccessTokenResult> body = responseEntity.getBody();
        HttpHeaders header = HmacAuthUtil.generateHeader(thirdProperties.getUserInfoPath(), "POST", thirdProperties.getAccessKey(), thirdProperties.getSecretKey());
        header.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> map = new HashMap<>();
        map.put("token", body.getData().getAccessToken());
        log.info("获取到的AccessToken:{}", body.getData().getAccessToken());
        String param1 = Json.toJson(map);
        log.info(param1);
        HttpEntity<String> httpEntity = new HttpEntity<>(param1, header);
        ResponseEntity<ZlbWeChatBaseResult<ZlbWeChatUserInfoResult>> result = restTemplate.exchange(thirdProperties.getUserInfoPath(), HttpMethod.POST, httpEntity, new ParameterizedTypeReference<ZlbWeChatBaseResult<ZlbWeChatUserInfoResult>>() {
        });
        ZlbWeChatUserInfoResult.PersonInfo personInfo = result.getBody().getData().getPersonInfo();
        log.info("浙里办微信小程序单点登录用户信息:{}", personInfo);
        return new UserResult().setUsername(personInfo.getUserName()).setExt(BeanMap.create(personInfo));
    }
}

package com.chua.starter.oauth.server.support.third;

import com.chua.common.support.annotations.Extension;
import com.chua.common.support.json.Json;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.chua.starter.oauth.server.support.third.paramter.BaseParameter;
import com.chua.starter.oauth.server.support.third.paramter.TicketValidationParameter;
import com.chua.starter.oauth.server.support.third.paramter.TokenUserInfoParameter;
import com.chua.starter.oauth.server.support.third.properties.ThirdProperties;
import com.chua.starter.oauth.server.support.third.result.TicketValidationResult;
import com.chua.starter.oauth.server.support.third.result.TokenUserInfoResult;
import com.chua.starter.oauth.server.support.third.utils.UrlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.cglib.beans.BeanMap;
import org.springframework.http.*;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 浙里办登录
 *
 * @author CH
 */
@Slf4j
@Extension("zlb")
public class ZlbThirdLoginResolver implements ThirdLoginResolver {

    @Resource
    private ThirdProperties thirdProperties;

    @Resource
    private RestTemplate restTemplate;

    @Override
    public UserResult login(String ticket, String appId) {
        TicketValidationParameter ticketParameter = new TicketValidationParameter();
        ticketParameter.setTicket(ticket);
        TicketValidationResult ticketResult = simpleTicketValidation(ticketParameter);

        TokenUserInfoParameter infoParameter = new TokenUserInfoParameter();
        infoParameter.setToken(ticketResult.getToken());
        TokenUserInfoResult userInfoByToken = getUserInfoByToken(infoParameter);
        return new UserResult().setUsername(userInfoByToken.getUsername()).setExt(BeanMap.create(userInfoByToken));
    }

    /**
     * 获取用戶結果
     *
     * @param parameter 参数
     * @return 结果
     */
    public TokenUserInfoResult getUserInfoByToken(TokenUserInfoParameter parameter) {
        String apiCode = "atg.biz.resultful.simpleauth.getuserinfo";
        setBaseParam(parameter);
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("servicecode", thirdProperties.getServiceCode());
        params.add("method", "getUserInfo");
        params.add("time", parameter.getTime());
        params.add("sign", parameter.getSign());
        params.add("token", parameter.getToken());
        params.add("datatype", parameter.getDatatype());
        String body = postRequest(apiCode, params, thirdProperties.getGetUserInfoByToken());
        return Json.fromJson(body, TokenUserInfoResult.class);
    }


    /**
     * 浙里办校验
     *
     * @param parameter 参数
     * @return 结果
     */
    public TicketValidationResult simpleTicketValidation(TicketValidationParameter parameter) {
        this.setBaseParam(parameter);
        MultiValueMap<String, String> params = new LinkedMultiValueMap();
        params.add("servicecode", thirdProperties.getServiceCode());
        params.add("method", "ticketValidation");
        params.add("time", parameter.getTime());
        params.add("sign", parameter.getSign());
        params.add("st", parameter.getTicket());
        params.add("datatype", parameter.getDatatype());
        String body = this.postRequest(params, thirdProperties.getTicketValidation());
        return Json.fromJson(body, TicketValidationResult.class);
    }

    /**
     * post请求
     *
     * @param apiCode code
     * @param params  参数
     * @param url     地址
     * @return 结果
     */
    protected String postRequest(String apiCode, MultiValueMap<String, String> params, String url) {
        log.info("请求体:{}", params);
        HttpHeaders httpHeaders = UrlUtils.generateHeader(apiCode, url, "POST", thirdProperties.getServiceCode(), thirdProperties.getServicePassword());
        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(params, httpHeaders);
        log.info("浙里办接口请求地址:{}", url);
        ResponseEntity<String> forEntity = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
        Assert.isTrue(forEntity.getStatusCode().equals(HttpStatus.OK), "请求浙里办失败");
        log.info("浙里办接口请求返回内容:{}", forEntity);
        return forEntity.getBody();
    }

    /**
     * post请求
     *
     * @param params 参数
     * @param url    地址
     * @return 结果
     */
    protected String postRequest(MultiValueMap<String, String> params, String url) {
        log.info("请求体:{}", params);
        HttpHeaders httpHeaders = UrlUtils.generateHeader(url, "POST", thirdProperties.getServiceCode(), thirdProperties.getServicePassword());
        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(params, httpHeaders);
        log.info("浙里办接口请求地址:{}", url);
        ResponseEntity<String> forEntity = this.restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class, new Object[0]);
        Assert.isTrue(forEntity.getStatusCode().equals(HttpStatus.OK), "请求浙里办失败");
        log.info("浙里办接口请求返回内容:{}", forEntity);
        return forEntity.getBody();
    }

    protected void setBaseParam(BaseParameter parameter) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        parameter.setDatatype("json");
        parameter.setSign(this.sign(time));
        parameter.setTime(time);
        parameter.setServicecode(thirdProperties.getServiceCode());
    }

    protected String sign(String time) {
        return DigestUtils.md5Hex(thirdProperties.getServiceCode() + thirdProperties.getServicePassword() + time);
    }
}

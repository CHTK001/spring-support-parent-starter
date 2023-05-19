package com.chua.starter.oauth.server.support.third.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 浙里办
 *
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = "plugin.oauth.third", ignoreInvalidFields = true)
public class ThirdProperties {
    private String appId;
    private String accessKey;
    private String secretKey;
    private String serviceCode;
    private String servicePassword;
    private String ssoUrl = "https://puser.zjzwfw.gov.cn/sso/servlet/organduser";
    private String authUrl = "https://ibcdsg.zj.gov.cn:8443/restapi/prod/IC33000020220228000002/sso/servlet/simpleauth";
    private String aduitUrl = "https://unibase.zjzwfw.gov.cn:7006/Authentication/getAduitResult";
    private String aduitUrlTest = "http://unibase.zjzwfw.gov.cn:5006/Authentication/getAduitResult";
    private String ticketValidation = "https://ibcdsg.zj.gov.cn:8443/restapi/prod/IC33000020220228000002/sso/servlet/simpleauth";
    private String getUserInfoByToken = "https://ibcdsg.zj.gov.cn:8443/restapi/prod/IC33000020220228000004/sso/servlet/simpleauth";
    /**
     * 政务外网环境:https://bcdsg.zj.gov.cn:8443/restapi/prod/IC33000020220329000007/uc/sso/access_token
     */
    private String accessTokenPath = "https://ibcdsg.zj.gov.cn:8443/restapi/prod/IC33000020220329000007/uc/sso/access_token";

    /**
     * 政务外网环境:https://bcdsg.zj.gov.cn:8443/restapi/prod/IC33000020220329000008/uc/sso/getUserInfo
     */
    private String userInfoPath = "https://ibcdsg.zj.gov.cn:8443/restapi/prod/IC33000020220329000008/uc/sso/getUserInfo";


}

package com.chua.starter.oauth.client.support.properties;

import com.google.common.collect.Sets;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * 客户端配置
 *
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = AuthClientProperties.PRE, ignoreInvalidFields = true)
public class AuthClientProperties {

    public AuthClientProperties() {
        whitelist.add("/**/captcha");
        whitelist.add("/**/login");
        whitelist.add("/**/logout");
        whitelist.add("/doc.html");
        whitelist.add("/actuator");
        whitelist.add("/webjars/**");
        whitelist.add("/**/users/loginCode");
        whitelist.add("/v3/api-docs/**");
    }

    public static final String PRE = "plugin.oauth";
    /**
     * 链接超时
     */
    private long connectTimeout = 5000;

    /**
     * 客户端缓存鉴权信息(用于提高访问效率)(s)
     */
    private long cacheTimeout = 60 * 60;
    /**
     * 是否开启客户端缓存冷热备份
     */
    private boolean cacheHotColdBackup = true;
    /**
     * 鉴权地址
     */
    private String address;
    /**
     * 登录地址
     */
    private String loginAddress = "${plugin.oauth.auth-address:}";
    /**
     * ak
     */
    private String accessKey;
    /**
     * sk
     */
    private String secretKey;
    /**
     * 服务序列
     */
    private String serviceKey = "D518E462DF7B36828FA68CCD69FC6140";
    /**
     * 加密方式
     */
    private String encryption = "aes";

    /**
     * 拦截地址
     */
    private Set<String> blockAddress = Sets.newHashSet("/*");

    /**
     * 白名单
     */
    private List<String> whitelist = new LinkedList<>();

    /**
     * 协议
     */
    private String protocol = "http";
    /**
     * token-name
     */
    private String tokenName = "x-oauth-token";
    /**
     * 登录页
     */
    private String loginPage = "/login";
    /**
     * 登出页
     */
    private String logoutPage = "/logout";
    /**
     * 鉴权地址
     */
    private String oauthUrl = "/oauth";
    /**
     * 均衡模式
     */
    private String balance = "polling";

    /**
     * 无权限页面
     */
    private String noPermissionPage = "/oauth-page";

    private TempUser  temp = new TempUser();

    @Data
    public static class TempUser {
        private boolean open;

        private String menuPath;
        /**
         * 临时账号(只用于账号类型为Embed)
         */
        private String user = "guest:guest;ops:opsAdmin2023";
    }
}

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

    /**
     * 是否启用
     */
    private boolean enable = true;

    public static final String PRE = "plugin.oauth";

    public AuthClientProperties() {
        whitelist.add("/**/captcha");
        whitelist.add("/**/login");
        whitelist.add("/**/oauth");
        whitelist.add("/**/doLogin");
        whitelist.add("/**/logout");
        whitelist.add("/doc.html");
        whitelist.add("/actuator");
        whitelist.add("/actuator/**");
        whitelist.add("/report");
        whitelist.add("/modules/**");
        whitelist.add("/markdown/**");
        whitelist.add("/v1/sys/setting");
        whitelist.add("/static/**");
        whitelist.add("/v1/file/**");
        whitelist.add("/**/*.html");
        whitelist.add("/**/users/loginCode");
        whitelist.add("/v3/api-docs/**");
        whitelist.add("/**/node/receive_push");
    }
    /**
     * 链接超时
     */
    private long connectTimeout = 5000;

    /**
     * 客户端缓存鉴权信息(用于提高访问效率)(s)
     */
    private long cacheTimeout =  6 * 60 * 60;
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
     * 加密方式
     */
    private String encryption = "aes";

    /**
     * 是否启用数据加密
     */
    private boolean enableEncryption = true;

    /**
     * 拦截地址
     */
    private Set<String> blockAddress = Sets.newHashSet("/*");

    /**
     * 白名单
     */
    private List<String> whitelist = new LinkedList<>();

    /**
     * 协议, http, http-lite, static
     */
    private String protocol = "http";
    /**
     * token-name
     */
    private String tokenName = "x-oauth-token";
    /**
     * token 别名
     */
    private String alias = "Boren-Token";
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

    /**
     * 密钥
     */
    private Aksk key = new Aksk();
    /**
     * 临时用户
     */
    private TempUser temp = new TempUser();

    /**
     * 密钥
     */
    @Data
    public static class Aksk {
        /**
         * ak
         */
        private String accessKey;
        /**
         * sk
         */
        private String secretKey;
    }

    @Data
    public static class TempUser {
        /**
         * 是否启用临时用户
         */
        private boolean open;

        private String menuPath;
        /**
         * 临时账号(只用于账号类型为Embed)
         */
        private String user = "guest:guest;ops:opsAdmin2023;admin:admin@123!456";
    }
}

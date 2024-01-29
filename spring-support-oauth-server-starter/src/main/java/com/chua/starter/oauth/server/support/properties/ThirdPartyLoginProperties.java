package com.chua.starter.oauth.server.support.properties;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 三方绑定
 * @author CH
 */
@EqualsAndHashCode(callSuper = false)
@Data
@ConfigurationProperties(prefix = ThirdPartyLoginProperties.PRE, ignoreUnknownFields = false)
public class ThirdPartyLoginProperties {

    public static final String PRE = "plugin.oauth.third.login";

    private Gitee gitee = new Gitee();

    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class Gitee extends Base{
        public Gitee() {
            this.setClientId("f0d999a2e91e9a4edcec329fe7e6b3f1fa8b7063f8a618c3c7a2e0d0a13d5943");
            this.setClientSecret("158ced91fe30cfa06798c638d2b477d54e57b623a7e869c42d34d3125e88f1b7");
            this.setRedirectUri("http://127.0.0.1:19180/oauth/gitee/login/callback");
        }
    }

    @Data
    public static class Base {

        /**
         * 客户端id：对应各平台的appKey
         */
        private String clientId;

        /**
         * 客户端Secret：对应各平台的appSecret
         */
        private String clientSecret;

        /**
         * 登录成功后的回调地址
         */
        private String redirectUri;
    }
}

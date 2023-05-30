package com.chua.starter.oauth.server.support.properties;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 三方登录
 * @author CH
 */
@EqualsAndHashCode(callSuper = false)
@Data
@ConfigurationProperties(prefix = ThirdPartyProperties.PRE, ignoreUnknownFields = false)
public class ThirdPartyProperties {

    public static final String PRE = "plugin.oauth.third";

    private Gitee gitee = new Gitee();

    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class Gitee extends Base{
        public Gitee() {
            this.setClientId("5117d2bf0e3f32c866afb7dd752cdcbc7947dafdb31bf7d9dc47bc4839609c50");
            this.setClientSecret("d467db8847cad16b1924c1210cb073b52a44b3370ea2d227b2a52957c2b6a42e");
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
        private String redirectUri = "http://127.0.0.1:19180/third-index";
    }
}

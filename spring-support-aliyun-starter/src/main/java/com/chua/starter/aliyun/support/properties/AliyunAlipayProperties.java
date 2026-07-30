package com.chua.starter.aliyun.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * 支付宝属性配置
 *
 * @author CH
 * @since 2026-03-19
 */
@Data
@ConfigurationProperties(prefix = AliyunAlipayProperties.PREFIX, ignoreInvalidFields = true)
public class AliyunAlipayProperties {

    public static final String PREFIX = "plugin.aliyun.alipay";

    private boolean enable = false;

    private boolean enabled = false;

    private boolean sandbox = false;

    private String serverUrl;

    private String appId;

    private String privateKey;

    private String alipayPublicKey;

    private String notifyUrl;

    private String returnUrl;

    private String format = "json";

    private String charset = "UTF-8";

    private String signType = "RSA2";

    private Integer connectTimeout = 10000;

    private Integer readTimeout = 30000;

    private Integer maxIdleConnections = 5;

    private Long keepAliveDuration = 300000L;

    public String resolveServerUrl() {
        if (StringUtils.hasText(serverUrl)) {
            return serverUrl;
        }
        return sandbox
                ? "https://openapi-sandbox.dl.alipaydev.com/gateway.do"
                : "https://openapi.alipay.com/gateway.do";
    }
}

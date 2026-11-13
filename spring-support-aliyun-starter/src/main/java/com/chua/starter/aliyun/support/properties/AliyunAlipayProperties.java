package com.chua.starter.aliyun.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * 支付宝属性配置
 *
 * @since 2026-03-19
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = AliyunAlipayProperties.PREFIX, ignoreInvalidFields = true)
public class AliyunAlipayProperties {

    /**
     * 前缀
    */
    public static final String PREFIX = "plugin.aliyun.alipay";

    /**
     * Enable
    */
    private boolean enable = false;

    /**
     * 是否启用
    */
    private boolean enabled = false;

    /**
     * Sandbox
    */
    private boolean sandbox = false;

    /**
     * 服务器URL
    */
    private String serverUrl;

    /**
     * APPID
    */
    private String appId;

    /**
     * 私有密钥
    */
    private String privateKey;

    /**
     * Alipay公开密钥
    */
    private String alipayPublicKey;

    /**
     * 通知url
    */
    private String notifyUrl;

    /**
     * 返回值URL
    */
    private String returnUrl;

    /**
     * 格式
    */
    private String format = "json";

    /**
     * 字符集
    */
    private String charset = "UTF-8";

    /**
     * 标志类型
    */
    private String signType = "RSA2";

    /**
     * 连接超时
    */
    // [P3C A20 豁免]
    private Integer connectTimeout = 10000;

    /**
     * 读取超时
    */
    // [P3C A20 豁免]
    private Integer readTimeout = 30000;

    /**
     * 最大值idleconnections
    */
    private Integer maxIdleConnections = 5;

    /**
     * Keepalive持续时间
    */
    // [P3C A20 豁免]
    private Long keepAliveDuration = 300000L;

    /**
     * 解析服务端url
     *
     * @return resolve服务端url的结果
     */
    public String resolveServerUrl() {
        if (StringUtils.hasText(serverUrl)) {
            return serverUrl;
        }
        return sandbox
                ? "https://openapi-sandbox.dl.alipaydev.com/gateway.do"
                : "https://openapi.alipay.com/gateway.do";
    }
}

package com.chua.starter.tencent.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 微信支付属性配置
 *
 * @author CH
 * @since 2026-03-19
 */
@Data
@ConfigurationProperties(prefix = TencentWechatPayProperties.PREFIX, ignoreInvalidFields = true)
public class TencentWechatPayProperties {

    public static final String PREFIX = "plugin.tencent.wechat-pay";

    private boolean enable = false;

    private boolean enabled = false;

    private String appId;

    private String merchantId;

    private String privateKey;

    private String merchantSerialNumber;

    private String apiV3Key;

    private String notifyUrl;
}

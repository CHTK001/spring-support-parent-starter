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

    /**
     * 沙箱模式
     * <p>true = 使用沙箱环境 (https://api.mch.weixin.qq.com/sandboxnew/)</p>
     */
    private boolean sandbox = false;

    /**
     * 沙箱环境 APIv3 密钥
     * <p>沙箱环境需要使用 sandbox_signkey 替代生产环境的 apiV3Key。
     * 可通过 {@code https://api.mch.weixin.qq.com/sandboxnew/pay/getsignkey} 获取。</p>
     */
    private String sandboxSignKey;
}

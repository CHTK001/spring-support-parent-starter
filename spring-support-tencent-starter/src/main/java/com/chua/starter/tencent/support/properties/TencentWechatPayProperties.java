package com.chua.starter.tencent.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
* 微信支付属性配置
*
* @since 2026-03-19
* @author CH
 */
@Data
@ConfigurationProperties(prefix = TencentWechatPayProperties.PREFIX, ignoreInvalidFields = true)
public class TencentWechatPayProperties {

    /** 前缀 */
    public static final String PREFIX = "plugin.tencent.wechat-pay";

    /** Enable */
    private boolean enable = false;

    /** 是否启用 */
    private boolean enabled = false;

    /** APPID */
    private String appId;

    /** merchantid */
    private String merchantId;

    /** 私有密钥 */
    private String privateKey;

    /** Merchantserial数字 */
    private String merchantSerialNumber;

    /** apiv3钥匙 */
    private String apiV3Key;

    /** 通知url */
    private String notifyUrl;

    /**
    * 沙箱模式
    * <p>true = 使用沙箱环境 (https://api.mch.weixin.qq.com/sandboxnew/)</p>
     */
    private boolean sandbox = false;

    /**
    * 沙箱环境 apiv3 密钥
    * <p>沙箱环境需要使用 sandbox_signkey 替代生产环境的 apiV3Key。
    * 可通过 {@code https://api.mch.weixin.qq.com/sandboxnew/pay/getsignkey} 获取。</p>
     */
    private String sandboxSignKey;
}

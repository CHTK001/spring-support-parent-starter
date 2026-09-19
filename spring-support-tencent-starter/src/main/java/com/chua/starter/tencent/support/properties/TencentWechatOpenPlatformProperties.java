package com.chua.starter.tencent.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 微信开放平台第三方平台配置
 * <p>
 * 用于小程序openid到公众号openid的转换等功能。
 *
 * @author CH
 * @since 4.0.0
 */
@Data
@ConfigurationProperties(prefix = TencentWechatOpenPlatformProperties.PREFIX, ignoreInvalidFields = true)
public class TencentWechatOpenPlatformProperties {

    /** 前缀 */
    public static final String PREFIX = "plugin.tencent.open-platform";

    /**
    * 是否启用
    */
    private boolean enable = false;

    /**
     * 第三方平台appid
     */
    private String componentAppId;

    /**
     * 第三方平台appsecret
     */
    private String componentAppSecret;

    /**
     * 默认小程序appid（可选，调用转换接口时可省略miniappid参数）
     */
    private String defaultMiniAppId;

    /**
     * 数据存储路径（JSON文件），用于持久化openid映射关系
     */
    private String storagePath = "wechat-openid-mapping.json";
}

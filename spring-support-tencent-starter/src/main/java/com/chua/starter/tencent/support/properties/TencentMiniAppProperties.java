package com.chua.starter.tencent.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 腾讯小程序配置
 *
 * @since 2024/12/2
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = TencentMiniAppProperties.PREFIX, ignoreInvalidFields = true)
public class TencentMiniAppProperties {

    /** Prefix */
    public static final String PREFIX = "plugin.tencent.mini-app";

    /**
     * 是否启用
     */
    private boolean enable = false;

    /**
     * 是否启用（兼容字段）
     */
    private boolean enabled = false;

    /**
     * 小程序appId
     */
    private String appId;

    /**
     * 小程序appSecret
     */
    private String appSecret;
}

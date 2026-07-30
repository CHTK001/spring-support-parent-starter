package com.chua.starter.tencent.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 腾讯小程序配置
 *
 * @author CH
 * @since 2024/12/2
 */
@Data
@ConfigurationProperties(prefix = TencentMiniAppProperties.PREFIX, ignoreInvalidFields = true)
public class TencentMiniAppProperties {

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

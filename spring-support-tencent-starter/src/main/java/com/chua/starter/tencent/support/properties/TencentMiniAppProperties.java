package com.chua.starter.tencent.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 腾讯小程序配置。
 *
 * <p>配置前缀：{@code plugin.tencent.mini-app}。<b>注意：当前所有字段均无消费方</b>——
 * 小程序登录走 {@code WECHAT_MINI} SPI（{@code WechatMiniUserInfoService}），不读取本配置；
 * 两个开关也是死开关。本配置类保留用于后续扩展。</p>
 *
 * <p>完整 YAML 配置示例：</p>
 * <pre>{@code
 * plugin:
 *   tencent:
 *     mini-app:
 *       enable: false            # 死开关，无代码读取
 *       enabled: false           # 兼容字段，无代码读取
 *       app-id: your-mini-app-id       # 当前无消费方
 *       app-secret: your-mini-app-secret   # 当前无消费方
 * }</pre>
 *
 * @since 2024/12/2
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = TencentMiniAppProperties.PREFIX, ignoreInvalidFields = true)
public class TencentMiniAppProperties {

    /**
     * 前缀
    */
    public static final String PREFIX = "plugin.tencent.mini-app";

    /**
     * 是否启用。
     * <p>含义：预留开关；当前为死开关，无任何代码读取。</p>
     * <p>默认值：{@code false}。</p>
     * <pre>{@code
     * plugin:
     *   tencent:
     *     mini-app:
     *       enable: false
     * }</pre>
     */
    private boolean enable = false;

    /**
     * 是否启用（兼容字段）。
     * <p>含义：与 {@link #enable} 同义的兼容命名；当前无任何代码读取。</p>
     * <p>默认值：{@code false}。</p>
     * <pre>{@code
     * plugin:
     *   tencent:
     *     mini-app:
     *       enabled: false
     * }</pre>
     */
    private boolean enabled = false;

    /**
     * 小程序appid。
     * <p>含义：小程序 AppID。当前无消费方：小程序登录走 {@code WECHAT_MINI} SPI，凭据由外部登录组件管理。</p>
     * <p>默认值：{@code null}（未配置）。</p>
     * <pre>{@code
     * plugin:
     *   tencent:
     *     mini-app:
     *       app-id: your-mini-app-id
     * }</pre>
     */
    private String appId;

    /**
     * 小程序appsecret。
     * <p>含义：小程序 AppSecret。当前无消费方。如启用请经环境变量/配置中心注入。</p>
     * <p>默认值：{@code null}（未配置）。</p>
     * <pre>{@code
     * plugin:
     *   tencent:
     *     mini-app:
     *       app-secret: your-mini-app-secret
     * }</pre>
     */
    private String appSecret;
}

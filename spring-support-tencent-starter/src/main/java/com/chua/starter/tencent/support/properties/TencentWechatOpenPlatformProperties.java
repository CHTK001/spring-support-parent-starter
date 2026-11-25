package com.chua.starter.tencent.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 微信开放平台第三方平台配置。
 *
 * <p>配置前缀：{@code plugin.tencent.open-platform}。用于小程序 openid 到公众号 openid 的转换等功能；
 * {@code enable=true} 时装配 {@code wechatOpenPlatformService} 与 {@code openIdConvertService}
 * 两个占位 Bean（均为 {@code @ConditionalOnMissingBean}，宿主可自行实现覆盖）。</p>
 *
 * <p>完整 YAML 配置示例（凭据请经环境变量/配置中心注入，示例中均为占位符）：</p>
 * <pre>{@code
 * plugin:
 *   tencent:
 *     open-platform:
 *       enable: false                              # true 才装配开放平台占位 Bean
 *       component-app-id: your-component-app-id    # 第三方平台 AppID
 *       component-app-secret: your-component-app-secret   # 第三方平台 AppSecret
 *       default-mini-app-id: your-default-mini-app-id     # 默认小程序 AppID（可选）
 *       storage-path: wechat-openid-mapping.json   # openid 映射存储路径
 * }</pre>
 *
 * @author CH
 * @since 4.0.0
 */
@Data
@ConfigurationProperties(prefix = TencentWechatOpenPlatformProperties.PREFIX, ignoreInvalidFields = true)
public class TencentWechatOpenPlatformProperties {

    /**
     * 前缀
    */
    public static final String PREFIX = "plugin.tencent.open-platform";

    /**
     * 是否启用。
     * <p>含义：开放平台占位 Bean 的装配开关；{@code true} 时 {@code TencentConfiguration} 才会装配
     * {@code wechatOpenPlatformService} 与 {@code openIdConvertService}。</p>
     * <p>默认值：{@code false}（不装配）。</p>
     * <pre>{@code
     * plugin:
     *   tencent:
     *     open-platform:
     *       enable: false
     * }</pre>
     */
    private boolean enable = false;

    /**
     * 第三方平台appid。
     * <p>含义：微信开放平台第三方平台的 AppID；当前在 Bean 初始化日志中打印，并作为占位实现的构造入参。</p>
     * <p>默认值：{@code null}（未配置）。</p>
     * <pre>{@code
     * plugin:
     *   tencent:
     *     open-platform:
     *       component-app-id: your-component-app-id
     * }</pre>
     */
    private String componentAppId;

    /**
     * 第三方平台appsecret。
     * <p>含义：微信开放平台第三方平台的 AppSecret；作为占位实现的构造入参。</p>
     * <p>默认值：{@code null}（未配置）。请经环境变量/配置中心注入，避免明文落盘。</p>
     * <pre>{@code
     * plugin:
     *   tencent:
     *     open-platform:
     *       component-app-secret: your-component-app-secret
     * }</pre>
     */
    private String componentAppSecret;

    /**
     * 默认小程序appid。
     * <p>含义：默认小程序 AppID（可选），调用 openid 转换接口时可省略 miniappid 参数；
     * 作为 {@code openIdConvertService} 的构造入参。</p>
     * <p>默认值：{@code null}（未配置）。</p>
     * <pre>{@code
     * plugin:
     *   tencent:
     *     open-platform:
     *       default-mini-app-id: your-default-mini-app-id
     * }</pre>
     */
    private String defaultMiniAppId;

    /**
     * 数据存储路径。
     * <p>含义：openid 映射关系 JSON 文件的存储路径，用于持久化映射关系；当前持久化尚未实现，
     * 该值仅传入 {@code DefaultWechatOpenPlatformService} 并在启动日志中打印。</p>
     * <p>默认值：{@code wechat-openid-mapping.json}（相对工作目录）。</p>
     * <pre>{@code
     * plugin:
     *   tencent:
     *     open-platform:
     *       storage-path: /data/wechat-openid-mapping.json
     * }</pre>
     */
    private String storagePath = "wechat-openid-mapping.json";
}

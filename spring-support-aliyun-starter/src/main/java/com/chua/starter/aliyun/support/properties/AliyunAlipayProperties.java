package com.chua.starter.aliyun.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * 支付宝属性配置（配置前缀 {@value #PREFIX}，非法绑定字段自动忽略）。
 *
 * <p>完整 YAML 配置示例（所有键均可省略，省略时取各字段默认值）：
 *
 * <pre>{@code
 * plugin:
 *   aliyun:
 *     alipay:
 *       enabled: true                                   # 总开关：true 才注册 AlipayClient Bean（默认 false）
 *       # enable: false                                 # 历史兼容字段，无代码读取，配置不生效
 *       sandbox: true                                   # 沙箱联调；server-url 为空时决定生产/沙箱网关（默认 false）
 *       # server-url: https://openapi.alipay.com/gateway.do   # 显式网关地址，优先级高于 sandbox
 *       app-id: your-app-id                             # 支付宝应用 APPID
 *       private-key: your-private-key                   # 应用私钥（请求签名）
 *       alipay-public-key: your-alipay-public-key       # 支付宝公钥（响应验签 + 通知验签共用）
 *       # notify-url: https://example.com/pay/notify    # 声明项：网关不读取，以请求级 notifyUrl 为准
 *       # return-url: https://example.com/pay/return    # 声明项：网关不读取，以请求级 returnUrl 为准
 *       format: json                                    # 报文格式（默认 json）
 *       charset: UTF-8                                  # 字符集（默认 UTF-8，通知验签兜底值）
 *       sign-type: RSA2                                 # 签名类型（默认 RSA2，通知验签兜底值）
 *       connect-timeout: 10000                          # 连接超时毫秒（默认 10000，null 时不设置）
 *       read-timeout: 30000                             # 读超时毫秒（默认 30000，null 时不设置）
 *       max-idle-connections: 5                         # 连接池最大空闲连接数（默认 5，null 时不设置）
 *       keep-alive-duration: 300000                     # 连接保活时长毫秒（默认 300000，null 时不设置）
 * }</pre>
 *
 * <p>网关地址由 {@link #resolveServerUrl()} 解析：显式 {@code server-url} 优先，
 * 否则按 {@code sandbox} 回落到生产 {@code https://openapi.alipay.com/gateway.do}
 * 或沙箱 {@code https://openapi-sandbox.dl.alipaydev.com/gateway.do}。
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
     * 历史兼容开关：当前无任何代码读取，配置不生效；实际生效的总开关是 {@link #enabled}。
     *
     * <p>默认值：{@code false}。
     * <p>YAML：{@code plugin.aliyun.alipay.enable: false}
     */
    private boolean enable = false;

    /**
     * 是否启用支付宝客户端自动装配：为 {@code true} 时才注册 {@code AlipayClient} Bean
     * （{@code @ConditionalOnProperty} 门控）；{@code AliyunAlipayGateway} SPI 调用不受此开关限制。
     *
     * <p>默认值：{@code false}。
     * <p>YAML：{@code plugin.aliyun.alipay.enabled: true}
     */
    private boolean enabled = false;

    /**
     * 是否使用支付宝沙箱环境：{@code server-url} 为空时，{@code true} 走沙箱网关
     * {@code https://openapi-sandbox.dl.alipaydev.com/gateway.do}，{@code false} 走生产网关。
     *
     * <p>默认值：{@code false}。
     * <p>YAML：{@code plugin.aliyun.alipay.sandbox: true}
     */
    private boolean sandbox = false;

    /**
     * 显式网关地址，优先级高于 {@link #sandbox}；为空时由 {@link #resolveServerUrl()}
     * 按 {@code sandbox} 回落到生产或沙箱网关。
     *
     * <p>默认值：{@code null}（回落 {@code https://openapi.alipay.com/gateway.do} 或沙箱网关）。
     * <p>YAML：{@code plugin.aliyun.alipay.server-url: https://openapi.alipay.com/gateway.do}
     */
    private String serverUrl;

    /**
     * 支付宝开放平台应用 APPID，创建客户端时写入 {@code AlipayConfig#setAppId}；
     * 为空时发起支付宝调用会失败。
     *
     * <p>默认值：{@code null}。
     * <p>YAML：{@code plugin.aliyun.alipay.app-id: your-app-id}
     */
    private String appId;

    /**
     * 应用私钥（用于请求签名），创建客户端时写入 {@code AlipayConfig#setPrivateKey}。
     *
     * <p>默认值：{@code null}。
     * <p>YAML：{@code plugin.aliyun.alipay.private-key: your-private-key}
     */
    private String privateKey;

    /**
     * 支付宝公钥：创建客户端时写入 {@code AlipayConfig#setAlipayPublicKey} 用于响应验签，
     * 并作为异步通知验签（{@code AlipaySignature.rsaCheckV1}）的验签公钥。
     *
     * <p>默认值：{@code null}。
     * <p>YAML：{@code plugin.aliyun.alipay.alipay-public-key: your-alipay-public-key}
     */
    private String alipayPublicKey;

    /**
     * 异步通知地址（声明项）：网关不读取该配置，实际通知地址以
     * {@code AliyunAlipayPayRequest#notifyUrl} 请求级字段为准。
     *
     * <p>默认值：{@code null}。
     * <p>YAML：{@code plugin.aliyun.alipay.notify-url: https://example.com/pay/notify}
     */
    private String notifyUrl;

    /**
     * 支付完成跳转地址（声明项）：网关不读取该配置，实际跳转地址以
     * {@code AliyunAlipayPayRequest#returnUrl} 请求级字段为准（App 支付不设置 returnUrl）。
     *
     * <p>默认值：{@code null}。
     * <p>YAML：{@code plugin.aliyun.alipay.return-url: https://example.com/pay/return}
     */
    private String returnUrl;

    /**
     * 报文格式，创建客户端时写入 {@code AlipayConfig#setFormat}。
     *
     * <p>默认值：{@code json}。
     * <p>YAML：{@code plugin.aliyun.alipay.format: json}
     */
    private String format = "json";

    /**
     * 报文字符集，创建客户端时写入 {@code AlipayConfig#setCharset}；异步通知验签时作为
     * {@code charset} 兜底值（优先级：通知参数 &gt; 该配置 &gt; {@code UTF-8}）。
     *
     * <p>默认值：{@code UTF-8}。
     * <p>YAML：{@code plugin.aliyun.alipay.charset: UTF-8}
     */
    private String charset = "UTF-8";

    /**
     * 签名类型，创建客户端时写入 {@code AlipayConfig#setSignType}；异步通知验签时作为
     * {@code sign_type} 兜底值（优先级：通知参数 &gt; 该配置 &gt; {@code RSA2}）。
     *
     * <p>默认值：{@code RSA2}。
     * <p>YAML：{@code plugin.aliyun.alipay.sign-type: RSA2}
     */
    private String signType = "RSA2";

    /**
     * 连接超时（毫秒），仅在该值非 {@code null} 时写入 {@code AlipayConfig#setConnectTimeout}。
     *
     * <p>默认值：{@code 10000}；配置为 {@code null} 时不设置，回落支付宝 SDK 默认值。
     * <p>YAML：{@code plugin.aliyun.alipay.connect-timeout: 10000}
     */
    // [P3C A20 豁免]
    private Integer connectTimeout = 10000;

    /**
     * 读取超时（毫秒），仅在该值非 {@code null} 时写入 {@code AlipayConfig#setReadTimeout}。
     *
     * <p>默认值：{@code 30000}；配置为 {@code null} 时不设置，回落支付宝 SDK 默认值。
     * <p>YAML：{@code plugin.aliyun.alipay.read-timeout: 30000}
     */
    // [P3C A20 豁免]
    private Integer readTimeout = 30000;

    /**
     * HTTP 连接池最大空闲连接数，仅在该值非 {@code null} 时写入 {@code AlipayConfig#setMaxIdleConnections}。
     *
     * <p>默认值：{@code 5}；配置为 {@code null} 时不设置，回落支付宝 SDK 默认值。
     * <p>YAML：{@code plugin.aliyun.alipay.max-idle-connections: 5}
     */
    private Integer maxIdleConnections = 5;

    /**
     * HTTP 连接保活时长（毫秒），仅在该值非 {@code null} 时写入 {@code AlipayConfig#setKeepAliveDuration}。
     *
     * <p>默认值：{@code 300000}（5 分钟）；配置为 {@code null} 时不设置，回落支付宝 SDK 默认值。
     * <p>YAML：{@code plugin.aliyun.alipay.keep-alive-duration: 300000}
     */
    // [P3C A20 豁免]
    private Long keepAliveDuration = 300000L;

    /**
     * 解析支付宝网关地址：显式 {@code serverUrl} 优先；为空时按 {@code sandbox}
     * 回落到生产网关 {@code https://openapi.alipay.com/gateway.do} 或沙箱网关
     * {@code https://openapi-sandbox.dl.alipaydev.com/gateway.do}。
     *
     * @return 支付宝网关地址（生产或沙箱）
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

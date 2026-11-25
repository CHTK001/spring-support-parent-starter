package com.chua.starter.tencent.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 微信支付属性配置。
 *
 * <p>配置前缀：{@code plugin.tencent.wechat-pay}。{@code TencentWechatPayGateway} 的下单、查单、
 * 关单、退款、支付分、分账与支付/退款通知验签均从本配置读取商户凭据；本类同时是网关每个方法的第一入参。</p>
 *
 * <p>完整 YAML 配置示例（凭据请经环境变量/配置中心注入，示例中均为占位符）：</p>
 * <pre>{@code
 * plugin:
 *   tencent:
 *     wechat-pay:
 *       enable: false                             # 总开关（当前未被装配逻辑读取）
 *       enabled: false                            # 兼容字段，未被读取
 *       app-id: your-app-id                       # 公众号/小程序/APP 的 AppID，下单 appid
 *       merchant-id: your-merchant-id             # 商户号 mchid
 *       private-key: your-private-key-content     # 商户 API 私钥内容（非文件路径）
 *       merchant-serial-number: your-merchant-serial-number   # 商户证书序列号
 *       api-v3-key: your-api-v3-key               # APIv3 密钥，通知验签与解密
 *       notify-url: https://example.com/pay/wechat/notify     # 默认通知地址（退款/支付分回退用）
 *       sandbox: false                            # 沙箱开关（当前未接线，网关固定走生产域名）
 *       sandbox-sign-key: your-sandbox-sign-key   # 沙箱 APIv3 密钥（当前未接线）
 * }</pre>
 *
 * @since 2026-03-19
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = TencentWechatPayProperties.PREFIX, ignoreInvalidFields = true)
public class TencentWechatPayProperties {

    /**
     * 前缀
    */
    public static final String PREFIX = "plugin.tencent.wechat-pay";

    /**
     * 总开关。
     * <p>含义：预留的模块启用开关。当前为「死开关」：自动装配与网关均不读取该字段，
     * 置为 {@code true} 不会额外装配任何 Bean；支付能力只要类在 classpath 即可用，是否调用由业务侧决定。</p>
     * <p>默认值：{@code false}。</p>
     * <pre>{@code
     * plugin:
     *   tencent:
     *     wechat-pay:
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
     *     wechat-pay:
     *       enabled: false
     * }</pre>
     */
    private boolean enabled = false;

    /**
     * 公众号/小程序/APP 的 AppID。
     * <p>含义：微信支付下单的 {@code appid}；支付分、分账等接口在请求未携带 appId 时回退到本值
     * （{@code firstNonBlank(request.appId(), properties.getAppId())}）。</p>
     * <p>默认值：{@code null}（未配置）。为空时下单请求缺少 appid，微信支付侧将按参数错误拒绝。</p>
     * <pre>{@code
     * plugin:
     *   tencent:
     *     wechat-pay:
     *       app-id: your-app-id
     * }</pre>
     */
    private String appId;

    /**
     * 商户号。
     * <p>含义：微信支付商户号 {@code mchid}，下单/查单/关单/退款均读取。</p>
     * <p>默认值：{@code null}（未配置）。为空时商户凭据不完整，调用网关方法将被微信支付侧拒绝。</p>
     * <pre>{@code
     * plugin:
     *   tencent:
     *     wechat-pay:
     *       merchant-id: your-merchant-id
     * }</pre>
     */
    private String merchantId;

    /**
     * 商户 API 私钥。
     * <p>含义：商户 API 私钥的<b>内容</b>（PEM 全文），不是文件路径；用于构建
     * {@code RSAAutoCertificateConfig} 完成请求签名与平台证书自动更新。</p>
     * <p>默认值：{@code null}（未配置）。为空时无法构建商户凭据，网关调用直接失败。</p>
     * <pre>{@code
     * plugin:
     *   tencent:
     *     wechat-pay:
     *       private-key: your-private-key-content   # 建议经环境变量注入，如 ${WECHAT_MCH_PRIVATE_KEY}
     * }</pre>
     */
    private String privateKey;

    /**
     * 商户证书序列号。
     * <p>含义：商户 API 证书序列号，与私钥配对使用。</p>
     * <p>默认值：{@code null}（未配置）。为空时无法构建商户凭据，网关调用直接失败；轮换证书时需同步更新。</p>
     * <pre>{@code
     * plugin:
     *   tencent:
     *     wechat-pay:
     *       merchant-serial-number: your-merchant-serial-number
     * }</pre>
     */
    private String merchantSerialNumber;

    /**
     * APIv3 密钥。
     * <p>含义：微信支付 APIv3 密钥（32 位），用于支付/退款/支付分通知的验签与报文解密。</p>
     * <p>默认值：{@code null}（未配置）。为空时无法构建商户凭据，通知解析（验签解密）失败。</p>
     * <pre>{@code
     * plugin:
     *   tencent:
     *     wechat-pay:
     *       api-v3-key: your-api-v3-key   # 建议经环境变量注入，如 ${WECHAT_API_V3_KEY}
     * }</pre>
     */
    private String apiV3Key;

    /**
     * 默认通知地址。
     * <p>含义：微信支付回调通知的默认 {@code notify_url}。仅退款与支付分在请求未携带
     * {@code notifyUrl} 时回退使用（{@code firstNonBlank(request.notifyUrl(), properties.getNotifyUrl())}）；
     * 下单通道只使用请求级 {@code request.notifyUrl()}，不回退到本值。</p>
     * <p>默认值：{@code null}（未配置）。为空且请求也未携带时，退款/支付分请求不带通知地址，微信侧不会回调。</p>
     * <pre>{@code
     * plugin:
     *   tencent:
     *     wechat-pay:
     *       notify-url: https://example.com/pay/wechat/notify
     * }</pre>
     */
    private String notifyUrl;

    /**
     * 沙箱模式开关。
     * <p>含义：预留的沙箱环境开关（{@code https://api.mch.weixin.qq.com/sandboxnew/}）。
     * 当前<b>未接线</b>：无代码读取，网关固定走生产域名 {@code https://api.mch.weixin.qq.com}。</p>
     * <p>默认值：{@code false}。</p>
     * <pre>{@code
     * plugin:
     *   tencent:
     *     wechat-pay:
     *       sandbox: false
     * }</pre>
     */
    private boolean sandbox = false;

    /**
     * 沙箱环境 APIv3 密钥。
     * <p>含义：沙箱环境需使用 {@code sandbox_signkey} 替代生产环境 {@code apiV3Key}，
     * 可通过 {@code https://api.mch.weixin.qq.com/sandboxnew/pay/getsignkey} 获取。
     * 当前<b>未接线</b>：无代码读取。</p>
     * <p>默认值：{@code null}（未配置）。</p>
     * <pre>{@code
     * plugin:
     *   tencent:
     *     wechat-pay:
     *       sandbox-sign-key: your-sandbox-sign-key
     * }</pre>
     */
    private String sandboxSignKey;
}

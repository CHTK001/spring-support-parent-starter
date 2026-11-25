package com.chua.payment.support.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 支付回调地址配置，定义对外可访问的支付服务根地址。
 *
 * <p>配置前缀为 {@code plugin.payment.callback}。</p>
 *
 * <p>完整 YAML 配置示例：</p>
 * <pre>{@code
 * plugin:
 *   payment:
 *     callback:
 *       base-url: https://pay.example.com   # 对外可访问的支付服务根地址（无默认值）
 * }</pre>
 *
 * <p>该值仅作为 {@code pay_global_config.payment_notify_base_url} 的兜底初始化值；
 * 未配置时 {@code PaymentCallbackUrlResolver} 无法生成 scoped 默认回调地址，
 * 需在支付全局配置中显式维护。</p>
 *
 * @author CH
 * @since 4.0.0.50
 */
@Data
@Component
@ConfigurationProperties(prefix = "plugin.payment.callback")
public class PaymentCallbackProperties {

    /**
     * 对外可访问的支付服务根地址，例如：https://pay.example.com
     *
     * <p>含义：拼接微信/支付宝/钱包/支付分等异步通知回调地址时使用的对外根地址，
     * 仅作为 {@code pay_global_config.payment_notify_base_url} 的兜底初始化值。</p>
     * <p>默认值：无（未配置时为 {@code null}，此时 {@code PaymentCallbackUrlResolver}
     * 无法生成 scoped 默认回调地址，需在支付全局配置中显式维护）。</p>
     * <p>YAML 示例：</p>
     * <pre>{@code
     * plugin:
     *   payment:
     *     callback:
     *       base-url: https://pay.example.com
     * }</pre>
     */
    private String baseUrl;
}

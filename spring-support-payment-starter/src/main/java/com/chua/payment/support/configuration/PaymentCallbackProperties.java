package com.chua.payment.support.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 支付回调地址配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "plugin.payment.callback")
public class PaymentCallbackProperties {

    /**
     * 对外可访问的支付服务根地址，例如 https://pay.example.com
     */
    private String baseUrl;
}

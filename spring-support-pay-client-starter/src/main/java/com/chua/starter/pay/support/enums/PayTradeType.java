package com.chua.starter.pay.support.enums;

import com.chua.starter.swagger.support.SwaggerEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 支付类型
 * @author CH
 * @since 2025/10/14 13:41
 */
@Getter
@AllArgsConstructor
public enum PayTradeType implements SwaggerEnum {

    PAY_WALLET("WALLET", "钱包支付"),

    /**
     * 微信支付
     */
    PAY_WECHAT_JS_API("WECHAT_JS_API", "微信支付"),
    /**
     * 微信H5支付
     */
    PAY_WECHAT_H5("WECHAT_H5", "微信H5支付"),

    /**
     * 微信扫码支付
     */
    PAY_WECHAT_NATIVE("WECHAT_NATIVE", "微信扫码支付"),
    /**
     * 微信支付分
     */
    PAY_WECHAT_PAYMENT_POINTS("WECHAT_PAYMENT_POINTS", "微信支付分呢"),
    /**
     * 微信预支付
     */
    PAY_PREPAYMENT("WECHAT_PREPAYMENT",  "微信预支付");

    private final String code;
    private final String name;


    /**
     * 解析
     * @param code code
     * @return PayTradeType
     */
    public static PayTradeType parse(String code) {
        for (PayTradeType value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}

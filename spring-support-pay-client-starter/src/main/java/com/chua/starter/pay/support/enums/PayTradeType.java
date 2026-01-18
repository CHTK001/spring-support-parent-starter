package com.chua.starter.pay.support.enums;

import com.chua.starter.swagger.support.SwaggerEnum;
import lombok.Getter;

/**
 * 支付类型
 * @author CH
 * @since 2025/10/14 13:41
 */
@Getter
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
    PAY_PREPAYMENT("WECHAT_PREPAYMENT",  "微信预支付"),

    /**
     * 支付宝APP支付
     */
    PAY_ALIPAY_APP("ALIPAY_APP", "支付宝APP支付"),

    /**
     * 支付宝网页支付
     */
    PAY_ALIPAY_WAP("ALIPAY_WAP", "支付宝网页支付"),

    /**
     * 支付宝扫码支付
     */
    PAY_ALIPAY_QR_CODE("ALIPAY_QR_CODE", "支付宝扫码支付"),

    /**
     * 支付宝小程序支付
     */
    PAY_ALIPAY_MINI("ALIPAY_MINI", "支付宝小程序支付"),

    /**
     * 云闪付APP支付
     */
    PAY_UNIONPAY_APP("UNIONPAY_APP", "云闪付APP支付"),

    /**
     * 云闪付扫码支付
     */
    PAY_UNIONPAY_QR_CODE("UNIONPAY_QR_CODE", "云闪付扫码支付"),

    /**
     * 云闪付H5支付
     */
    PAY_UNIONPAY_H5("UNIONPAY_H5", "云闪付H5支付");

    private final String code;
    private final String name;

    PayTradeType(String code, String name) {
        this.code = code;
        this.name = name;
    }


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

    @Override
    public Object getCode() {
        return code;
    }

    @Override
    public String getName() {
        return name;
    }
}

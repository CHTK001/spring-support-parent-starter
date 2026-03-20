package com.chua.payment.support.enums;

/**
 * 支付渠道类型
 */
public enum PaymentChannelType {

    WECHAT("WECHAT", "微信支付"),
    ALIPAY("ALIPAY", "支付宝"),
    COMPOSITE("COMPOSITE", "综合支付"),
    WALLET("WALLET", "钱包");

    private final String code;
    private final String description;

    PaymentChannelType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static String descriptionOf(String code) {
        if (code == null) {
            return "";
        }
        for (PaymentChannelType value : values()) {
            if (value.code.equalsIgnoreCase(code)) {
                return value.description;
            }
        }
        return "";
    }
}

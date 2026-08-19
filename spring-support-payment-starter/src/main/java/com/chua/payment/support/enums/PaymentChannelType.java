package com.chua.payment.support.enums;

/**
 * 支付渠道类型
 * @author CH
 */
public enum PaymentChannelType {

    WECHAT("WECHAT", "微信支付"),
    ALIPAY("ALIPAY", "支付宝"),
    EPAY("EPAY", "易支付"),
    COMPOSITE("COMPOSITE", "直营网关"),
    WALLET("WALLET", "钱包");

    /** 代码 */
    private final String code;
    /** 描述 */
    private final String description;

    PaymentChannelType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /** 获取Code */
    public String getCode() {
        return code;
    }

    /** 获取Description */
    public String getDescription() {
        return description;
    }

    /** DescriptionOf */
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


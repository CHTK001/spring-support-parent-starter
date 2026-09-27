package com.chua.payment.support.enums;

/**
 * 支付渠道类型
 *
 * @author CH
 * @since 4.0.0
 */
public enum PaymentChannelType {

    /**
     * WECHAT，微信支付渠道
     */
    WECHAT("WECHAT", "微信支付"),
    /**
     * ALIPAY，支付宝渠道
     */
    ALIPAY("ALIPAY", "支付宝"),
    /**
     * EPAY，易支付聚合渠道
     */
    EPAY("EPAY", "易支付"),
    /**
     * COMPOSITE，运营网关统一收银渠道
     */
    COMPOSITE("COMPOSITE", "运营网关"),
    /**
     * WALLET，钱包余额支付渠道
     */
    WALLET("WALLET", "钱包");

    /**
     * 代码
     */
    private final String code;
    /**
     * 描述
     */
    private final String description;

    /**
     * 支付渠道类型
     *
     * @param code 编码
     * @param description 描述
     */
    PaymentChannelType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 获取编码
     *
     * @return 获取编码的结果
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取描述
     *
     * @return 获取描述的结果
     */
    public String getDescription() {
        return description;
    }

    /**
     * 根据编码获取描述
     *
     * @param code 编码
     * @return 描述
     */
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

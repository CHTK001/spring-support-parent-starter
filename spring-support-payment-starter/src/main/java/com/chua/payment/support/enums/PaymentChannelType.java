package com.chua.payment.support.enums;

/**
* 支付渠道类型
* @author CH
* @since 4.0.0
 */
public enum PaymentChannelType {

    /**
    * WECHAT。
    * @param "WECHAT" "WECHAT"
    * @param "微信支付" "微信支付"
     */
    WECHAT("WECHAT", "微信支付"),
    /**
    * ALIPAY。
    * @param "ALIPAY" "ALIPAY"
    * @param "支付宝" "支付宝"
     */
    ALIPAY("ALIPAY", "支付宝"),
    /**
    * EPAY。
    * @param "EPAY" "EPAY"
    * @param "易支付" "易支付"
     */
    EPAY("EPAY", "易支付"),
    /**
    * COMPOSITE。
    * @param "COMPOSITE" "COMPOSITE"
    * @param "直营网关" "直营网关"
     */
    COMPOSITE("COMPOSITE", "直营网关"),
    /**
    * WALLET。
    * @param "WALLET" "WALLET"
    * @param "钱包" "钱包"
     */
    WALLET("WALLET", "钱包");

    /** 代码 */
    private final String code;
    /** 描述 */
    private final String description;

    /**
    * 薪酬通道类型。
    * @param code 编码
    * @param description description
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
    * 获取Description
    *
    * @return 获取description的结果
     */
    public String getDescription() {
        return description;
    }

    /**
    * description的
    *
    * @param code 编码
    * @return description的的结果
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


package com.chua.payment.support.enums;

/**
 * 商户状态
 * @author CH
 */
public enum MerchantStatus {

    PENDING(0, "待审核"),
    ACTIVE(1, "已激活"),
    DISABLED(2, "已停用"),
    CLOSED(3, "已注销");

    /** 代码 */
    private final int code;
    /** 描述 */
    private final String description;

    MerchantStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    /** 获取Code */
    public int getCode() {
        return code;
    }

    /** 获取Description */
    public String getDescription() {
        return description;
    }

    /** DescriptionOf */
    public static String descriptionOf(Integer code) {
        if (code == null) {
            return "";
        }
        for (MerchantStatus value : values()) {
            if (value.code == code) {
                return value.description;
            }
        }
        return "";
    }
}


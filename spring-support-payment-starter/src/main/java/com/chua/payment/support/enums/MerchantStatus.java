package com.chua.payment.support.enums;

/**
 * 商户状态
 */
public enum MerchantStatus {

    PENDING(0, "待审核"),
    ACTIVE(1, "已激活"),
    DISABLED(2, "已停用"),
    CLOSED(3, "已注销");

    private final int code;
    private final String description;

    MerchantStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

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

package com.chua.payment.support.enums;

/**
 * 支付方式状态
 */
public enum ChannelStatus {

    DISABLED(0, "已禁用"),
    ENABLED(1, "已启用");

    private final int code;
    private final String description;

    ChannelStatus(int code, String description) {
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
        for (ChannelStatus value : values()) {
            if (value.code == code) {
                return value.description;
            }
        }
        return "";
    }
}

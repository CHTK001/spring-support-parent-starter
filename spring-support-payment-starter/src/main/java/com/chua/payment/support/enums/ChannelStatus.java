package com.chua.payment.support.enums;

/**
 * 支付方式状态
 * @author CH
 */
public enum ChannelStatus {

    DISABLED(0, "已禁用"),
    ENABLED(1, "已启用");

    /** 代码 */
    private final int code;
    /** 描述 */
    private final String description;

    ChannelStatus(int code, String description) {
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
        for (ChannelStatus value : values()) {
            if (value.code == code) {
                return value.description;
            }
        }
        return "";
    }
}


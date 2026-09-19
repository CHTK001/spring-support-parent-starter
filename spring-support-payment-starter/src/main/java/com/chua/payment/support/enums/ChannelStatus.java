package com.chua.payment.support.enums;

/**
 * 支付方式状态
 * @author CH
 * @since 4.0.0
 */
public enum ChannelStatus {

    /**
     * 已禁用。
     * @param 0 0
     * @param "已禁用" "已禁用"
     */
    DISABLED(0, "已禁用"),
    /**
     * 已启用。
     * @param 1 1
     * @param "已启用" "已启用"
     */
    ENABLED(1, "已启用");

    /**
     * 代码
    */
    private final int code;
    /**
     * 描述
    */
    private final String description;

    /**
     * 通道状态。
     * @param code 编码
     * @param description description
     */
    ChannelStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 获取编码
     *
     * @return 获取编码的结果
     */
    public int getCode() {
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


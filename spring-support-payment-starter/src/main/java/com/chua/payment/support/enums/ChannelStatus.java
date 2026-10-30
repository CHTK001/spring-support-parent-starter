package com.chua.payment.support.enums;

/**
 * 支付渠道状态
 *
 * @author CH
 * @since 4.0.0
 */
public enum ChannelStatus {

    /**
     * 已禁用，渠道不可用于下单
     */
    DISABLED(0, "已禁用"),
    /**
     * 已启用，渠道可正常收款
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
     * 渠道状态
     * @param code 编码
     * @param description 描述
     * @return 返回值
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
    public Integer getCode() {
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

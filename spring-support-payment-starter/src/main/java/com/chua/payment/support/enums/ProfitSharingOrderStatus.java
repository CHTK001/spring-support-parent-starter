package com.chua.payment.support.enums;

/**
 * 分账订单状态
 *
 * @author CH
 * @since 4.0.0
 */
public enum ProfitSharingOrderStatus {

    /**
     * 待分账
     */
    PENDING("PENDING", "待分账"),
    /**
     * 分账中
     */
    PROCESSING("PROCESSING", "分账中"),
    /**
     * 分账成功
     */
    SUCCEEDED("SUCCEEDED", "分账成功"),
    /**
     * 分账失败
     */
    FAILED("FAILED", "分账失败"),
    /**
     * 已回退
     */
    RETURNED("RETURNED", "已回退");

    /**
     * 状态码
     */
    private final String code;
    /**
     * 状态描述
     */
    private final String description;

    /**
     * 分账订单状态
     *
     * @param code        状态码
     * @param description 状态描述
     */
    ProfitSharingOrderStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 获取状态码
     *
     * @return 状态码
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取状态描述
     *
     * @return 状态描述
     */
    public String getDescription() {
        return description;
    }
}

package com.chua.payment.support.enums;

/**
 * 分账回退状态
 *
 * @author CH
 * @since 4.0.0
 */
public enum ProfitSharingReturnStatus {

    /**
     * 处理中
     */
    PENDING("PENDING", "处理中"),
    /**
     * 回退成功
     */
    SUCCEEDED("SUCCEEDED", "回退成功"),
    /**
     * 回退失败
     */
    FAILED("FAILED", "回退失败");

    /**
     * 状态码
     */
    private final String code;
    /**
     * 状态描述
     */
    private final String description;

    /**
     * 分账回退状态
     *
     * @param code        状态码
     * @param description 状态描述
     */
    ProfitSharingReturnStatus(String code, String description) {
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

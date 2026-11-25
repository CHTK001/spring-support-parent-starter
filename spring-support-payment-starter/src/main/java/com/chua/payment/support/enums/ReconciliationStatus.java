package com.chua.payment.support.enums;

/**
 * 渠道对账单状态
 *
 * @author CH
 * @since 4.0.0
 */
public enum ReconciliationStatus {

    /**
     * 对账一致
     */
    MATCHED("MATCHED", "对账一致"),
    /**
     * 存在差异
     */
    DIFF("DIFF", "存在差异"),
    /**
     * 对账失败（账单下载或解析失败）
     */
    FAILED("FAILED", "对账失败");

    /**
     * 状态码
     */
    private final String code;
    /**
     * 状态描述
     */
    private final String description;

    /**
     * 渠道对账单状态
     *
     * @param code        状态码
     * @param description 状态描述
     */
    ReconciliationStatus(String code, String description) {
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

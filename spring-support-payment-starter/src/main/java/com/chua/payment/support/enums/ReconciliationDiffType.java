package com.chua.payment.support.enums;

/**
 * 渠道对账差异类型
 *
 * @author CH
 * @since 4.0.0
 */
public enum ReconciliationDiffType {

    /**
     * 渠道有账单、本地无流水
     */
    LOCAL_MISSING("LOCAL_MISSING", "渠道有本地无"),
    /**
     * 本地有流水、渠道无账单
     */
    CHANNEL_MISSING("CHANNEL_MISSING", "本地有渠道无"),
    /**
     * 双方均有记录但金额不一致
     */
    AMOUNT_MISMATCH("AMOUNT_MISMATCH", "金额不一致");

    /**
     * 类型码
     */
    private final String code;
    /**
     * 类型描述
     */
    private final String description;

    /**
     * 渠道对账差异类型
     *
     * @param code        类型码
     * @param description 类型描述
     */
    ReconciliationDiffType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 获取类型码
     *
     * @return 类型码
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取类型描述
     *
     * @return 类型描述
     */
    public String getDescription() {
        return description;
    }
}

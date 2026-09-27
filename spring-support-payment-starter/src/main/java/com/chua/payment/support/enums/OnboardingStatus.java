package com.chua.payment.support.enums;

/**
 * 开通状态
 *
 * @author CH
 * @since 4.0.0
 */
public enum OnboardingStatus {

    /**
     * 尚未开始，渠道开通流程未发起
     */
    NOT_STARTED("NOT_STARTED", "未开始"),
    /**
     * 开通进行中，资料已提交等待渠道审核
     */
    IN_PROGRESS("IN_PROGRESS", "进行中"),
    /**
     * 已开通，渠道可正式使用
     */
    COMPLETED("COMPLETED", "已开通");

    /**
     * 代码
     */
    private final String code;
    /**
     * 描述
     */
    private final String description;

    /**
     * 开通状态
     *
     * @param code 编码
     * @param description 描述
     */
    OnboardingStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 获取编码
     *
     * @return 获取编码的结果
     */
    public String getCode() {
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
    public static String descriptionOf(String code) {
        if (code == null) {
            return "";
        }
        for (OnboardingStatus value : values()) {
            if (value.code.equalsIgnoreCase(code)) {
                return value.description;
            }
        }
        return "";
    }
}

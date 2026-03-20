package com.chua.payment.support.enums;

/**
 * 开通状态
 */
public enum OnboardingStatus {

    NOT_STARTED("NOT_STARTED", "未开始"),
    IN_PROGRESS("IN_PROGRESS", "开通中"),
    COMPLETED("COMPLETED", "已开通");

    private final String code;
    private final String description;

    OnboardingStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

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

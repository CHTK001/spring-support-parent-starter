package com.chua.payment.support.enums;

/**
* 开通状态
* @author CH
* @since 4.0.0
 */
public enum OnboardingStatus {

    /**
    * not启动。
    * @param "NOT_STARTED" "NOT_启动"
    * @param "未开始" "未开始"
     */
    NOT_STARTED("NOT_STARTED", "未开始"),
    /**
    * 入进步。
    * @param "IN_PROGRESS" "入_进步"
    * @param "开通中" "开通中"
     */
    IN_PROGRESS("IN_PROGRESS", "开通中"),
    /**
    * 完成。
    * @param "COMPLETED" "COMPLETED"
    * @param "已开通" "已开通"
     */
    COMPLETED("COMPLETED", "已开通");

    /** 代码 */
    private final String code;
    /** 描述 */
    private final String description;

    /**
    * onboarding状态。
    * @param code 编码
    * @param description description
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


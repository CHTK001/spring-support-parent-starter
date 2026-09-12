package com.chua.payment.support.enums;

/**
* 商户状态
* @author CH
* @since 4.0.0
 */
public enum MerchantStatus {

    /**
    * PENDING。
    * @param 0 0
    * @param "待审核" "待审核"
     */
    PENDING(0, "待审核"),
    /**
    * 活跃。
    * @param 1 1
    * @param "已激活" "已激活"
     */
    ACTIVE(1, "已激活"),
    /**
    * 已禁用。
    * @param 2 2
    * @param "已停用" "已停用"
     */
    DISABLED(2, "已停用"),
    /**
    * 关闭。
    * @param 3 3
    * @param "已注销" "已注销"
     */
    CLOSED(3, "已注销");

    /** 代码 */
    private final int code;
    /** 描述 */
    private final String description;

    /**
    * merchant状态。
    * @param code 编码
    * @param description description
     */
    MerchantStatus(int code, String description) {
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
        for (MerchantStatus value : values()) {
            if (value.code == code) {
                return value.description;
            }
        }
        return "";
    }
}


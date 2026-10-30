package com.chua.payment.support.enums;

/**
 * 商户状态
 *
 * @author CH
 * @since 4.0.0
 */
public enum MerchantStatus {

    /**
     * 待审核，商户资料已提交但尚未通过审核
     */
    PENDING(0, "待审核"),
    /**
     * 已激活，商户可正常开展支付业务
     */
    ACTIVE(1, "已激活"),
    /**
     * 已停用，商户被暂停使用但资料保留
     */
    DISABLED(2, "已停用"),
    /**
     * 已关闭，商户终止合作不再使用
     */
    CLOSED(3, "已关闭");

    /**
     * 代码
     */
    private final int code;
    /**
     * 描述
     */
    private final String description;

    /**
     * 商户状态
     * @param code 编码
     * @param description 描述
     * @return 返回值
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
        for (MerchantStatus value : values()) {
            if (value.code == code) {
                return value.description;
            }
        }
        return "";
    }
}

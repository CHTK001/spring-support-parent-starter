package com.chua.payment.support.dto;

import lombok.Data;

/**
 * 支付全局配置 DTO
 * @author CH
 */
@Data
public class PaymentGlobalConfigDTO {

    /** PaymentnotifybaseURL */
    private String paymentNotifyBaseUrl;

    /** Payment返回值URL */
    private String paymentReturnUrl;

    /** Paymentcallback路径模板 */
    private String paymentCallbackPathTemplate;

    /** Paymentautorefresh秒 */
    private Integer paymentAutoRefreshSeconds;

    /** Coupondeductwithdrawable */
    private Boolean couponDeductWithdrawable;

    /** Remark */
    private String remark;
}


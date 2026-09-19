package com.chua.payment.support.dto;

import lombok.Data;

/**
 * 支付全局配置 DTO
 * @author CH
 * @since 4.0.0
 */
@Data
public class PaymentGlobalConfigDTO {

    /** paymentnotifybaseurl */
    private String paymentNotifyBaseUrl;

    /** Payment返回值URL */
    private String paymentReturnUrl;

    /** Paymentcallback路径模板 */
    private String paymentCallbackPathTemplate;

    /** Paymentautorefresh秒 */
    private Integer paymentAutoRefreshSeconds;

    /** Coupondeductwithdrawable */
    private Boolean couponDeductWithdrawable;

    /** 评论 */
    private String remark;
}


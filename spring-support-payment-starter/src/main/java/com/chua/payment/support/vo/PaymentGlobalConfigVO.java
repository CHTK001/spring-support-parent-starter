package com.chua.payment.support.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 支付全局配置 VO
 * @author CH
 */
@Data
public class PaymentGlobalConfigVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 配置密钥 */
    private String configKey;

    /** PaymentnotifybaseURL */
    private String paymentNotifyBaseUrl;

    /** Payment返回值URL */
    private String paymentReturnUrl;

    /** Paymentcallback路径模板 */
    private String paymentCallbackPathTemplate;

    /** Paymentautorefresh秒 */
    private Integer paymentAutoRefreshSeconds;

    /** Remark */
    private String remark;

    /** Payment示例PAYnotifyURL */
    private String paymentSamplePayNotifyUrl;

    /** Payment示例refundnotifyURL */
    private String paymentSampleRefundNotifyUrl;

    /** Coupondeductwithdrawable */
    private Boolean couponDeductWithdrawable;

    /** 更新时间AT */
    private LocalDateTime updatedAt;
}

package com.chua.payment.support.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 支付全局配置 VO
 * @author CH
 * @since 4.0.0
 */
@Data
public class PaymentGlobalConfigVO implements Serializable {
    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
     * 配置密钥
    */
    private String configKey;

    /**
     * paymentnotifybaseurl
    */
    private String paymentNotifyBaseUrl;

    /**
     * Payment返回值URL
    */
    private String paymentReturnUrl;

    /**
     * Paymentcallback路径模板
    */
    private String paymentCallbackPathTemplate;

    /**
     * Paymentautorefresh秒
    */
    private Integer paymentAutoRefreshSeconds;

    /**
     * 评论
    */
    private String remark;

    /**
     * 薪酬示例paynotifyurl
    */
    private String paymentSamplePayNotifyUrl;

    /**
     * 薪酬示例refundnotifyurl
    */
    private String paymentSampleRefundNotifyUrl;

    /**
     * Coupondeductwithdrawable
    */
    private Boolean couponDeductWithdrawable;

    /**
     * 更新时间AT
    */
    private LocalDateTime updatedAt;
}

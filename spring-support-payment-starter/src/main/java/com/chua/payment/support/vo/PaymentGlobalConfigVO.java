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
    private static final long serialVersionUID = 1L; // 序列化版本uid

    /**
     * 配置键
     */
    private String configKey;

    /**
     * 支付通知基础URL
     */
    private String paymentNotifyBaseUrl;

    /**
     * 支付返回值URL
     */
    private String paymentReturnUrl;

    /**
     * 支付回调路径模板
     */
    private String paymentCallbackPathTemplate;

    /**
     * 支付自动刷新秒数
     */
    private Integer paymentAutoRefreshSeconds;

    /**
     * 备注
     */
    private String remark;

    /**
     * 支付示例支付通知URL
     */
    private String paymentSamplePayNotifyUrl;

    /**
     * 支付示例退款通知URL
     */
    private String paymentSampleRefundNotifyUrl;

    /**
     * 优惠券抵扣是否可提现
     */
    private Boolean couponDeductWithdrawable;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}

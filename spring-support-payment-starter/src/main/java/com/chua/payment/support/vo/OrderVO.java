package com.chua.payment.support.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单视图对象
 *
 * @since 2026-03-18
 * @author CH
 */
@Data
public class OrderVO implements Serializable {
    /** ID */
    private Long id;

    /** 排序NO */
    private String orderNo;

    /** Business排序NO */
    private String businessOrderNo;

    /** MerchantID */
    private Long merchantId;

    /** Merchant名称 */
    private String merchantName;

    /** 通道ID */
    private Long channelId;

    /** 通道名称 */
    private String channelName;

    /** 用户ID */
    private Long userId;

    /** 通道类型 */
    private String channelType;

    /** 通道SUB类型 */
    private String channelSubType;

    /** 排序amount */
    private BigDecimal orderAmount;

    /** Paidamount */
    private BigDecimal paidAmount;

    /** Refundamount */
    private BigDecimal refundAmount;

    /** Discountamount */
    private BigDecimal discountAmount;

    /** Currency */
    private String currency;

    /** 状态 */
    private String status;

    /** 状态desc */
    private String statusDesc;

    /** Subject */
    private String subject;

    /** 请求体 */
    private String body;

    /** NotifyURL */
    private String notifyUrl;

    /** 返回值URL */
    private String returnUrl;

    /** Expire时间 */
    private LocalDateTime expireTime;

    /** PAY时间 */
    private LocalDateTime payTime;

    /** Complete时间 */
    private LocalDateTime completeTime;

    /** Refund时间 */
    private LocalDateTime refundTime;

    /** Thirdparty排序NO */
    private String thirdPartyOrderNo;

    /** Remark */
    private String remark;

    /** 删除标记 */
    private Integer deleted;

    /** 创建时间AT */
    private LocalDateTime createdAt;

    /** 更新时间AT */
    private LocalDateTime updatedAt;
}

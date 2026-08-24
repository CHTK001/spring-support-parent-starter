package com.chua.payment.support.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款单视图对象
 * @author CH
 */
@Data
public class RefundOrderVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** ID */
    private Long id;

    /** RefundNO */
    private String refundNo;

    /** 排序ID */
    private Long orderId;

    /** 排序NO */
    private String orderNo;

    /** MerchantID */
    private Long merchantId;

    /** Merchant名称 */
    private String merchantName;

    /** 通道ID */
    private Long channelId;

    /** 通道名称 */
    private String channelName;

    /** 来源排序状态 */
    private String sourceOrderStatus;

    /** 来源排序状态desc */
    private String sourceOrderStatusDesc;

    /** ThirdpartyrefundNO */
    private String thirdPartyRefundNo;

    /** Refundamount */
    private BigDecimal refundAmount;

    /** 状态 */
    private String status;

    /** 状态desc */
    private String statusDesc;

    /** Reason */
    private String reason;

    /** Notify状态 */
    private Integer notifyStatus;

    /** 请求payload */
    private String requestPayload;

    /** 响应payload */
    private String responsePayload;

    /** Operator */
    private String operator;

    /** Remark */
    private String remark;

    /** 创建时间AT */
    private LocalDateTime createdAt;

    /** 更新时间AT */
    private LocalDateTime updatedAt;
}

package com.chua.payment.support.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款单视图对象
 * @author CH
 * @since 4.0.0
 */
@Data
public class RefundOrderVO implements Serializable {
    private static final long serialVersionUID = 1L; // 串行版本uid

    /** 标识 */
    private Long id;

    /** 退税no */
    private String refundNo;

    /** 排序标识 */
    private Long orderId;

    /** 排序NO */
    private String orderNo;

    /** merchantid */
    private Long merchantId;

    /** Merchant名称 */
    private String merchantName;

    /** 通道标识 */
    private Long channelId;

    /** 通道名称 */
    private String channelName;

    /** 来源排序状态 */
    private String sourceOrderStatus;

    /** 来源排序状态desc */
    private String sourceOrderStatusDesc;

    /** thirdpartyrefundno */
    private String thirdPartyRefundNo;

    /** Refundamount */
    private BigDecimal refundAmount;

    /** 状态 */
    private String status;

    /** 状态desc */
    private String statusDesc;

    /** ReasonMLmlml */
    private String reason;

    /** 通知状态 */
    private Integer notifyStatus;

    /** 请求payload */
    private String requestPayload;

    /** 响应payload */
    private String responsePayload;

    /** 操作符 */
    private String operator;

    /** 评论 */
    private String remark;

    /** 创建时间AT */
    private LocalDateTime createdAt;

    /** 更新时间AT */
    private LocalDateTime updatedAt;
}

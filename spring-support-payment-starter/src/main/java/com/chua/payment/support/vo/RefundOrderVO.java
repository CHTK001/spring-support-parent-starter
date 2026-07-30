package com.chua.payment.support.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款单视图对象
 */
@Data
public class RefundOrderVO implements Serializable {

    private Long id;

    private String refundNo;

    private Long orderId;

    private String orderNo;

    private Long merchantId;

    private String merchantName;

    private Long channelId;

    private String channelName;

    private String sourceOrderStatus;

    private String sourceOrderStatusDesc;

    private String thirdPartyRefundNo;

    private BigDecimal refundAmount;

    private String status;

    private String statusDesc;

    private String reason;

    private Integer notifyStatus;

    private String requestPayload;

    private String responsePayload;

    private String operator;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

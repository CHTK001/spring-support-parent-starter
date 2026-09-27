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
    private static final long serialVersionUID = 1L; // 序列化版本uid

    /**
     * 标识
     */
    private Long id;

    /**
     * 退款单号
     */
    private String refundNo;

    /**
     * 订单标识
     */
    private Long orderId;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 商户ID
     */
    private Long merchantId;

    /**
     * 商户名称
     */
    private String merchantName;

    /**
     * 渠道标识
     */
    private Long channelId;

    /**
     * 渠道名称
     */
    private String channelName;

    /**
     * 来源订单状态
     */
    private String sourceOrderStatus;

    /**
     * 来源订单状态描述
     */
    private String sourceOrderStatusDesc;

    /**
     * 第三方退款单号
     */
    private String thirdPartyRefundNo;

    /**
     * 退款金额
     */
    private BigDecimal refundAmount;

    /**
     * 状态
     */
    private String status;

    /**
     * 状态描述
     */
    private String statusDesc;

    /**
     * 退款原因
     */
    private String reason;

    /**
     * 通知状态
     */
    private Integer notifyStatus;

    /**
     * 请求报文
     */
    private String requestPayload;

    /**
     * 响应报文
     */
    private String responsePayload;

    /**
     * 操作人
     */
    private String operator;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}

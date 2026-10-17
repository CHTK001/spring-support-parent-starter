package com.chua.payment.support.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款单视图对象
 *
 * @param id                    标识
 * @param refundNo              退款单号
 * @param orderId               订单标识
 * @param orderNo               订单号
 * @param merchantId            商户ID
 * @param merchantName          商户名称
 * @param channelId             渠道标识
 * @param channelName           渠道名称
 * @param sourceOrderStatus     来源订单状态
 * @param sourceOrderStatusDesc 来源订单状态描述
 * @param thirdPartyRefundNo    第三方退款单号
 * @param refundAmount          退款金额
 * @param status                状态
 * @param statusDesc            状态描述
 * @param reason                退款原因
 * @param notifyStatus          通知状态
 * @param requestPayload        请求报文
 * @param responsePayload       响应报文
 * @param operator              操作人
 * @param remark                备注
 * @param createdAt             创建时间
 * @param updatedAt             更新时间
 * @author CH
 * @since 4.0.0
 */
public record RefundOrderVO(
        Long id,
        String refundNo,
        Long orderId,
        String orderNo,
        Long merchantId,
        String merchantName,
        Long channelId,
        String channelName,
        String sourceOrderStatus,
        String sourceOrderStatusDesc,
        String thirdPartyRefundNo,
        BigDecimal refundAmount,
        String status,
        String statusDesc,
        String reason,
        Integer notifyStatus,
        String requestPayload,
        String responsePayload,
        String operator,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) implements Serializable {

    private static final long serialVersionUID = 1L; // 序列化版本uid
}

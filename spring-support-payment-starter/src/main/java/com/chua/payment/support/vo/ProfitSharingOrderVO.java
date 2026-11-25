package com.chua.payment.support.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 分账订单视图对象
 *
 * @param id                主键ID
 * @param sharingOrderNo    分账单号
 * @param merchantId        商户ID
 * @param channelId         支付渠道ID
 * @param channelType       渠道类型: WECHAT/ALIPAY
 * @param orderId           支付订单ID
 * @param orderNo           支付订单号
 * @param thirdPartyOrderNo 第三方交易号
 * @param totalAmount       订单实付金额
 * @param sharingAmount     分账总金额
 * @param receivers         分账接收方明细(JSON)
 * @param status            状态: PENDING/PROCESSING/SUCCEEDED/FAILED/RETURNED
 * @param statusDesc        状态描述
 * @param channelOrderNo    渠道分账单号
 * @param failReason        失败原因
 * @param finishAt          完成时间
 * @param remark            备注
 * @param createdAt         创建时间
 * @since 4.0.0
 * @author CH
 */
public record ProfitSharingOrderVO(
        Long id,
        String sharingOrderNo,
        Long merchantId,
        Long channelId,
        String channelType,
        Long orderId,
        String orderNo,
        String thirdPartyOrderNo,
        BigDecimal totalAmount,
        BigDecimal sharingAmount,
        String receivers,
        String status,
        String statusDesc,
        String channelOrderNo,
        String failReason,
        LocalDateTime finishAt,
        String remark,
        LocalDateTime createdAt
) implements Serializable {

    private static final long serialVersionUID = 1L;
}

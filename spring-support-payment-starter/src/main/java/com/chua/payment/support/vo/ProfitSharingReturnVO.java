package com.chua.payment.support.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 分账回退视图对象
 *
 * @param id               主键ID
 * @param returnNo         回退单号
 * @param merchantId       商户ID
 * @param sharingOrderId   分账单ID
 * @param sharingOrderNo   分账单号
 * @param orderNo          原支付订单号
 * @param amount           回退金额
 * @param receiverAccount  回退接收方账号
 * @param reason           回退原因
 * @param status           状态: PENDING/SUCCEEDED/FAILED
 * @param statusDesc       状态描述
 * @param channelReturnNo  渠道回退单号
 * @param failReason       失败原因
 * @param finishAt         完成时间
 * @param createdAt        创建时间
 * @since 4.0.0
 * @author CH
 */
public record ProfitSharingReturnVO(
        Long id,
        String returnNo,
        Long merchantId,
        Long sharingOrderId,
        String sharingOrderNo,
        String orderNo,
        BigDecimal amount,
        String receiverAccount,
        String reason,
        String status,
        String statusDesc,
        String channelReturnNo,
        String failReason,
        LocalDateTime finishAt,
        LocalDateTime createdAt
) implements Serializable {

    private static final long serialVersionUID = 1L;
}

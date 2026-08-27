package com.chua.payment.support.dto;

/**
 * 钱包异步回调通知参数对象。
 *
 * @param orderType         订单类型（RECHARGE/TRANSFER/WITHDRAW）
 * @param orderNo           订单号
 * @param thirdPartyOrderNo 第三方支付单号
 * @param status            状态（SUCCESS/PROCESSING/FAILED）
 * @param payload           通知报文
 * @param reason            失败原因
 * @author CH
 * @since 4.0.0.50
 */
public record WalletNotifyDTO(
        String orderType,
        String orderNo,
        String thirdPartyOrderNo,
        String status,
        String payload,
        String reason
) {
}

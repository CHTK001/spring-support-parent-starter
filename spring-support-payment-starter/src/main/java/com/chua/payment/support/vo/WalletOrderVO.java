package com.chua.payment.support.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 钱包订单视图
 *
 * @param id                 标识
 * @param orderNo            订单号
 * @param orderType          订单类型
 * @param merchantId         商户ID
 * @param userId             用户标识
 * @param relatedUserId      关联用户标识
 * @param amount             金额
 * @param status             状态
 * @param thirdPartyOrderNo  第三方订单号
 * @param bankAccount        银行账号
 * @param bankName           银行名称
 * @param accountName        账户名
 * @param requestPayload     请求报文
 * @param responsePayload    响应报文
 * @param operator           操作人
 * @param remark             备注
 * @param notifyUrl          通知URL
 * @param completedAt        完成时间
 * @param createTime         创建时间
 * @param updateTime         更新时间
 * @author CH
 * @since 4.0.0
 */
public record WalletOrderVO(
        Long id,
        String orderNo,
        String orderType,
        Long merchantId,
        Long userId,
        Long relatedUserId,
        BigDecimal amount,
        String status,
        String thirdPartyOrderNo,
        String bankAccount,
        String bankName,
        String accountName,
        String requestPayload,
        String responsePayload,
        String operator,
        String remark,
        String notifyUrl,
        LocalDateTime completedAt,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}

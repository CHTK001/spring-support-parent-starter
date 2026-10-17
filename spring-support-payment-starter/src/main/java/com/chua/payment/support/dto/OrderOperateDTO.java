package com.chua.payment.support.dto;

import java.math.BigDecimal;

/**
 * 订单操作通用DTO
 *
 * @param operator          操作人
 * @param remark            备注
 * @param paidAmount        已付金额
 * @param refundAmount      退款金额
 * @param thirdPartyOrderNo 第三方订单号
 * @author CH
 * @since 4.0.0
 */
public record OrderOperateDTO(
        String operator,
        String remark,
        BigDecimal paidAmount,
        BigDecimal refundAmount,
        String thirdPartyOrderNo) {
}

package com.chua.payment.support.dto;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 分账回退参数
 *
 * @param sharingOrderNo 分账单号
 * @param receiverAccount 回退接收方账号
 * @param amount          回退金额
 * @param reason          回退原因
 * @author CH
 * @since 4.0.0
 */
public record ProfitSharingReturnDTO(
        String sharingOrderNo,
        String receiverAccount,
        BigDecimal amount,
        String reason
) implements Serializable {

    private static final long serialVersionUID = 1L;
}

package com.chua.payment.support.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 渠道对账差异明细视图对象
 *
 * @param id            主键ID
 * @param statementId   对账单ID
 * @param statementNo   对账单号
 * @param merchantId    商户ID
 * @param channelType   渠道类型: WECHAT/ALIPAY
 * @param billDate      账单日期
 * @param diffType      差异类型: LOCAL_MISSING/CHANNEL_MISSING/AMOUNT_MISMATCH
 * @param diffTypeDesc  差异类型描述
 * @param orderNo       商户订单号
 * @param thirdPartyNo  第三方交易号
 * @param channelAmount 渠道账单金额
 * @param localAmount   本地流水金额
 * @param diffAmount    差额(渠道-本地)
 * @param remark        备注
 * @param createdAt     创建时间
 * @author CH
 * @since 4.0.0
 */
public record ReconciliationDiffVO(
        Long id,
        Long statementId,
        String statementNo,
        Long merchantId,
        String channelType,
        LocalDate billDate,
        String diffType,
        String diffTypeDesc,
        String orderNo,
        String thirdPartyNo,
        BigDecimal channelAmount,
        BigDecimal localAmount,
        BigDecimal diffAmount,
        String remark,
        LocalDateTime createdAt
) implements Serializable {

    private static final long serialVersionUID = 1L;
}

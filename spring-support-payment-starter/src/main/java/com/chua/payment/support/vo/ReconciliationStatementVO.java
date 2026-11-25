package com.chua.payment.support.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 渠道对账单视图对象
 *
 * @param id            主键ID
 * @param statementNo   对账单号
 * @param merchantId    商户ID
 * @param channelId     支付渠道ID
 * @param channelType   渠道类型: WECHAT/ALIPAY
 * @param billDate      账单日期
 * @param channelCount  渠道账单笔数
 * @param channelAmount 渠道账单总金额
 * @param localCount    本地流水笔数
 * @param localAmount   本地流水总金额
 * @param diffCount     差异数
 * @param status        状态: MATCHED/DIFF/FAILED
 * @param statusDesc    状态描述
 * @param failReason    失败原因
 * @param createdAt     创建时间
 * @param updatedAt     更新时间
 * @author CH
 * @since 4.0.0
 */
public record ReconciliationStatementVO(
        Long id,
        String statementNo,
        Long merchantId,
        Long channelId,
        String channelType,
        LocalDate billDate,
        Integer channelCount,
        BigDecimal channelAmount,
        Integer localCount,
        BigDecimal localAmount,
        Integer diffCount,
        String status,
        String statusDesc,
        String failReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) implements Serializable {

    private static final long serialVersionUID = 1L;
}

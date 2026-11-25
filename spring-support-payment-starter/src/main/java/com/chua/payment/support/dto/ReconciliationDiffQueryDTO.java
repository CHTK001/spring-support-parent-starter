package com.chua.payment.support.dto;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 渠道对账差异明细分页查询参数
 *
 * @param pageNum     页码
 * @param pageSize    每页条数
 * @param statementId 对账单ID
 * @param statementNo 对账单号
 * @param merchantId  商户ID
 * @param channelType 渠道类型: WECHAT/ALIPAY
 * @param billDate    账单日期
 * @param diffType    差异类型: LOCAL_MISSING/CHANNEL_MISSING/AMOUNT_MISMATCH
 * @author CH
 * @since 4.0.0
 */
public record ReconciliationDiffQueryDTO(
        Integer pageNum,
        Integer pageSize,
        Long statementId,
        String statementNo,
        Long merchantId,
        String channelType,
        LocalDate billDate,
        String diffType
) implements Serializable {

    private static final long serialVersionUID = 1L;
}

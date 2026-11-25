package com.chua.payment.support.dto;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 渠道对账单分页查询参数
 *
 * @param pageNum      页码
 * @param pageSize     每页条数
 * @param merchantId   商户ID
 * @param channelType  渠道类型: WECHAT/ALIPAY
 * @param billDate     账单日期
 * @param billDateFrom 账单日期起（含）
 * @param billDateTo   账单日期止（含）
 * @param status       状态: MATCHED/DIFF/FAILED
 * @author CH
 * @since 4.0.0
 */
public record ReconciliationStatementQueryDTO(
        Integer pageNum,
        Integer pageSize,
        Long merchantId,
        String channelType,
        LocalDate billDate,
        LocalDate billDateFrom,
        LocalDate billDateTo,
        String status
) implements Serializable {

    private static final long serialVersionUID = 1L;
}

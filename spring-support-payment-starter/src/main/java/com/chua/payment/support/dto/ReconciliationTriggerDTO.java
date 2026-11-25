package com.chua.payment.support.dto;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 渠道对账触发参数
 *
 * @param merchantId  商户ID，为空时对账全部商户
 * @param channelType 渠道类型: WECHAT/ALIPAY，为空时对账全部启用渠道
 * @param billDate    账单日期，为空时默认取昨日
 * @author CH
 * @since 4.0.0
 */
public record ReconciliationTriggerDTO(
        Long merchantId,
        String channelType,
        LocalDate billDate
) implements Serializable {

    private static final long serialVersionUID = 1L;
}

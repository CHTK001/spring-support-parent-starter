package com.chua.starter.pay.support.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author CH
 * @since 2025/10/15 10:49
 */
@Data
@Schema(description = "查询转账订单")
public class QueryTransferV2Request {

    /**
     * 转账单号
     */
    @Schema(description = "转账单号")
    private String transferBillNo;

    /**
     * 商户转账单号
     */
    @Schema(description = "商户ID")
    private Integer merchantId;
}

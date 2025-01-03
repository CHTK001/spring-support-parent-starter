package com.chua.starter.pay.support.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 订单
 * @author CH
 * @since 2024/12/30
 */
@Data
@Schema(description = "订单响应数据")
public class PayOrderResponse {

    /**
     * 预支付ID
     */
    @Schema(description = "预支付ID")
    private String prepayId;

    /**
     * H5支付地址
     */
    @Schema(description = "支付地址")
    private String url;

    /**
     * 订单编号
     */
    @Schema(description = "订单编号")
    private String payMerchantCode;
}

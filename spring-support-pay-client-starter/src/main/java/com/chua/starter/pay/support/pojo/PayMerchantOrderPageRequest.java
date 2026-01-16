package com.chua.starter.pay.support.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单分页查询条件
 * 作者: CH
 * 创建时间: 2025-10-15
 * 版本: 1.0.0
 */
@Data
@Schema(description = "订单分页查询条件")
public class PayMerchantOrderPageRequest {

    /** 订单状态 */
    @Schema(description = "订单状态")
    private String payMerchantOrderStatus;

    /** 商户ID */
    @Schema(description = "商户ID")
    private Integer payMerchantId;

    /** 支付时间-开始 */
    @Schema(description = "支付时间-开始")
    private LocalDateTime payMerchantOrderPayTimeStart;

    /** 支付时间-结束 */
    @Schema(description = "支付时间-结束")
    private LocalDateTime payMerchantOrderPayTimeEnd;

    /** 完成时间-开始 */
    @Schema(description = "完成时间-开始")
    private LocalDateTime payMerchantOrderFinishedTimeStart;

    /** 完成时间-结束 */
    @Schema(description = "完成时间-结束")
    private LocalDateTime payMerchantOrderFinishedTimeEnd;
}

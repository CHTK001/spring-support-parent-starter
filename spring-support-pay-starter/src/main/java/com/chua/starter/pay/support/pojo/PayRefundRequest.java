package com.chua.starter.pay.support.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 请求
 * @author CH
 * @since 2024/12/30
 */
@Data
@Schema(description = "退款请求")
public class PayRefundRequest {

    /**
     * 订单号
     */
    @Schema(description = "订单号")
    private String payMerchantOrderCode;

    /**
     * 退款原因
     */
    @Schema(description = "退款原因")
    private String refundReason;
    /**
     * 金额
     */
    @Schema(description = "金额")
    private BigDecimal money;
    /**
     * 用户id
     */
    @Schema(description = "用户id")
    @NotBlank(message = "用户id不能为空")
    private String userId;

    /**
     * 对关闭服务的商户强制启用
     */
    @Schema(description = "对关闭服务的商户强制启用")
    private boolean force;
}

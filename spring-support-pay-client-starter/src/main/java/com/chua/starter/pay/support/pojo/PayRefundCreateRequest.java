package com.chua.starter.pay.support.pojo;

import com.chua.common.support.validator.group.UpdateGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 退款
 * @author CH
 * @since 2024/12/30
 */
@Data
@Schema(description = "退款响应请求")
public class PayRefundCreateRequest implements Serializable {

    /**
     * 退款原因
     */
    @Schema(description = "退款原因")
    private String refundReason;
    /**
     * 金额
     */
    @Schema(description = "金额(不填默认全额)")
    private BigDecimal money;

    /**
     * 订单号
     */
    @Schema(description = "订单号")
    @NotBlank(message = "订单号不能为空", groups = {UpdateGroup.class})
    private String payMerchantOrderCode;
}

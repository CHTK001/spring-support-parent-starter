package com.chua.starter.pay.support.pojo;

import com.chua.common.support.validator.group.AddGroup;
import com.chua.common.support.validator.group.UpdateGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单退款
 * @author CH
 * @since 2025/10/14 16:31
 */
@Data
@Schema(title = "订单退款")
public class RefundOrderV2Request {

    /**
     * 退款金额
     */
    @Schema(title = "退款金额")
    @NotNull(message = "退款金额不能为空", groups = UpdateGroup.class)
    private BigDecimal refundAmount;

    /**
     * 退款原因
     */
    @Schema(title = "退款原因")
    @NotNull(message = "退款原因不能为空", groups = UpdateGroup.class)
    private String refundReason;

}

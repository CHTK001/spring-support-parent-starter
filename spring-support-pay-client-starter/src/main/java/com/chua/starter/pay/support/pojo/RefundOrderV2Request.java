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
@Schema(description = "订单退款")
public class RefundOrderV2Request {

    /**
     * 退款金额
     */
    @Schema(description = "退款金额")
    @NotNull(message = "退款金额不能为空", groups = UpdateGroup.class)
    private BigDecimal refundAmount;

    /**
     * 退款原因
     */
    @Schema(description = "退款原因")
    @NotNull(message = "退款原因不能为空", groups = UpdateGroup.class)
    private String refundReason;

    /**
     * 克隆
     * @return 退款
     */
    public RefundOrderV2Request cloneAndGet() {
        RefundOrderV2Request refundOrderV2Request = new RefundOrderV2Request();
        refundOrderV2Request.setRefundAmount(this.getRefundAmount());
        refundOrderV2Request.setRefundReason(this.getRefundReason());
        return refundOrderV2Request;
    }

}

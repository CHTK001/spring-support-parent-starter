package com.chua.starter.pay.support.pojo;

import com.chua.common.support.validator.group.AddGroup;
import com.chua.common.support.validator.group.UpdateGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 订单状态
 * @author CH
 * @since 2025/10/15 14:12
 */
@Data
@Schema(name = "订单状态")
public class PayOrderStatusRequest {


    /**
     * 订单编号
     */
    @Schema(name = "订单编号")
    @NotBlank(message = "订单编号不能为空", groups = {UpdateGroup.class, AddGroup.class})
    private String payMerchantOrderCode;
}

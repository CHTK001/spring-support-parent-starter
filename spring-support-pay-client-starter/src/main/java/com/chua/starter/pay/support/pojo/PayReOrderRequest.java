package com.chua.starter.pay.support.pojo;

import com.chua.common.support.validator.group.AddGroup;
import com.chua.starter.pay.support.emuns.TradeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 请求
 * @author CH
 * @since 2024/12/30
 */
@Data
@Schema(description = "重新支付订单请求")
public class PayReOrderRequest implements Serializable {


    /**
     * 订单id
     */
    @Schema(description = "订单code")
    @NotBlank(message = "订单code不能为空", groups = {AddGroup.class})
    private String payMerchantOrderCode;
}

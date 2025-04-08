package com.chua.starter.pay.support.pojo;

import com.chua.starter.pay.support.emuns.TradeType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 请求
 *
 * @author CH
 * @since 2024/12/30
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "重新支付订单请求")
public class PayUpdateOrderRequest extends PayReOrderRequest {

    /**
     * 交易类型
     */
    @Schema(description = "交易类型")
    private TradeType tradeType;
}

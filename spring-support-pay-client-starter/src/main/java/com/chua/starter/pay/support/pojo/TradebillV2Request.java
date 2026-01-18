package com.chua.starter.pay.support.pojo;

import com.chua.common.support.base.validator.group.AddGroup;
import com.chua.common.support.base.validator.group.SelectGroup;
import com.chua.starter.pay.support.enums.PayTradeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 交易账单请求参数
 * @author CH
 * @since 2025-10-15
 */
@Data
@Schema(description = "交易账单请求参数")
public class TradebillV2Request {

    /**
     * 商家ID
     */
    @Schema(description = "商家ID")
    @NotNull(message = "商家ID不能为空", groups = SelectGroup.class)
    private Integer merchantId;

    /**
     * 支付方式
     */
    @Schema(description = "支付方式")
    @NotBlank(message = "支付方式不能为空", groups = SelectGroup.class)
    private PayTradeType payTradeType;
}

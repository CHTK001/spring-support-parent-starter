package com.chua.starter.pay.support.pojo;

import com.chua.common.support.validator.group.AddGroup;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.io.Serializable;

/**
 * 创建订单响应
 * @author CH
 * @since 2025/10/14 13:08
 */
@Data
@RequiredArgsConstructor
public class CreateOrderV2Response implements Serializable {

    /**
     * 订单编号
     */
    @ApiModelProperty("订单编号")
    @NotEmpty(message = "订单编号不能为空", groups = {AddGroup.class})
    private final String payMerchantOrderCode;

    /**
     * 预支付ID
     */
    @ApiModelProperty("预支付ID")
    private String prepayId;

    /**
     * H5支付地址
     */
    @Schema(description = "支付地址")
    private String url;
}

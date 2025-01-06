package com.chua.starter.pay.support.pojo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.chua.common.support.validator.group.SelectGroup;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单查询
 * @author CH
 */
@Data
@Schema(description = "订单查询")
public class PayOrderV1Request implements Serializable {


    /**
     * 商户号
     */
    @Schema(description = "商户号")
    @NotNull(message = "商户号不能为空", groups = {SelectGroup.class})
    private String payMerchantCode;

    /**
     * 订单号
     */
    @Schema(description = "订单号(支持模糊查询)")
    private String payMerchantOrderCode;
    /**
     * 用户名
     */
    @Schema(description = "用户名")
    private String username;

    /**
     * 交易方式；
     */
    @TableField(value = "pay_merchant_order_trade_type")
    @ApiModelProperty(value = "交易方式；")
    @Schema(description = "交易方式；")
    @Size(max = 255, message = "交易方式；最大长度要小于 255")
    private String payMerchantOrderTradeType;

    /**
     * 订单状态
     */
    @Schema(description = "订单状态")
    private String payMerchantOrderStatus;


    /**
     * 订单来源
     */
    @Schema(description = "订单来源")
    private String payMerchantOrderOrigin;
    /**
     * 附加数据
     */
    @Schema(description = "附加数据")
    private String payMerchantOrderAttach;

    /**
     * 开始时间
     */
    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    @Schema(description = "结束时间")
    private LocalDateTime endTime;
}

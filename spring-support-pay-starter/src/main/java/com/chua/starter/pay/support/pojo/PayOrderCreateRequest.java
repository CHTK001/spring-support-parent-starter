package com.chua.starter.pay.support.pojo;

import com.chua.common.support.validator.group.AddGroup;
import com.chua.starter.pay.support.emuns.TradeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建订单请求
 * @author CH
 * @since 2024/12/30
 */
@Data
@Schema(description = "创建订单请求")
public class PayOrderCreateRequest {


    /**
     * 订单id
     */
    @Schema(description = "订单id")
    @NotBlank(message = "订单id不能为空", groups = {AddGroup.class})
    private String orderId;

    /**
     * 来源
     */
    @Schema(description = "来源")
    @NotBlank(message = "来源不能为空", groups = {AddGroup.class})
    private String origin;

    /**
     * 商品名称
     */
    @Schema(description = "商品名称")
    @NotBlank(message = "商品名称不能为空", groups = {AddGroup.class})
    private String productName;
    /**
     * 商户编码
     */
    @Schema(description = "商户编码")
    @NotBlank(message = "商户编码不能为空", groups = {AddGroup.class})
    private String merchantCode;


    /**
     * 交易类型
     */
    @Schema(description = "交易类型")
    @NotNull(message = "交易类型不能为空", groups = {AddGroup.class})
    private TradeType tradeType;

    /**
     * 金额
     */
    @Schema(description = "金额")
    @NotNull(message = "金额不能为空", groups = {AddGroup.class})
    private BigDecimal price;

    /**
     * 总金额
     */
    @Schema(description = "总金额")
    @NotNull(message = "总金额不能为空", groups = {AddGroup.class})
    private BigDecimal totalPrice;


    /**
     * 备注
     */
    @Schema(description = "备注")
    private String remark;
}

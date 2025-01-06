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
@Schema(description = "订单请求")
public class PayOrderRequest implements Serializable {


    /**
     * 订单id
     */
    @Schema(description = "订单id(用于前端区分是否同一次交易)")
    @NotBlank(message = "订单id不能为空", groups = {AddGroup.class})
    private String orderId;
    /**
     * 用户id
     */
    @Schema(description = "用户id")
    @NotBlank(message = "用户id不能为空", groups = {AddGroup.class})
    private String userId;

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
     * 商品ID
     */
    @Schema(description = "商品编码(用于业务上的商品标识)")
    @NotBlank(message = "商品编码不能为空", groups = {AddGroup.class})
    private String productCode;
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
    @NotBlank(message = "交易类型不能为空", groups = {AddGroup.class})
    private TradeType tradeType;

    /**
     * 金额
     */
    @Schema(description = "金额")
    @NotBlank(message = "金额不能为空", groups = {AddGroup.class})
    private BigDecimal price;

    /**
     * 总金额
     */
    @Schema(description = "总金额")
    @NotBlank(message = "总金额不能为空", groups = {AddGroup.class})
    private BigDecimal totalPrice;


    /**
     * 备注
     */
    @Schema(description = "备注")
    private String remark;

    /**
     * 附加信息
     */
    @Schema(description = "附加信息(业务数据)")
    private String attach;
}

package com.chua.starter.pay.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 *
 * @since 2024/12/30
 * @author CH    
 */

/**
 * 支付订单
 */
@ApiModel(description = "商品")
@Schema(description = "支付订单")
@Data
@TableName(value = "pay_merchant_goods")
public class PayMerchantGoods implements Serializable {
    @TableId(value = "pay_merchant_goods_id", type = IdType.AUTO)
    @ApiModelProperty(value = "")
    @Schema(description = "")
    @NotNull(message = "不能为null")
    private Integer payMerchantGoodsId;

    /**
     * 商品名称
     */
    @TableField(value = "pay_merchant_goods_name")
    @ApiModelProperty(value = "商品名称")
    @Schema(description = "商品名称")
    @Size(max = 255, message = "商品名称最大长度要小于 255")
    private String payMerchantGoodsName;
    /**
     * 商品代码
     */
    @TableField(value = "pay_merchant_goods_code")
    @ApiModelProperty(value = "商品代码")
    @Schema(description = "商品代码")
    @Size(max = 255, message = "商品代码最大长度要小于 255")
    private String payMerchantGoodsCode;
    /**
     * 商品价格
     */
    @TableField(value = "pay_merchant_goods_price")
    @ApiModelProperty(value = "商品价格")
    @Schema(description = "商品价格")
    private BigDecimal payMerchantGoodsPrice;


    /**
     * 商品状态
     */
    @TableField(value = "pay_merchant_goods_status")
    @ApiModelProperty(value = "商品是否禁用;0:启用")
    private Integer payMerchantGoodsStatus;


}
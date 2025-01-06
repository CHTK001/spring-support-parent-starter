package com.chua.starter.pay.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.common.support.validator.group.UpdateGroup;
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
    * 商品代码表
    */
@ApiModel(description="商品代码表")
@Schema(description="商品代码表")
@Data
@EqualsAndHashCode(callSuper=true)
@TableName(value = "pay_merchant_goods")
public class PayMerchantGoods extends SysBase implements Serializable {
    @TableId(value = "pay_merchant_goods_id", type = IdType.AUTO)
    @ApiModelProperty(value="")
    @Schema(description="")
    @NotNull(message = "商品ID不能为空", groups = {UpdateGroup.class})
    private Integer payMerchantGoodsId;

    /**
     * 商品名称
     */
    @TableField(value = "pay_merchant_goods_name")
    @ApiModelProperty(value="商品名称")
    @Schema(description="商品名称")
    @Size(max = 255,message = "商品名称最大长度要小于 255", groups = {AddGroup.class})
    private String payMerchantGoodsName;
    /**
     * 商品名称拼音
     */
    @TableField(value = "pay_merchant_goods_pinyin")
    @ApiModelProperty(value="商品名称拼音")
    @Schema(description="商品名称拼音")
    private String payMerchantGoodsPinyin;

    /**
     * 商品编码
     */
    @TableField(value = "pay_merchant_goods_code")
    @ApiModelProperty(value="商品编码")
    @Schema(description="商品编码")
    @Size(max = 255,message = "商品编码最大长度要小于 255", groups = {AddGroup.class})
    private String payMerchantGoodsCode;

    /**
     * 商品价格
     */
    @TableField(value = "pay_merchant_goods_price")
    @ApiModelProperty(value="商品价格")
    @Schema(description="商品价格")
    private BigDecimal payMerchantGoodsPrice;

    /**
     * 商品是否禁用;0:启用
     */
    @TableField(value = "pay_merchant_goods_status")
    @ApiModelProperty(value="商品是否禁用;0:启用")
    @Schema(description="商品是否禁用;0:启用")
    private Integer payMerchantGoodsStatus;

    /**
     * 商户编码
     */
    @TableField(value = "pay_merchant_code")
    @ApiModelProperty(value="商户编码")
    @Schema(description="商户编码")
    @Size(max = 255,message = "商户编码最大长度要小于 255", groups = {AddGroup.class})
    private String payMerchantCode;

    /**
     * 优惠
     */
    @TableField(value = "pay_merchant_goods_discounts")
    @ApiModelProperty(value="优惠")
    @Schema(description="优惠")
    private BigDecimal payMerchantGoodsDiscounts;
}
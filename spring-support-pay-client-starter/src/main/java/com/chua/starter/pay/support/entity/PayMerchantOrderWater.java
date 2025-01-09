package com.chua.starter.pay.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.math.BigDecimal;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
    * 订单流水
    */
@ApiModel(description="订单流水")
@Schema(description="订单流水")
@Data
@EqualsAndHashCode(callSuper=true)
@TableName(value = "pay_merchant_order_water")
public class PayMerchantOrderWater extends SysBase implements Serializable {
    @TableId(value = "pay_merchant_order_water_id", type = IdType.AUTO)
    @ApiModelProperty(value="")
    @Schema(description="")
    @NotNull(message = "不能为null")
    private Integer payMerchantOrderWaterId;

    /**
     * 流水编号
     */
    @TableField(value = "pay_merchant_order_water_code")
    @ApiModelProperty(value="流水编号")
    @Schema(description="流水编号")
    @Size(max = 255,message = "流水编号最大长度要小于 255")
    private String payMerchantOrderWaterCode;

    /**
     * 订单编号
     */
    @TableField(value = "pay_merchant_order_code")
    @ApiModelProperty(value="订单编号")
    @Schema(description="订单编号")
    @Size(max = 255,message = "订单编号最大长度要小于 255")
    private String payMerchantOrderCode;

    /**
     * 订单状态;参见订单表
     */
    @TableField(value = "pay_merchant_order_status")
    @ApiModelProperty(value="订单状态;参见订单表")
    @Schema(description="订单状态;参见订单表")
    @Size(max = 255,message = "订单状态;参见订单表最大长度要小于 255")
    private String payMerchantOrderStatus;


    /**
     * 当时余额
     */
    @TableField(value = "pay_merchant_order_wallet")
    @ApiModelProperty(value = "当时余额")
    private BigDecimal payMerchantOrderWallet;
    /**
     * 失败原因
     */
    @TableField(value = "pay_merchant_order_fail_message")
    @ApiModelProperty(value="失败原因")
    @Schema(description="失败原因")
    @Size(max = 255,message = "失败原因最大长度要小于 255")
    private String payMerchantOrderFailMessage;

    /**
     * 支付服务提供商订单号
     */
    @TableField(value = "pay_merchant_order_transaction_id")
    @ApiModelProperty(value="支付服务提供商订单号")
    @Schema(description="支付服务提供商订单号")
    @Size(max = 255,message = "支付服务提供商订单号最大长度要小于 255")
    private String payMerchantOrderTransactionId;

    /**
     * 退款理由
     */
    @TableField(value = "pay_merchant_order_refund_reason")
    @ApiModelProperty(value="退款理由")
    @Schema(description="退款理由")
    @Size(max = 255,message = "退款理由最大长度要小于 255")
    private String payMerchantOrderRefundReason;

    /**
     * 支付服务提供商退款订单号
     */
    @TableField(value = "pay_merchant_order_refund_transaction_id")
    @ApiModelProperty(value="支付服务提供商退款订单号")
    @Schema(description="支付服务提供商退款订单号")
    @Size(max = 255,message = "支付服务提供商退款订单号最大长度要小于 255")
    private String payMerchantOrderRefundTransactionId;

    /**
     * 退款订单号
     */
    @TableField(value = "pay_merchant_order_refund_code")
    @ApiModelProperty(value="退款订单号")
    @Schema(description="退款订单号")
    @Size(max = 255,message = "退款订单号最大长度要小于 255")
    private String payMerchantOrderRefundCode;

    /**
     * 退款时间
     */
    @TableField(value = "pay_merchant_order_refund_create_time")
    @ApiModelProperty(value="退款时间")
    @Schema(description="退款时间")
    @Size(max = 255,message = "退款时间最大长度要小于 255")
    private String payMerchantOrderRefundCreateTime;

    /**
     * 退款成功时间
     */
    @TableField(value = "pay_merchant_order_refund_success_time")
    @ApiModelProperty(value="退款成功时间")
    @Schema(description="退款成功时间")
    @Size(max = 255,message = "退款成功时间最大长度要小于 255")
    private String payMerchantOrderRefundSuccessTime;

    /**
     * 退款入账账户 说明：取当前退款单的退款入账方，有以下几种情况： 1）退回银行卡：{银行名称}{卡类型}{卡尾号} 2）退回支付用户零钱:支付用户零钱* 3）退还商户:商户基本账户商户结算银行账户 4）退回支付用户零钱通:支付用户零钱通
     */
    @TableField(value = "pay_merchant_order_refund_user_received_account")
    @ApiModelProperty(value="退款入账账户 说明：取当前退款单的退款入账方，有以下几种情况： 1）退回银行卡：{银行名称}{卡类型}{卡尾号} 2）退回支付用户零钱:支付用户零钱* 3）退还商户:商户基本账户商户结算银行账户 4）退回支付用户零钱通:支付用户零钱通")
    @Schema(description="退款入账账户 说明：取当前退款单的退款入账方，有以下几种情况： 1）退回银行卡：{银行名称}{卡类型}{卡尾号} 2）退回支付用户零钱:支付用户零钱* 3）退还商户:商户基本账户商户结算银行账户 4）退回支付用户零钱通:支付用户零钱通")
    @Size(max = 255,message = "退款入账账户 说明：取当前退款单的退款入账方，有以下几种情况： 1）退回银行卡：{银行名称}{卡类型}{卡尾号} 2）退回支付用户零钱:支付用户零钱* 3）退还商户:商户基本账户商户结算银行账户 4）退回支付用户零钱通:支付用户零钱通最大长度要小于 255")
    private String payMerchantOrderRefundUserReceivedAccount;


    /**
     * 商户名称
     */
    @TableField(exist = false)
    @ApiModelProperty(value="商户名称")
    private String payMerchantName;

    /**
     * 商品名称
     */
    @TableField(exist = false)
    @ApiModelProperty(value="商品名称")
    private String payMerchantOrderProductName;
    /**
     * 商品code
     */
    @TableField(exist = false)
    @ApiModelProperty(value="商品code")
    private String payMerchantOrderProductCode;

    /**
     * 商品原始价格
     */
    @TableField(exist = false)
    @ApiModelProperty(value="商品原始价格")
    private String payMerchantOrderPrice;
    /**
     * 商品总价格
     */
    @TableField(exist = false)
    @ApiModelProperty(value="商品总价格")
    private String payMerchantOrderTotalPrice;

    /**
     * 支付方式
     */
    @TableField(exist = false)
    @ApiModelProperty(value="支付方式")
    private String payMerchantOrderTradeType;
    /**
     * 订单来源
     */
    @TableField(exist = false)
    @ApiModelProperty(value = "订单来源")
    @Schema(description = "订单来源")
    @Size(max = 255, message = "订单来源最大长度要小于 255")
    private String payMerchantOrderOrigin;

    /**
     * 附加参数
     */
    @TableField(exist = false)
    @ApiModelProperty(value = "附加参数")
    @Schema(description = "附加参数")
    @Size(max = 255, message = "附加参数最大长度要小于 255")
    private String payMerchantOrderAttach;
}
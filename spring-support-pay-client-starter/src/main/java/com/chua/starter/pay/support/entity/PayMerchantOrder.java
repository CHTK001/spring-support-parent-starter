package com.chua.starter.pay.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import com.chua.starter.pay.support.enums.PayOrderStatus;
import com.chua.starter.pay.support.enums.PayTradeType;
import com.chua.starter.pay.support.handler.PayOrderStatusTypeHandler;
import com.chua.starter.pay.support.handler.PayTradeTypeTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 *
 * @author CH
 * @since 2025/10/14 11:28
 */
/**
 * 订单表
 */
@EqualsAndHashCode(callSuper = true)
@Schema(description="订单表")
@Data
@TableName(value = "pay_merchant_order")
public class PayMerchantOrder extends SysBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "pay_merchant_order_id", type = IdType.AUTO)
    @Schema(description="")
    private Integer payMerchantOrderId;

    /**
     * 订单编号
     */
    @TableField(value = "pay_merchant_order_code")
    @Schema(description="订单编号")
    private String payMerchantOrderCode;

    /**
     * 商户ID
     */
    @TableField(value = "pay_merchant_id")
    @Schema(description="商户ID")
    private Integer payMerchantId;

    /**
     * 当时钱包余额
     */
    @TableField(value = "pay_merchant_current_wallet_amount")
    @Schema(description="当时钱包余额")
    private BigDecimal payMerchantCurrentWalletAmount;
    /**
     * 微信openID
     */
    @TableField(value = "pay_merchant_order_openid")
    @Schema(description="微信openID")
    private String payMerchantOrderOpenid;

    /**
     * 账号ID
     */
    @TableField(value = "user_id")
    @Schema(description="账号ID")
    private String userId;

    /**
     * 账号类型
     */
    @TableField(value = "user_type")
    @Schema(description="账号类型")
    private String userType;

    /**
     * 订单类型
     */
    @TableField(value = "pay_merchant_order_type")
    @Schema(description="订单类型")
    private Integer payMerchantOrderType;

    /**
     * 订单项目
     */
    @TableField(value = "pay_merchant_order_project")
    @Schema(description="订单项目")
    private Integer payMerchantOrderProject;

    /**
     * 订单实际支付金额(保留4位，实际支付四舍)
     */
    @TableField(value = "pay_merchant_order_amount")
    @Schema(description="订单实际支付金额(保留4位，实际支付四舍)")
    private BigDecimal payMerchantOrderAmount;


    /**
     * 订单原始金额(保留4位，实际支付四舍)
     */
    @TableField(value = "pay_merchant_order_origin_amount")
    @Schema(description="订单原始金额(保留4位，实际支付四舍)")
    private BigDecimal payMerchantOrderOriginAmount;

    /**
     * 支付时间
     */
    @TableField(value = "pay_merchant_order_pay_time")
    @Schema(description="支付时间")
    private LocalDateTime payMerchantOrderPayTime;

    /**
     * 创建时间
     */
    @TableField(value = "pay_merchant_order_create_time")
    @Schema(description="创建时间")
    private LocalDateTime payMerchantOrderCreateTime;
    /**
     * 完成时间
     */
    @TableField(value = "pay_merchant_order_finished_time")
    @Schema(description="完成时间")
    private LocalDateTime payMerchantOrderFinishedTime;

    /**
     * 订单状态
     */
    @TableField(value = "pay_merchant_order_status", typeHandler = PayOrderStatusTypeHandler.class)
    @Schema(description="订单状态")
    private PayOrderStatus payMerchantOrderStatus;

    /**
     * 订单失败原因
     */
    @TableField(value = "pay_merchant_order_failure_reason")
    @Schema(description="订单失败原因")
    private String payMerchantOrderFailureReason;

    /**
     * 交易类型
     */
    @TableField(value = "pay_merchant_trade_type", typeHandler = PayTradeTypeTypeHandler.class)
    @Schema(description="交易类型")
    private PayTradeType payMerchantTradeType;
    /**
     * 是否开票; 0:未开票
     */
    @TableField(value = "pay_merchant_order_invoiced")
    @Schema(description="是否开票; 0:未开票")
    private Integer payMerchantOrderInvoiced;



    /**
     * 开票ID
     */
    @TableField(value = "pay_merchant_order_invoiced_id")
    @Schema(description="开票ID")
    private String payMerchantOrderInvoicedId;

    /**
     * 产生订单的原始数据ID
     */
    @TableField(value = "pay_merchant_order_origin_id")
    @Schema(description="产生订单的原始数据ID")
    private String payMerchantOrderOriginId;

    /**
     * 支付服务提供商订单号
     */
    @TableField(value = "pay_merchant_order_transaction_id")
    @Schema(description="支付服务提供商订单号")
    private String payMerchantOrderTransactionId;

    /**
     * 请求系统
     */
    @TableField(value = "pay_merchant_order_browser_system")
    @Schema(description="请求系统")
    private String payMerchantOrderBrowserSystem;

    /**
     * 请求消息头
     */
    @TableField(value = "pay_merchant_order_browser")
    @Schema(description="请求消息头")
    private String payMerchantOrderBrowser;

    /**
     * 请求客户端IP
     */
    @TableField(value = "pay_merchant_order_address")
    @Schema(description="请求客户端IP")
    private String payMerchantOrderAddress;

    /**
     * 备注
     */
    @TableField(value = "pay_merchant_order_remark")
    @Schema(description="备注")
    private String payMerchantOrderRemark;

    /**
     * 附加参数
     */
    @TableField(value = "pay_merchant_order_attach")
    @Schema(description="附加参数")
    private String payMerchantOrderAttach;

    /**
     * 退款入账账户 说明：取当前退款单的退款入账方，有以下几种情况： 1）退回银行卡：{银行名称}{卡类型}{卡尾号} 2）退回支付用户零钱:支付用户零钱* 3）退还商户:商户基本账户商户结算银行账户 4）退回支付用户零钱通:支付用户零钱通
     */
    @TableField(value = "pay_merchant_order_refund_user_received_account")
    @Schema(description="退款入账账户 说明：取当前退款单的退款入账方，有以下几种情况： 1）退回银行卡：{银行名称}{卡类型}{卡尾号} 2）退回支付用户零钱:支付用户零钱* 3）退还商户:商户基本账户商户结算银行账户 4）退回支付用户零钱通:支付用户零钱通")
    private String payMerchantOrderRefundUserReceivedAccount;

    /**
     * 退款成功时间
     */
    @TableField(value = "pay_merchant_order_refund_success_time")
    @Schema(description="退款成功时间")
    private String payMerchantOrderRefundSuccessTime;

    /**
     * 退款创建时间
     */
    @TableField(value = "pay_merchant_order_refund_create_time")
    @Schema(description="退款创建时间")
    private LocalDateTime payMerchantOrderRefundCreateTime;

    /**
     * 退款订单号
     */
    @TableField(value = "pay_merchant_order_refund_code")
    @Schema(description="退款订单号")
    private String payMerchantOrderRefundCode;

    /**
     * 退款原因
     */
    @TableField(value = "pay_merchant_order_refund_reason")
    @Schema(description="退款原因")
    private String payMerchantOrderRefundReason;
}

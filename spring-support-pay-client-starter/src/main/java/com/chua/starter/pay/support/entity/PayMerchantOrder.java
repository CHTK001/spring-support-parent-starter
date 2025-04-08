package com.chua.starter.pay.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 *
 * @since 2024/12/30
 * @author CH    
 */

/**
 * 支付订单
 */
@ApiModel(description = "支付订单")
@Schema(description = "支付订单")
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pay_merchant_order")
public class PayMerchantOrder extends SysBase implements Serializable {
    @TableId(value = "pay_merchant_order_id", type = IdType.AUTO)
    @ApiModelProperty(value = "")
    @Schema(description = "")
    @NotNull(message = "不能为null")
    private Integer payMerchantOrderId;

    /**
     * 订单号
     */
    @TableField(value = "pay_merchant_order_code")
    @ApiModelProperty(value = "订单号")
    @Schema(description = "订单号")
    @Size(max = 255, message = "订单号最大长度要小于 255")
    private String payMerchantOrderCode;

    /**
     * 涉及到的设备序列号
     */
    @TableField(value = "pay_merchant_involved_device_serial_number")
    @ApiModelProperty(value = "涉及到的设备序列号")
    @Schema(description = "涉及到的设备序列号")
    private String payMerchantInvolvedDeviceSerialNumber;
    /**
     * 涉及到的账号类型
     */
    @TableField(value = "pay_merchant_involved_account_type")
    @ApiModelProperty(value = "涉及到的账号类型")
    @Schema(description = "涉及到的账号类型")
    private String payMerchantInvolvedAccountType;


    /**
     * 部门ID
     */
    @TableField(value = "pay_merchant_order_dept_id")
    @ApiModelProperty(value = "部门ID")
    @Schema(description = "部门ID")
    private String payMerchantOrderDeptId;
    /**
     * 部门名称
     */
    @TableField(value = "pay_merchant_order_dept_name")
    @ApiModelProperty(value = "部门名称")
    @Schema(description = "部门名称")
    private String payMerchantOrderDeptName;



    /**
     * 优惠券编码
     */
    @TableField(value = "pay_merchant_coupon_code")
    @ApiModelProperty(value = "优惠券编码")
    @Schema(description = "优惠券编码")
    private String payMerchantCouponCode;


    /**
     * 组织者
     */
    @TableField(value = "pay_merchant_dept_organizer")
    @ApiModelProperty(value = "机构组织者")
    @Schema(description = "机构组织者")
    private String payMerchantOrderDeptOrganizer;



    /**
     * 创建订单的用户
     */
    @TableField(value = "pay_merchant_order_user_id")
    @ApiModelProperty(value = "创建订单的用户")
    @Schema(description = "创建订单的用户")
    @Size(max = 255, message = "创建订单的用户最大长度要小于 255")
    private String payMerchantOrderUserId;

    /**
     * 订单来源
     */
    @TableField(value = "pay_merchant_order_origin")
    @ApiModelProperty(value = "订单来源")
    @Schema(description = "订单来源")
    @Size(max = 255, message = "订单来源最大长度要小于 255")
    private String payMerchantOrderOrigin;

    /**
     * 附加参数
     */
    @TableField(value = "pay_merchant_order_attach")
    @ApiModelProperty(value = "附加参数")
    @Schema(description = "附加参数")
    @Size(max = 255, message = "附加参数最大长度要小于 255")
    private String payMerchantOrderAttach;

    /**
     * 单价
     */
    @TableField(value = "pay_merchant_order_price")
    @ApiModelProperty(value = "商品原始价格")
    @Schema(description = "商品原始价格")
    private BigDecimal payMerchantOrderPrice;
    /**
     * 服务费
     */
    @TableField(value = "pay_merchant_order_service_price")
    @ApiModelProperty(value = "服务费")
    @Schema(description = "服务费")
    private BigDecimal payMerchantOrderServicePrice;

    /**
     * 总价
     */
    @TableField(value = "pay_merchant_order_total_price")
    @ApiModelProperty(value = "实际支付价格")
    @Schema(description = "实际支付价格")
    private BigDecimal payMerchantOrderTotalPrice;

    /**
     * 当时余额
     */
    @TableField(value = "pay_merchant_order_wallet")
    @ApiModelProperty(value = "当时余额")
    private BigDecimal payMerchantOrderWallet;
    /**
     订单状态;
     <pre>
         <code>

             0000: 新建;
             1000:待支付;
             1003:支付失败(订单创建失败);
             2000:支付成功;
             2005:支付成功(订单解析失败)
             3000:订单超时
             4000:退款中;
             4002:退款成功；
             4003:退款失败;
             5000:订单已关闭
             5001:订单已关闭(手动)
             5002:订单取消
         </code>
     </pre>
     */
    @TableField(value = "pay_merchant_order_status")
    @Schema(description = "订单状态; " +
            "0000: 新建; " +
            "1000:待支付; " +
            "1003:支付失败(订单创建失败);" +
            "2000:支付成功;" +
            "2005:支付成功(订单解析失败);" +
            "3000:订单超时;" +
            "4000:退款中;" +
            "4002:退款成功；" +
            "4003:退款失败;" +
            "5000:订单已关闭;" +
            "5001:订单已关闭(手动)" +
            "5002:订单取消(成功)"
            )
    private String payMerchantOrderStatus;


    /**
     * 交易方式；
     */
    @TableField(value = "pay_merchant_order_trade_type")
    @ApiModelProperty(value = "交易方式；")
    @Schema(description = "交易方式；")
    @Size(max = 255, message = "交易方式；最大长度要小于 255")
    private String payMerchantOrderTradeType;

    /**
     * 商户号
     */
    @TableField(value = "pay_merchant_code")
    @ApiModelProperty(value = "商户号")
    @Schema(description = "商户号")
    @Size(max = 255, message = "商户号最大长度要小于 255")
    private String payMerchantCode;

    /**
     * 备注
     */
    @TableField(value = "pay_merchant_order_remark")
    @ApiModelProperty(value = "备注")
    @Schema(description = "备注")
    @Size(max = 255, message = "备注最大长度要小于 255")
    private String payMerchantOrderRemark;

    /**
     * 商品名称
     */
    @TableField(value = "pay_merchant_order_product_name")
    @ApiModelProperty(value = "商品名称")
    @Schema(description = "商品名称")
    @Size(max = 255, message = "商品名称最大长度要小于 255")
    private String payMerchantOrderProductName;
    /**
     * 商品ID
     */
    @TableField(value = "pay_merchant_order_product_code")
    @Schema(description = "商品编码(用于业务上的商品标识)")
    @Size(max = 255, message = "商品编码最大长度要小于 255")
    @NotBlank(message = "商品编码不能为空")
    private String payMerchantOrderProductCode;

    /**
     * 请求消息头
     */
    @TableField(value = "pay_merchant_order_browser")
    @ApiModelProperty(value = "请求消息头")
    @Schema(description = "请求消息头")
    private String payMerchantOrderBrowser;

    /**
     * 请求系统
     */
    @TableField(value = "pay_merchant_order_browser_system")
    @ApiModelProperty(value = "请求系统")
    @Schema(description = "请求系统")
    @Size(max = 255, message = "请求系统最大长度要小于 255")
    private String payMerchantOrderBrowserSystem;

    /**
     * 锁
     */
    @TableField(value = "pay_merchant_order_version")
    @ApiModelProperty(value = "锁")
    @Schema(description = "锁")
    @Version
    private Integer payMerchantOrderVersion;


    /**
     * 完成时间
     */
    @TableField(value = "pay_merchant_order_finished_time")
    @ApiModelProperty(value = "完成时间")
    @Schema(description = "完成时间")
    private LocalDateTime payMerchantOrderFinishedTime;

    /**
     * 失败原因
     */
    @TableField(value = "pay_merchant_order_fail_message")
    @ApiModelProperty(value = "失败原因")
    @Schema(description = "失败原因")
    @Size(max = 255, message = "失败原因最大长度要小于 255")
    private String payMerchantOrderFailMessage;

    /**
     * 支付服务提供商订单号
     */
    @TableField(value = "pay_merchant_order_transaction_id")
    @ApiModelProperty(value = "支付服务提供商订单号")
    @Schema(description = "支付服务提供商订单号")
    @Size(max = 255, message = "支付服务提供商订单号最大长度要小于 255")
    private String payMerchantOrderTransactionId;
    /**
     * 退款理由
     */
    @TableField(value = "pay_merchant_order_refund_reason")
    @ApiModelProperty(value = "退款理由")
    @Schema(description = "退款理由")
    @Size(max = 255, message = "退款理由最大长度要小于 255")
    private String payMerchantOrderRefundReason;

    /**
     * 支付服务提供商退款订单号
     */
    @TableField(value = "pay_merchant_order_refund_transaction_id")
    @ApiModelProperty(value = "支付服务提供商退款订单号")
    @Schema(description = "支付服务提供商退款订单号")
    @Size(max = 255, message = "支付服务提供商退款订单号最大长度要小于 255")
    private String payMerchantOrderRefundTransactionId;

    /**
     * 退款订单号
     */
    @TableField(value = "pay_merchant_order_refund_code")
    @ApiModelProperty(value = "退款订单号")
    @Schema(description = "退款订单号")
    @Size(max = 255, message = "退款订单号最大长度要小于 255")
    private String payMerchantOrderRefundCode;

    /**
     * 退款时间
     */
    @TableField(value = "pay_merchant_order_refund_create_time")
    @ApiModelProperty(value = "退款时间")
    @Schema(description = "退款时间")
    @Size(max = 255, message = "退款时间最大长度要小于 255")
    private String payMerchantOrderRefundCreateTime;

    /**
     * 退款成功时间
     */
    @TableField(value = "pay_merchant_order_refund_success_time")
    @ApiModelProperty(value = "退款成功时间")
    @Schema(description = "退款成功时间")
    @Size(max = 255, message = "退款成功时间最大长度要小于 255")
    private String payMerchantOrderRefundSuccessTime;

    /**
     * 退款入账账户 说明：取当前退款单的退款入账方，有以下几种情况： 1）退回银行卡：{银行名称}{卡类型}{卡尾号} 2）退回支付用户零钱:支付用户零钱* 3）退还商户:商户基本账户商户结算银行账户 4）退回支付用户零钱通:支付用户零钱通
     */
    @TableField(value = "pay_merchant_order_refund_user_received_account")
    @ApiModelProperty(value = "退款入账账户 说明：取当前退款单的退款入账方，有以下几种情况： 1）退回银行卡：{银行名称}{卡类型}{卡尾号} 2）退回支付用户零钱:支付用户零钱* 3）退还商户:商户基本账户商户结算银行账户 4）退回支付用户零钱通:支付用户零钱通")
    @Schema(description = "退款入账账户 说明：取当前退款单的退款入账方，有以下几种情况： 1）退回银行卡：{银行名称}{卡类型}{卡尾号} 2）退回支付用户零钱:支付用户零钱* 3）退还商户:商户基本账户商户结算银行账户 4）退回支付用户零钱通:支付用户零钱通")
    @Size(max = 255, message = "退款入账账户 说明：取当前退款单的退款入账方，有以下几种情况： 1）退回银行卡：{银行名称}{卡类型}{卡尾号} 2）退回支付用户零钱:支付用户零钱* 3）退还商户:商户基本账户商户结算银行账户 4）退回支付用户零钱通:支付用户零钱通最大长度要小于 255")
    private String payMerchantOrderRefundUserReceivedAccount;



    /**
     * 商户名称
     */
    @TableField(exist = false)
    @ApiModelProperty(value = "商户名称")
    @Schema(description = "商户名称")
    private String payMerchantName;
}
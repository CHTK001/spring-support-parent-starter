package com.chua.starter.pay.support.pojo;

import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商户订单查询
 * @author CH
 * @since 2024/12/30
 */
@Data
@Schema(description = "商户订单查询")
public class PayOrderQueryRequest {

    /**
     * 商户编码
     */
    @Schema(description = "商户编码")
    private String payMerchantCode;

    /**
     * 收款机构ID
     */
    @TableField(value = "pay_merchant_pay_id")
    @ApiModelProperty(value = "收款机构ID")
    @Schema(description = "收款机构ID")
    private String payMerchantDeptId;

    /**
     * 组织者
     */
    @TableField(value = "pay_merchant_dept_organizer")
    @ApiModelProperty(value = "组织者")
    @Schema(description = "组织者")
    private String payMerchantOrderDeptOrganizer;

    /**
     * 收款机构名称
     */
    @TableField(value = "pay_merchant_dept_name")
    @ApiModelProperty(value = "收款机构名称")
    @Schema(description = "收款机构名称")
    private String payMerchantDeptName;
    /**
     * 订单来源
     */
    @TableField(value = "pay_merchant_order_origin")
    @ApiModelProperty(value = "订单来源")
    @Schema(description = "订单来源")
    @Size(max = 255, message = "订单来源最大长度要小于 255")
    private String payMerchantOrderOrigin;
    /**
     * 订单状态;
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
            "5002:订单取消"
    )
    private String payMerchantOrderStatus;

    /**
     * 关键字
     */
    @TableField(exist = false)
    @ApiModelProperty(value = "关键字")
    private String keyword;
    /**
     * 备注
     */
    @TableField(value = "pay_merchant_order_remark")
    @ApiModelProperty(value = "备注")
    @Schema(description = "备注")
    @Size(max = 255, message = "备注最大长度要小于 255")
    private String payMerchantOrderRemark;
    /**
     * 交易方式；
     */
    @TableField(value = "pay_merchant_order_trade_type")
    @ApiModelProperty(value = "交易方式；")
    @Schema(description = "交易方式；")
    @Size(max = 255, message = "交易方式；最大长度要小于 255")
    private String payMerchantOrderTradeType;


    /**
     * 订单号
     */
    @Schema(description = "订单号(支持模糊查询)")
    private String payMerchantOrderCode;

    /**
     * 用户id
     */
    @Schema(description = "下单账号ID")
    private String payMerchantOrderUserId;

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

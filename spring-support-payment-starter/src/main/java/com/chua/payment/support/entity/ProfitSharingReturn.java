package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 分账回退实体类
 *
 * <p>对已成功的分账单按接收方发起资金回退的记录。</p>
 *
 * @since 4.0.0
 * @author CH
 */
@Data
@TableName("pay_profit_sharing_return")
public class ProfitSharingReturn {

    @TableId(value = "pay_profit_sharing_return_id", type = IdType.AUTO)
    /**
     * 主键ID
     */
    private Long id;

    @TableField("pay_profit_sharing_return_no")
    /**
     * 回退单号
     */
    private String returnNo;

    @TableField("pay_profit_sharing_return_merchant_id")
    /**
     * 商户ID
     */
    private Long merchantId;

    @TableField("pay_profit_sharing_return_sharing_order_id")
    /**
     * 分账单ID
     */
    private Long sharingOrderId;

    @TableField("pay_profit_sharing_return_sharing_order_no")
    /**
     * 分账单号
     */
    private String sharingOrderNo;

    @TableField("pay_profit_sharing_return_order_no")
    /**
     * 原支付订单号
     */
    private String orderNo;

    @TableField("pay_profit_sharing_return_amount")
    /**
     * 回退金额
     */
    private BigDecimal amount;

    @TableField("pay_profit_sharing_return_receiver_account")
    /**
     * 回退接收方账号
     */
    private String receiverAccount;

    @TableField("pay_profit_sharing_return_reason")
    /**
     * 回退原因
     */
    private String reason;

    @TableField("pay_profit_sharing_return_status")
    /**
     * 状态: PENDING-处理中/SUCCEEDED-成功/FAILED-失败
     */
    private String status;

    @TableField("pay_profit_sharing_return_channel_return_no")
    /**
     * 渠道回退单号
     */
    private String channelReturnNo;

    @TableField("pay_profit_sharing_return_fail_reason")
    /**
     * 失败原因
     */
    private String failReason;

    @TableField("pay_profit_sharing_return_finish_at")
    /**
     * 完成时间
     */
    private LocalDateTime finishAt;

    @TableField("pay_profit_sharing_return_deleted")
    /**
     * 逻辑删除
     */
    private Integer deleted;

    @TableField(value = "pay_profit_sharing_return_created_at", fill = FieldFill.INSERT)
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    @TableField(value = "pay_profit_sharing_return_updated_at", fill = FieldFill.INSERT_UPDATE)
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}

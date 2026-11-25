package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 分账订单实体类
 *
 * <p>支付成功后按商户分账配置生成的分账单，记录分账明细与渠道执行结果。</p>
 *
 * @since 4.0.0
 * @author CH
 */
@Data
@TableName("pay_profit_sharing_order")
public class ProfitSharingOrder {

    @TableId(value = "pay_profit_sharing_order_id", type = IdType.AUTO)
    /**
     * 主键ID
     */
    private Long id;

    @TableField("pay_profit_sharing_order_no")
    /**
     * 分账单号
     */
    private String sharingOrderNo;

    @TableField("pay_profit_sharing_order_merchant_id")
    /**
     * 商户ID
     */
    private Long merchantId;

    @TableField("pay_profit_sharing_order_channel_id")
    /**
     * 支付渠道ID
     */
    private Long channelId;

    @TableField("pay_profit_sharing_order_channel_type")
    /**
     * 渠道类型: WECHAT/ALIPAY
     */
    private String channelType;

    @TableField("pay_profit_sharing_order_order_id")
    /**
     * 支付订单ID
     */
    private Long orderId;

    @TableField("pay_profit_sharing_order_order_no")
    /**
     * 支付订单号
     */
    private String orderNo;

    @TableField("pay_profit_sharing_order_third_party_order_no")
    /**
     * 第三方交易号
     */
    private String thirdPartyOrderNo;

    @TableField("pay_profit_sharing_order_total_amount")
    /**
     * 订单实付金额
     */
    private BigDecimal totalAmount;

    @TableField("pay_profit_sharing_order_sharing_amount")
    /**
     * 分账总金额
     */
    private BigDecimal sharingAmount;

    @TableField("pay_profit_sharing_order_receivers")
    /**
     * 分账接收方明细(JSON)
     */
    private String receivers;

    @TableField("pay_profit_sharing_order_status")
    /**
     * 状态: PENDING-待分账/PROCESSING-分账中/SUCCEEDED-成功/FAILED-失败/RETURNED-已回退
     */
    private String status;

    @TableField("pay_profit_sharing_order_channel_order_no")
    /**
     * 渠道分账单号
     */
    private String channelOrderNo;

    @TableField("pay_profit_sharing_order_fail_reason")
    /**
     * 失败原因
     */
    private String failReason;

    @TableField("pay_profit_sharing_order_finish_at")
    /**
     * 完成时间
     */
    private LocalDateTime finishAt;

    @TableField("pay_profit_sharing_order_remark")
    /**
     * 备注
     */
    private String remark;

    @TableField("pay_profit_sharing_order_deleted")
    /**
     * 逻辑删除
     */
    private Integer deleted;

    @TableField(value = "pay_profit_sharing_order_created_at", fill = FieldFill.INSERT)
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    @TableField(value = "pay_profit_sharing_order_updated_at", fill = FieldFill.INSERT_UPDATE)
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}

package com.chua.payment.support.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单视图对象
 *
 * @author CH
 * @since 2026-03-18
 */
@Data
public class OrderVO implements Serializable {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 商户ID
     */
    private Long merchantId;

    /**
     * 商户名称
     */
    private String merchantName;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 支付渠道
     */
    private String channelType;

    /**
     * 渠道子类型
     */
    private String channelSubType;

    /**
     * 订单金额
     */
    private BigDecimal orderAmount;

    /**
     * 实付金额
     */
    private BigDecimal paidAmount;

    /**
     * 优惠金额
     */
    private BigDecimal discountAmount;

    /**
     * 订单状态
     */
    private String status;

    /**
     * 状态描述
     */
    private String statusDesc;

    /**
     * 支付时间
     */
    private LocalDateTime payTime;

    /**
     * 完成时间
     */
    private LocalDateTime completeTime;

    /**
     * 退款时间
     */
    private LocalDateTime refundTime;

    /**
     * 第三方订单号
     */
    private String thirdPartyOrderNo;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}

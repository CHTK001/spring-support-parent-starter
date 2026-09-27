package com.chua.payment.support.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单视图对象
 *
 * @since 2026-03-18
 * @author CH
 */
@Data
public class OrderVO implements Serializable {
    private static final long serialVersionUID = 1L; // 序列化版本uid
    /**
     * 标识
     */
    private Long id;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 业务订单号
     */
    private String businessOrderNo;

    /**
     * 商户ID
     */
    private Long merchantId;

    /**
     * 商户名称
     */
    private String merchantName;

    /**
     * 渠道标识
     */
    private Long channelId;

    /**
     * 渠道名称
     */
    private String channelName;

    /**
     * 用户标识
     */
    private Long userId;

    /**
     * 渠道类型
     */
    private String channelType;

    /**
     * 渠道SUB类型
     */
    private String channelSubType;

    /**
     * 订单金额
     */
    private BigDecimal orderAmount;

    /**
     * 已付金额
     */
    private BigDecimal paidAmount;

    /**
     * 退款金额
     */
    private BigDecimal refundAmount;

    /**
     * 优惠金额
     */
    private BigDecimal discountAmount;

    /**
     * 币种
     */
    private String currency;

    /**
     * 状态
     */
    private String status;

    /**
     * 状态描述
     */
    private String statusDesc;

    /**
     * 主题
     */
    private String subject;

    /**
     * 请求体
     */
    private String body;

    /**
     * 通知URL
     */
    private String notifyUrl;

    /**
     * 返回值URL
     */
    private String returnUrl;

    /**
     * 过期时间
     */
    private LocalDateTime expireTime;

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
     * 删除标记
     */
    private Integer deleted;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}

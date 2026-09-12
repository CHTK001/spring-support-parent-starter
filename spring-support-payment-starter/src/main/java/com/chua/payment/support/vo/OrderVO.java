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
    private static final long serialVersionUID = 1L; // 串行版本uid
    /** 标识 */
    private Long id;

    /** 排序NO */
    private String orderNo;

    /** 业务排序NO */
    private String businessOrderNo;

    /** merchantid */
    private Long merchantId;

    /** Merchant名称 */
    private String merchantName;

    /** 通道标识 */
    private Long channelId;

    /** 通道名称 */
    private String channelName;

    /** 用户标识 */
    private Long userId;

    /** 通道类型 */
    private String channelType;

    /** 通道SUB类型 */
    private String channelSubType;

    /** 排序amount */
    private BigDecimal orderAmount;

    /** Paidamount */
    private BigDecimal paidAmount;

    /** Refundamount */
    private BigDecimal refundAmount;

    /** Discountamount */
    private BigDecimal discountAmount;

    /** 货币 */
    private String currency;

    /** 状态 */
    private String status;

    /** 状态desc */
    private String statusDesc;

    /** 主题 */
    private String subject;

    /** 请求体 */
    private String body;

    /** 通知url */
    private String notifyUrl;

    /** 返回值URL */
    private String returnUrl;

    /** Expire时间 */
    private LocalDateTime expireTime;

    /** 薪酬时间 */
    private LocalDateTime payTime;

    /** 完成时间 */
    private LocalDateTime completeTime;

    /** 退税时间 */
    private LocalDateTime refundTime;

    /** Thirdparty排序NO */
    private String thirdPartyOrderNo;

    /** 评论 */
    private String remark;

    /** 删除标记 */
    private Integer deleted;

    /** 创建时间AT */
    private LocalDateTime createdAt;

    /** 更新时间AT */
    private LocalDateTime updatedAt;
}
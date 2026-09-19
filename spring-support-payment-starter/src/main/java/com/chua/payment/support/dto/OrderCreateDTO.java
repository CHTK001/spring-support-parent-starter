package com.chua.payment.support.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单创建数据传输对象
 *
 * @since 2026-03-18
 * @author CH
 */
@Data
public class OrderCreateDTO implements Serializable {
    private static final long serialVersionUID = 1L; // 串行版本uid
    /** merchantid */
    private Long merchantId;

    /** 通道标识 */
    private Long channelId;

    /** 用户标识 */
    private Long userId;

    /** 业务排序NO */
    private String businessOrderNo;

    /** 通道类型 */
    private String channelType;

    /** 通道SUB类型 */
    private String channelSubType;

    /** 排序amount */
    private BigDecimal orderAmount;

    /** Discountamount */
    private BigDecimal discountAmount;

    /** 货币 */
    private String currency;

    /** 主题 */
    private String subject;

    /** 请求体 */
    private String body;

    /** 通知url */
    private String notifyUrl;

    /** 返回值URL */
    private String returnUrl;

    /** Expireminutes */
    private Integer expireMinutes;

    /** 评论 */
    private String remark;
}
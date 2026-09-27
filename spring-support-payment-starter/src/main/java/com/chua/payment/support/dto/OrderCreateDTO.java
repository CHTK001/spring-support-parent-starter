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
    private static final long serialVersionUID = 1L; // 序列化版本uid
    /**
     * 商户ID
     */
    private Long merchantId;

    /**
     * 渠道标识
     */
    private Long channelId;

    /**
     * 用户标识
     */
    private Long userId;

    /**
     * 业务订单NO
     */
    private String businessOrderNo;

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
     * 优惠金额
     */
    private BigDecimal discountAmount;

    /**
     * 币种
     */
    private String currency;

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
     * 过期分钟数
     */
    private Integer expireMinutes;

    /**
     * 备注
     */
    private String remark;
}

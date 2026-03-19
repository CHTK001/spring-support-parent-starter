package com.chua.payment.support.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单创建数据传输对象
 *
 * @author CH
 * @since 2026-03-18
 */
@Data
public class OrderCreateDTO implements Serializable {

    /**
     * 商户ID
     */
    private Long merchantId;

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
     * 优惠金额
     */
    private BigDecimal discountAmount;

    /**
     * 备注
     */
    private String remark;
}

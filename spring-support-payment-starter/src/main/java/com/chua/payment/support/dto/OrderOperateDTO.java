package com.chua.payment.support.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单操作通用DTO
 * @author CH
 * @since 4.0.0
 */
@Data
public class OrderOperateDTO {

    /**
     * 操作人
     */
    private String operator;

    /**
     * 备注
     */
    private String remark;

    /**
     * 已付金额
     */
    private BigDecimal paidAmount;

    /**
     * 退款金额
     */
    private BigDecimal refundAmount;

    /**
     * 第三方订单号
     */
    private String thirdPartyOrderNo;
}

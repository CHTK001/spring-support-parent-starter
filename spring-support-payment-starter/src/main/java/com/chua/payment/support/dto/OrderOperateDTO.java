package com.chua.payment.support.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单操作通用 DTO
 */
@Data
public class OrderOperateDTO {

    private String operator;

    private String remark;

    private BigDecimal paidAmount;

    private BigDecimal refundAmount;

    private String thirdPartyOrderNo;
}

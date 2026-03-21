package com.chua.payment.support.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 退款单操作 DTO
 */
@Data
public class RefundOperateDTO {

    private String operator;

    private String remark;

    private BigDecimal refundAmount;

    private String thirdPartyRefundNo;
}

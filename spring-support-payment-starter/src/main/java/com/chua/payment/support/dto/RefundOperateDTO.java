package com.chua.payment.support.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 退款单操作 DTO
 * @author CH
 */
@Data
public class RefundOperateDTO {

    /** Operator */
    private String operator;

    /** Remark */
    private String remark;

    /** Refundamount */
    private BigDecimal refundAmount;

    /** ThirdpartyrefundNO */
    private String thirdPartyRefundNo;
}


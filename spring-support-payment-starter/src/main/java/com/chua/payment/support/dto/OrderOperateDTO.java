package com.chua.payment.support.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单操作通用 DTO
 * @author CH
 */
@Data
public class OrderOperateDTO {

    /** Operator */
    private String operator;

    /** Remark */
    private String remark;

    /** Paidamount */
    private BigDecimal paidAmount;

    /** Refundamount */
    private BigDecimal refundAmount;

    /** Thirdparty排序NO */
    private String thirdPartyOrderNo;
}


package com.chua.payment.support.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
* 退款单操作 DTO
* @author CH
* @since 4.0.0
 */
@Data
public class RefundOperateDTO {

    /** 操作符 */
    private String operator;

    /** 评论 */
    private String remark;

    /** Refundamount */
    private BigDecimal refundAmount;

    /** thirdpartyrefundno */
    private String thirdPartyRefundNo;
}


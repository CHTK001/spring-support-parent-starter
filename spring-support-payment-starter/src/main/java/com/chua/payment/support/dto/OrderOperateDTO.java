package com.chua.payment.support.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
* 订单操作通用 DTO
* @author CH
* @since 4.0.0
 */
@Data
public class OrderOperateDTO {

    /** 操作符 */
    private String operator;

    /** 评论 */
    private String remark;

    /** Paidamount */
    private BigDecimal paidAmount;

    /** Refundamount */
    private BigDecimal refundAmount;

    /** Thirdparty排序NO */
    private String thirdPartyOrderNo;
}


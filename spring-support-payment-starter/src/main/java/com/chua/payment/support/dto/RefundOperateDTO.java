package com.chua.payment.support.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 退款操作 DTO
 * @author CH
 * @since 4.0.0
 */
@Data
public class RefundOperateDTO {

    /**
     * 操作人
     */
    private String operator;

    /**
     * 备注
     */
    private String remark;

    /**
     * 退款金额
     */
    private BigDecimal refundAmount;

    /**
     * 第三方退款单号
     */
    private String thirdPartyRefundNo;
}

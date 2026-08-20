package com.chua.starter.aliyun.support.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 支付宝单笔转账响应
 * @author CH
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AliyunAlipayTransferResponse {

    /** 是否成功 */
    /** Success */
    private boolean success;

    /** 支付宝转账订单号 */
    /** TransferNO */
    private String transferNo;

    /** 商户转账唯一订单号 */
    /** OUTBIZNO */
    private String outBizNo;

    /** 转账金额 */
    /** Amount */
    private BigDecimal amount;

    /** 转账状态：SUCCESS/FAIL/WAIT_PAY */
    /** 状态 */
    private String status;

    /** 支付时间 */
    /** PAY日期 */
    private String payDate;

    /** 错误信息 */
    /** 消息 */
    private String message;

    /** 原始响应 body */
    /** RAW响应 */
    private String rawResponse;
}


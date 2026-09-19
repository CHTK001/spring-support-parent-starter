package com.chua.starter.aliyun.support.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 支付宝单笔转账响应
 * @author CH
 * @since 4.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AliyunAlipayTransferResponse {

    /** 是否成功 */
    /** 成功 */
    private boolean success;

    /** 支付宝转账订单号 */
    /** 调动no */
    private String transferNo;

    /** 商户转账唯一订单号 */
    /** OUTBIZNO */
    private String outBizNo;

    /** 转账金额 */
    /** Amount */
    private BigDecimal amount;

    /** 转账状态：成功/失败/WAIT_薪酬 */
    /** 状态 */
    private String status;

    /** 支付时间 */
    /** 薪酬日期 */
    private String payDate;

    /** 错误信息 */
    /** 消息 */
    private String message;

    /** 原始响应 主体 */
    /** RAW响应 */
    private String rawResponse;
}


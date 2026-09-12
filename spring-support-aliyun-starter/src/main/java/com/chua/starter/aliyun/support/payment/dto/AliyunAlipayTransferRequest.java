package com.chua.starter.aliyun.support.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 支付宝单笔转账请求
 * @author CH
 * @since 4.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AliyunAlipayTransferRequest {

    /** 商户转账唯一订单号 */
    /** OUTBIZNO */
    private String outBizNo;

    /** 收款方支付宝账号（手机号或邮箱） */
    /** Payeeaccount */
    private String payeeAccount;

    /** 收款方真实姓名（可选，校验姓名） */
    /** Payee名称 */
    private String payeeName;

    /** 转账金额 */
    /** Amount */
    private BigDecimal amount;

    /** 转账备注 */
    /** 评论 */
    private String remark;
}


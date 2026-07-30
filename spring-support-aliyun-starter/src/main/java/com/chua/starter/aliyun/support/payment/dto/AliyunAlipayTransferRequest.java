package com.chua.starter.aliyun.support.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 支付宝单笔转账请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AliyunAlipayTransferRequest {

    /** 商户转账唯一订单号 */
    private String outBizNo;

    /** 收款方支付宝账号（手机号或邮箱） */
    private String payeeAccount;

    /** 收款方真实姓名（可选，校验姓名） */
    private String payeeName;

    /** 转账金额 */
    private BigDecimal amount;

    /** 转账备注 */
    private String remark;
}

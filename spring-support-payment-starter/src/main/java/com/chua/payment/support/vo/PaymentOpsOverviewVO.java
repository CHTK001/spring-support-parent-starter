package com.chua.payment.support.vo;

import lombok.Data;

import java.util.List;

/**
 * 支付运营总览视图
 */
@Data
public class PaymentOpsOverviewVO {
    private List<PaymentCallbackAuditVO> callbackAudits;
    private List<PaymentOrderNumberStrategyVO> orderNumberStrategies;
}

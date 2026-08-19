package com.chua.payment.support.vo;

import lombok.Data;

import java.util.List;

/**
 * 支付运营总览视图
 * @author CH
 */
@Data
public class PaymentOpsOverviewVO {
    /** Callbackaudits */
    private List<PaymentCallbackAuditVO> callbackAudits;
    /** 排序数字strategies */
    private List<PaymentOrderNumberStrategyVO> orderNumberStrategies;
}


package com.chua.payment.support.vo;

import lombok.Data;

import java.util.List;

/**
 * 支付运营总览视图
 * @author CH
 * @since 4.0.0
 */
@Data
public class PaymentOpsOverviewVO {
    /**
     * 回调策略审计列表
     */
    private List<PaymentCallbackAuditVO> callbackAudits;
    /**
     * 订单号生成策略列表
     */
    private List<PaymentOrderNumberStrategyVO> orderNumberStrategies;
}

package com.chua.payment.support.vo;

import java.util.List;

/**
 * 支付运营总览视图
 *
 * @param callbackAudits        回调策略审计列表
 * @param orderNumberStrategies 订单号生成策略列表
 * @author CH
 * @since 4.0.0
 */
public record PaymentOpsOverviewVO(
        List<PaymentCallbackAuditVO> callbackAudits,
        List<PaymentOrderNumberStrategyVO> orderNumberStrategies) {
}

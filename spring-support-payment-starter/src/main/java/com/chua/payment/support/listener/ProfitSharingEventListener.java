package com.chua.payment.support.listener;

import com.chua.payment.support.event.PaymentSuccessEvent;
import com.chua.payment.support.service.ProfitSharingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 分账事件监听器：支付成功后按商户分账配置登记并执行分账。
 *
 * <p>监听器内异常只记录日志不向支付主流程传播，避免影响支付状态机；
 * 失败的分账单保留 FAILED 状态，可通过对账或人工补偿处理。</p>
 *
 * @author CH
 * @since 4.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfitSharingEventListener {

    /**
     * 分账服务
     */
    private final ProfitSharingService profitSharingService;

    /**
     * 支付成功：登记并执行分账
     *
     * @param event 支付成功事件
     */
    @EventListener
    public void handlePaymentSuccess(PaymentSuccessEvent event) {
        try {
            profitSharingService.shareForOrder(event);
        } catch (Exception e) {
            log.error("分账登记执行失败: orderNo={}", event == null ? null : event.getOrderNo(), e);
        }
    }
}

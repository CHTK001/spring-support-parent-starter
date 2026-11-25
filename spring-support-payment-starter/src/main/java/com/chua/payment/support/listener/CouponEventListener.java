package com.chua.payment.support.listener;

import com.chua.payment.support.event.PaymentSuccessEvent;
import com.chua.payment.support.event.RefundSuccessEvent;
import com.chua.payment.support.service.CouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 优惠券事件监听器：支付成功核销、退款成功退券。
 *
 * <p>监听器内异常只记录日志不向支付主流程传播，避免影响支付/退款状态机；
 * 失败的核销/退券可通过对账或人工补偿处理。</p>
 *
 * @author CH
 * @since 4.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponEventListener {

    /**
     * 用户优惠券服务
     */
    private final CouponService couponService;

    /**
     * 支付成功：将订单锁定的优惠券核销
     *
     * @param event 支付成功事件
     */
    @EventListener
    public void handlePaymentSuccess(PaymentSuccessEvent event) {
        try {
            boolean used = couponService.confirmUsed(event.getOrderNo());
            if (used) {
                log.info("优惠券核销完成: orderNo={}", event.getOrderNo());
            }
        } catch (Exception e) {
            log.error("优惠券核销失败: orderNo={}", event.getOrderNo(), e);
        }
    }

    /**
     * 退款成功：将订单核销的优惠券退回为未使用
     *
     * @param event 退款成功事件
     */
    @EventListener
    public void handleRefundSuccess(RefundSuccessEvent event) {
        try {
            boolean restored = couponService.restoreByOrder(event.getOrderNo());
            if (restored) {
                log.info("优惠券已退回: orderNo={}", event.getOrderNo());
            }
        } catch (Exception e) {
            log.error("优惠券退回失败: orderNo={}", event.getOrderNo(), e);
        }
    }
}

package com.chua.starter.pay.support.scheduler;

import com.chua.common.support.function.NamedThreadFactory;
import com.chua.common.support.core.utils.ThreadUtils;
import com.chua.starter.pay.support.entity.PayMerchant;
import com.chua.starter.pay.support.service.PayMerchantOrderService;
import com.chua.starter.pay.support.service.PayMerchantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.chua.starter.common.support.logger.ModuleLog.highlight;

/**
 * 订单超时定时任务
 *
 * @author CH
 * @since 2025/10/15 9:32
 */
@Slf4j
@SpringBootConfiguration
@EnableScheduling
@RequiredArgsConstructor
public class PayMerchantScheduler implements InitializingBean, DisposableBean {

    /**
     * 商户服务类，用于获取有效的商户信息
     */
    final PayMerchantService payMerchantService;
    
    /**
     * 定时任务线程池，用于执行订单超时检测任务
     */
    final ScheduledThreadPoolExecutor orderCheckExecutor = new ScheduledThreadPoolExecutor(1, new NamedThreadFactory("订单超时检测"));
    private final PayMerchantOrderService payMerchantOrderService;

    /**
     * 检查订单是否超时的入口方法
     * 该方法会定时执行，获取所有有效商户并逐一检查其订单是否超时
     */
    public void orderToTimeout() {
        List<PayMerchant> payMerchants = payMerchantService.allEffective();
        try {
            log.info("[Pay] 开始执行商户订单超时检测, 共有商户: {}", highlight(payMerchants.size()));
            for (PayMerchant payMerchant : payMerchants) {
                log.info("[Pay] 开始执行商户 {} 订单超时检测", highlight(payMerchant.getPayMerchantName()));
                checkTimeout(payMerchant);
            }
        } catch (Exception e) {
            log.error("商户订单超时检测异常", e);
        }
    }

    /**
     * 检查指定商户的订单是否超时
     *
     * @param payMerchant 商户信息对象，包含需要检查的商户相关信息
     */
    private void checkTimeout(PayMerchant payMerchant) {
        int timeoutCount = payMerchantOrderService.timeout(payMerchant.getPayMerchantId(), payMerchant.getPayMerchantOpenTimeoutTime());
        log.info("[Pay] 商户 {} 订单超时检测, 超时订单数: {}", highlight(payMerchant.getPayMerchantName()), highlight(timeoutCount));
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        orderCheckExecutor.scheduleAtFixedRate(this::orderToTimeout, 0, 1, TimeUnit.SECONDS);
    }

    @Override
    public void destroy() throws Exception {
        ThreadUtils.closeQuietly(orderCheckExecutor);
    }
}

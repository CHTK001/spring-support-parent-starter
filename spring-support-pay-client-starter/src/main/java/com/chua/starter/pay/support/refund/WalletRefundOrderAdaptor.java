package com.chua.starter.pay.support.refund;

import com.chua.common.support.core.annotation.Spi;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.date.DateUtils;
import com.chua.common.support.objects.annotation.AutoInject;
import com.chua.starter.pay.support.constant.PayConstant;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.enums.PayOrderStatus;
import com.chua.starter.pay.support.pojo.RefundOrderV2Request;
import com.chua.starter.pay.support.pojo.RefundOrderV2Response;
import com.chua.starter.pay.support.service.PayMerchantOrderService;
import com.chua.starter.pay.support.service.PayUserWalletService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

/**
 * 退款到钱包适配器
 * @author CH
 * @since 2025/10/14 16:42
 */
@Spi("pay_wallet")
public class WalletRefundOrderAdaptor implements RefundOrderAdaptor {
    @AutoInject
    private PayUserWalletService payUserWalletService;

    @AutoInject
    private PayMerchantOrderService payMerchantOrderService;

    @AutoInject
    private RedissonClient redissonClient;

    @AutoInject
    private TransactionTemplate transactionTemplate;
    @Override
    public ReturnResult<RefundOrderV2Response> refundOrder(PayMerchantOrder merchantOrder, RefundOrderV2Request request) {
        String userId = merchantOrder.getUserId();
        RLock lock = redissonClient.getLock(PayConstant.CREATE_REFUND_PREFIX + merchantOrder.getPayMerchantOrderCode());

        lock.lock(3, TimeUnit.SECONDS);
        try {
            BigDecimal realAmount = merchantOrder.getPayMerchantOrderAmount().subtract(request.getRefundAmount());
            return transactionTemplate.execute(it -> {
                merchantOrder.setPayMerchantOrderStatus(realAmount.compareTo(BigDecimal.ZERO) == 0 ?
                        PayOrderStatus.PAY_REFUND_SUCCESS :
                        PayOrderStatus.PAY_REFUND_PART_SUCCESS);
                merchantOrder.setPayMerchantOrderAmount(realAmount);
                RefundOrderV2Response createOrderV2Response = new RefundOrderV2Response();
                payUserWalletService.addOrSubWallet(userId, request.getRefundAmount());
                merchantOrder.setPayMerchantOrderRefundSuccessTime(DateUtils.currentDateString());
                payMerchantOrderService.refundOrder(merchantOrder);
                return ReturnResult.ok(createOrderV2Response);
            });
        } catch (Exception e) {
            return ReturnResult.error(e);
        } finally {
            lock.unlock();
        }
    }
}

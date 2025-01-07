package com.chua.starter.pay.support.handler.wallet;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.objects.annotation.AutoInject;
import com.chua.starter.pay.support.configuration.PayListenerService;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.entity.PayUserWallet;
import com.chua.starter.pay.support.handler.PayOrderCreator;
import com.chua.starter.pay.support.result.PayOrderResponse;
import com.chua.starter.pay.support.service.PayMerchantOrderService;
import com.chua.starter.pay.support.service.PayUserWalletService;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 钱包支付
 *
 * @author CH
 */
@Spi("wallet")
public class WalletPayOrderCreator implements PayOrderCreator {
    @AutoInject
    private PayUserWalletService payUserWalletService;

    @AutoInject
    private PayMerchantOrderService payMerchantOrderService;
    @AutoInject
    private PayListenerService payListenerService;

    @AutoInject
    private TransactionTemplate transactionTemplate;
    @Override
    public ReturnResult<PayOrderResponse> handle(PayMerchantOrder payMerchantOrder) {
        String payMerchantOrderUserId = payMerchantOrder.getPayMerchantOrderUserId();
        ReturnResult<PayUserWallet> sysUserStudent = payUserWalletService.getUserWallet(payMerchantOrderUserId);
        if(null == sysUserStudent || !sysUserStudent.isOk()) {
            return ReturnResult.illegal("用户不存在");
        }

        PayUserWallet payUserWallet = sysUserStudent.getData();
        BigDecimal sysUserWallet = Optional.ofNullable(payUserWallet.getPayUserWalletMoney()).orElse(BigDecimal.ZERO);

        if(sysUserWallet.compareTo(payMerchantOrder.getPayMerchantOrderTotalPrice()) < 0) {
            return ReturnResult.illegal("钱包余额不足，请充值");
        }

        payMerchantOrder.setPayMerchantOrderWallet(sysUserWallet);
        payMerchantOrder.setPayMerchantOrderStatus("2000");
        return transactionTemplate.execute(it -> {
            payUserWalletService.decrementUserWallet(payUserWallet.getPayUserWalletUserId(), payMerchantOrder.getPayMerchantOrderTotalPrice());
            payMerchantOrderService.updateById(payMerchantOrder);
            return ReturnResult.ok(new PayOrderResponse());
        });
    }

    @Override
    public void onFinish(PayMerchantOrder payMerchantOrder) {
        payListenerService.listen(payMerchantOrder);
    }
}

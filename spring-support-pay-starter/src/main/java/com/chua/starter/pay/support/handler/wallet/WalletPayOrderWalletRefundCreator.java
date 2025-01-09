package com.chua.starter.pay.support.handler.wallet;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.date.DateTime;
import com.chua.common.support.objects.annotation.AutoInject;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.handler.PayOrderWalletRefundCreator;
import com.chua.starter.pay.support.pojo.PayRefundRequest;
import com.chua.starter.pay.support.result.PayRefundResponse;
import com.chua.starter.pay.support.result.PayRefundStatus;
import com.chua.starter.pay.support.service.PayUserWalletService;

/**
 * 钱包退款
 *
 * @author CH
 */
@Spi("wallet")
public class WalletPayOrderWalletRefundCreator implements PayOrderWalletRefundCreator {
    @AutoInject
    private PayUserWalletService payUserWalletService;
    @Override
    public ReturnResult<PayRefundResponse> handle(PayMerchantOrder payMerchantOrder, PayRefundRequest refundRequest) {
        String payMerchantOrderUserId = payMerchantOrder.getPayMerchantOrderUserId();
        payUserWalletService.incrementUserWallet(payMerchantOrderUserId, payMerchantOrder.getPayMerchantOrderTotalPrice());
        PayRefundResponse payRefundResponse = new PayRefundResponse();
        payRefundResponse.setStatus(PayRefundStatus.SUCCESS);
        payRefundResponse.setCreateTime(DateTime.now().toString());
        payRefundResponse.setSuccessTime(DateTime.now().toString());
        return ReturnResult.ok(payRefundResponse);
    }
}

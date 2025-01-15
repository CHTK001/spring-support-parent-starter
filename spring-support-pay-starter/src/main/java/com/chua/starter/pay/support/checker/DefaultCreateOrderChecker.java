package com.chua.starter.pay.support.checker;

import com.chua.common.support.annotations.SpiDefault;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.pay.support.pojo.PayOrderRequest;
import com.chua.starter.pay.support.transfer.CouponCodeTransfer;

/**
 * 检测器
 * @author CH
 */
@SpiDefault
public class DefaultCreateOrderChecker implements CreateOrderChecker {

    @Override
    public ReturnResult<String> check(PayOrderRequest request) {

        CouponCodeTransfer couponCodeTransfer = ServiceProvider.of(CouponCodeTransfer.class).getNewExtension(request.getTradeType());
        ReturnResult<String> couponCodeTransferResult = couponCodeTransfer.check(request);
        if(!couponCodeTransferResult.isOk()) {
            return couponCodeTransferResult;
        }

        MoneyChecker moneyChecker = ServiceProvider.of(MoneyChecker.class).getNewExtension(request.getTradeType());
        ReturnResult<String> moneyCheckerResult = moneyChecker.check(request);
        if(!moneyCheckerResult.isOk()) {
            return moneyCheckerResult;
        }

        return couponCodeTransferResult;
    }
}